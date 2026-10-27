import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  apiErrorCode,
  attemptsLabel,
  attemptsRemaining,
  authenticationMessage,
  emailCodeMessage,
  formatCountdown,
  normalizeNumericCode,
  retryAfterSeconds,
  safeReturnPath
} from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { setPrivatePageMetadata } from './private-page-metadata';

const RESEND_WINDOW_SECONDS = 60;

@Component({
  selector: 'lendly-verify-email-page',
  standalone: true,
  template: `
    <section class="auth-page" aria-labelledby="page-title">
      <div class="auth-intro">
        <p class="eyebrow">Potwierdzenie adresu</p>
        <h1 id="page-title" tabindex="-1">Potwierdź adres e-mail</h1>
        <p>
          Wysłaliśmy 6-cyfrowy kod na adres <span class="email-address">{{ email() }}</span>.
          Kod działa przez 15 minut.
        </p>
      </div>

      <div class="auth-panel">
        @if (locked()) {
          <p class="form-error-summary" id="code-locked" role="alert">
            Weryfikacja tego konta jest zablokowana po zbyt wielu błędnych kodach. Odblokować ją może tylko obsługa Lendly.
            Możesz się wylogować.
          </p>
        }
        @if (errorMessage() && !locked()) {
          <p class="form-error-summary" id="code-summary" role="alert">{{ errorMessage() }}</p>
        }
        @if (successMessage()) {
          <p class="form-success" role="status">{{ successMessage() }}</p>
        }

        <form (submit)="submit($event)" novalidate>
          <div class="form-field code-field">
            <label for="code">Kod z wiadomości</label>
            <input
              id="code"
              name="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              enterkeyhint="done"
              autocapitalize="off"
              spellcheck="false"
              [disabled]="locked()"
              [value]="code()"
              (input)="onCodeInput($event)"
              [attr.aria-invalid]="fieldError() || errorMessage() ? true : null"
              [attr.aria-describedby]="describedBy()">
            <p class="field-hint" id="code-hint">Wpisz 6 cyfr. Spacje i myślniki z wklejonego kodu zignorujemy.</p>
            @if (attemptsText()) {
              <p class="field-hint" id="code-attempts">{{ attemptsText() }}</p>
            }
            @if (fieldError()) {
              <p class="field-error" id="code-error">{{ fieldError() }}</p>
            }
          </div>

          <button class="button-primary" type="submit" [disabled]="busy() || locked() || attemptsLeft() === 0">
            {{ verifying() ? 'Sprawdzanie…' : 'Potwierdź adres' }}
          </button>
        </form>

        <div class="resend-block">
          <button class="button-secondary" type="button" [disabled]="busy() || locked() || cooldown() > 0" (click)="resend()">
            {{ resending() ? 'Wysyłanie…' : 'Wyślij kod ponownie' }}
          </button>
          @if (locked()) {
            <p class="field-hint">Wysyłanie kodów jest wyłączone.</p>
          } @else if (cooldown() > 0) {
            <p class="field-hint">Kolejny kod możesz wysłać za {{ countdown() }}.</p>
          } @else {
            <p class="field-hint">Nie widzisz wiadomości? Sprawdź spam albo wyślij kod ponownie.</p>
          }
          <p class="visually-hidden" aria-live="polite">{{ readyAnnouncement() }}</p>
        </div>

        <p class="auth-switch">
          Nie to konto?
          <button class="text-button" type="button" [disabled]="busy()" (click)="signOut()">Wyloguj się</button>
        </p>
      </div>
    </section>
  `,
  styleUrls: ['./auth-page.component.scss', './auth-code.scss']
})
export class VerifyEmailPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Potwierdź adres e-mail | Lendly', 'Wpisz kod z wiadomości, aby potwierdzić adres e-mail.');
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly returnPath = safeReturnPath(inject(ActivatedRoute).snapshot.queryParamMap.get('returnUrl'));
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly email = computed(() => this.auth.user()?.email ?? '');
  readonly code = signal('');
  readonly verifying = signal(false);
  readonly resending = signal(false);
  readonly busy = computed(() => this.verifying() || this.resending());
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly fieldError = signal('');
  readonly locked = signal(false);
  readonly attemptsLeft = signal<number | null>(null);
  readonly readyAnnouncement = signal('');

  private readonly resendAt = signal(0);
  private readonly now = signal(Date.now());
  readonly cooldown = computed(() => Math.max(0, Math.ceil((this.resendAt() - this.now()) / 1000)));
  readonly countdown = computed(() => formatCountdown(this.cooldown()));
  readonly attemptsText = computed(() => {
    const left = this.attemptsLeft();
    if (left === null) {
      return '';
    }
    return left === 0 ? 'Wykorzystano wszystkie próby tego kodu.' : attemptsLabel(left);
  });
  readonly describedBy = computed(() => {
    const ids = ['code-hint'];
    if (this.attemptsText()) ids.push('code-attempts');
    if (this.fieldError()) ids.push('code-error');
    if (this.errorMessage()) ids.push('code-summary');
    return ids.join(' ');
  });

  constructor() {
    const sentAt = (this.document.defaultView?.history.state as { codeSentAt?: number } | null)?.codeSentAt;
    if (typeof sentAt === 'number') {
      const left = RESEND_WINDOW_SECONDS - Math.floor((Date.now() - sentAt) / 1000);
      if (left > 0) {
        this.startCooldown(left);
      }
    }

    effect(() => {
      if (this.auth.user()?.emailVerified) {
        untracked(() => void this.router.navigateByUrl(this.returnPath, { replaceUrl: true }));
      }
    });

    this.destroyRef.onDestroy(() => this.stopTimer());
  }

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = normalizeNumericCode(input.value);
    input.value = value;
    this.code.set(value);
    this.fieldError.set('');
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.busy()) {
      return;
    }
    this.errorMessage.set('');
    this.successMessage.set('');
    this.fieldError.set('');

    if (this.code().length !== 6) {
      this.fieldError.set('Wpisz 6-cyfrowy kod z wiadomości.');
      this.focusCode();
      return;
    }

    this.verifying.set(true);
    this.auth.verifyEmail(this.code()).subscribe({
      next: () => this.verifying.set(false),
      error: (error: unknown) => {
        this.verifying.set(false);
        this.handleVerifyError(error);
      }
    });
  }

  resend(): void {
    if (this.busy() || this.cooldown() > 0) {
      return;
    }
    this.errorMessage.set('');
    this.successMessage.set('');
    this.resending.set(true);
    this.auth.resendEmailCode().subscribe({
      next: () => {
        this.resending.set(false);
        this.code.set('');
        this.attemptsLeft.set(null);
        this.successMessage.set('Wysłaliśmy nowy kod. Poprzedni już nie działa.');
        this.startCooldown(RESEND_WINDOW_SECONDS);
        this.focusCode();
      },
      error: (error: unknown) => {
        this.resending.set(false);
        this.handleResendError(error);
      }
    });
  }

  signOut(): void {
    this.auth.signOut().subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: () => this.errorMessage.set('Nie udało się wylogować. Spróbuj ponownie.')
    });
  }

  private handleVerifyError(error: unknown): void {
    const code = apiErrorCode(error);
    if (code === 'VERIFICATION_LOCKED') {
      this.lockAccount();
      return;
    }
    if (code === 'EMAIL_ALREADY_VERIFIED') {
      this.auth.refreshProfile().subscribe({ error: () => this.errorMessage.set(authenticationMessage(error, 'login')) });
      return;
    }
    const remaining = attemptsRemaining(error);
    if (code === 'VERIFICATION_CODE_INVALID' && remaining !== null) {
      this.attemptsLeft.set(remaining);
    } else if (code === 'VERIFICATION_ATTEMPTS_EXCEEDED') {
      this.attemptsLeft.set(0);
    }
    // The remaining attempts live in the persistent hint under the field, not in the alert.
    this.errorMessage.set(emailCodeMessage(error, null));
    this.focusCode(code === 'VERIFICATION_CODE_INVALID');
  }

  private handleResendError(error: unknown): void {
    const code = apiErrorCode(error);
    if (code === 'VERIFICATION_LOCKED') {
      this.lockAccount();
      return;
    }
    if (code === 'EMAIL_ALREADY_VERIFIED') {
      this.auth.refreshProfile().subscribe({ error: () => this.errorMessage.set(authenticationMessage(error, 'login')) });
      return;
    }
    if (code === 'RATE_LIMITED') {
      const seconds = retryAfterSeconds(error);
      this.startCooldown(seconds);
      this.errorMessage.set(seconds > RESEND_WINDOW_SECONDS
        ? `Wysłano już zbyt wiele kodów. Spróbuj ponownie za ${formatCountdown(seconds)}.`
        : `Nowy kod możesz wysłać za ${formatCountdown(seconds)}.`);
      return;
    }
    this.errorMessage.set(authenticationMessage(error, 'login'));
  }

  private lockAccount(): void {
    this.locked.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.fieldError.set('');
    this.stopTimer();
    this.resendAt.set(0);
  }

  private startCooldown(seconds: number): void {
    this.readyAnnouncement.set('');
    this.now.set(Date.now());
    this.resendAt.set(this.now() + seconds * 1000);
    if (this.timer || !this.document.defaultView) {
      return;
    }
    this.timer = setInterval(() => {
      this.now.set(Date.now());
      if (this.cooldown() === 0) {
        this.stopTimer();
        this.readyAnnouncement.set('Możesz wysłać nowy kod.');
      }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private focusCode(select = false): void {
    setTimeout(() => {
      const input = this.document.getElementById('code') as HTMLInputElement | null;
      input?.focus();
      if (select) {
        input?.select();
      }
    });
  }
}
