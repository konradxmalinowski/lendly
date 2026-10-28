import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  apiErrorCode,
  formatCountdown,
  mfaCodeMessage,
  normalizeNumericCode,
  normalizeRecoveryCode,
  retryAfterSeconds,
  safeReturnPath
} from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { Countdown } from './countdown';

@Component({
  selector: 'lendly-login-mfa-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="auth-page" aria-labelledby="page-title">
      <div class="auth-intro">
        <p class="eyebrow">Weryfikacja dwuetapowa</p>
        <h1 id="page-title" tabindex="-1">Potwierdź logowanie</h1>
        @if (useRecovery()) {
          <p>Wpisz jeden z kodów odzyskiwania zapisanych przy włączaniu weryfikacji. Każdy kod działa tylko raz.</p>
        } @else {
          <p>Otwórz aplikację uwierzytelniającą i wpisz aktualny 6-cyfrowy kod.</p>
        }
      </div>

      <div class="auth-panel">
        @if (errorMessage()) {
          <p class="form-error-summary" id="mfa-summary" role="alert">{{ errorMessage() }}</p>
        }

        <form (submit)="submit($event)" novalidate>
          <div class="form-field code-field">
            <label for="mfa-code">{{ useRecovery() ? 'Kod odzyskiwania' : 'Kod z aplikacji' }}</label>
            <input
              id="mfa-code"
              name="code"
              type="text"
              autocapitalize="off"
              spellcheck="false"
              enterkeyhint="done"
              [class.recovery]="useRecovery()"
              [attr.inputmode]="useRecovery() ? 'text' : 'numeric'"
              [attr.autocomplete]="useRecovery() ? 'off' : 'one-time-code'"
              [value]="code()"
              (input)="onCodeInput($event)"
              [attr.aria-invalid]="fieldError() || errorMessage() ? true : null"
              [attr.aria-describedby]="describedBy()">
            <p class="field-hint" id="mfa-hint">
              {{ useRecovery() ? 'Kod wygląda tak: abcd-efgh.' : 'Wpisz 6 cyfr. Spacje z wklejonego kodu zignorujemy.' }}
            </p>
            @if (fieldError()) {
              <p class="field-error" id="mfa-error">{{ fieldError() }}</p>
            }
            @if (lock.active()) {
              <p class="field-hint" id="mfa-lock">Spróbuj ponownie za {{ lockText() }}.</p>
            }
          </div>

          <button class="button-primary" type="submit" [disabled]="busy() || lock.active()">
            {{ busy() ? 'Sprawdzanie…' : 'Zaloguj się' }}
          </button>
          <p class="visually-hidden" aria-live="polite">{{ readyAnnouncement() }}</p>
        </form>

        <div class="step-actions">
          <button class="text-button" type="button" [disabled]="busy()" (click)="toggleRecovery()">
            {{ useRecovery() ? 'Wróć do kodu z aplikacji' : 'Użyj kodu odzyskiwania' }}
          </button>
          <a class="text-button" routerLink="/login" [queryParams]="returnQuery">Wróć do logowania</a>
        </div>
      </div>
    </section>
  `,
  styleUrls: ['./auth-page.component.scss', './auth-code.scss']
})
export class LoginMfaPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);

  readonly useRecovery = signal(false);
  readonly code = signal('');
  readonly busy = signal(false);
  readonly errorMessage = signal('');
  readonly fieldError = signal('');
  readonly returnQuery = this.route.snapshot.queryParamMap.has('returnUrl')
    ? { returnUrl: safeReturnPath(this.route.snapshot.queryParamMap.get('returnUrl')) }
    : undefined;
  readonly readyAnnouncement = signal('');
  readonly lock = new Countdown(inject(DestroyRef), () => {
    this.errorMessage.set('');
    this.readyAnnouncement.set('Możesz wpisać kod ponownie.');
  });
  readonly lockText = computed(() => formatCountdown(this.lock.remaining()));
  readonly describedBy = computed(() => {
    const ids = ['mfa-hint'];
    if (this.lock.active()) ids.push('mfa-lock');
    if (this.fieldError()) ids.push('mfa-error');
    if (this.errorMessage()) ids.push('mfa-summary');
    return ids.join(' ');
  });

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = this.useRecovery() ? normalizeRecoveryCode(input.value) : normalizeNumericCode(input.value);
    input.value = value;
    this.code.set(value);
    this.fieldError.set('');
  }

  toggleRecovery(): void {
    this.useRecovery.update((value) => !value);
    this.code.set('');
    this.errorMessage.set('');
    this.fieldError.set('');
    this.focusCode();
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.busy() || this.lock.active()) {
      return;
    }
    this.errorMessage.set('');
    this.readyAnnouncement.set('');
    this.fieldError.set('');

    const code = this.code();
    const valid = this.useRecovery() ? code.length >= 8 : code.length === 6;
    if (!valid) {
      this.fieldError.set(this.useRecovery() ? 'Wpisz cały kod odzyskiwania.' : 'Wpisz 6-cyfrowy kod z aplikacji.');
      this.focusCode();
      return;
    }

    this.busy.set(true);
    this.auth.verifyMfa(code).subscribe({
      next: ({ user }) => {
        const returnUrl = safeReturnPath(this.route.snapshot.queryParamMap.get('returnUrl'));
        void (user.emailVerified
          ? this.router.navigateByUrl(returnUrl)
          : this.router.navigate(['/verify-email'], { queryParams: { returnUrl } }));
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (apiErrorCode(error) === 'MFA_CHALLENGE_INVALID') {
          void this.router.navigate(['/login'], { queryParams: { auth: 'mfa-expired', ...this.returnQuery } });
          return;
        }
        if (apiErrorCode(error) === 'MFA_LOCKED') {
          this.lock.start(retryAfterSeconds(error));
        }
        this.errorMessage.set(mfaCodeMessage(error, this.useRecovery() ? 'recovery' : 'app'));
        this.focusCode(true);
      },
      complete: () => this.busy.set(false)
    });
  }

  private focusCode(select = false): void {
    setTimeout(() => {
      const input = this.document.getElementById('mfa-code') as HTMLInputElement | null;
      input?.focus();
      if (select) {
        input?.select();
      }
    });
  }
}
