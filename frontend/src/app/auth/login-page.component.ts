import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { authenticationMessage, isGoogleAuthorizationUrl, safeReturnPath } from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { setPrivatePageMetadata } from './private-page-metadata';

@Component({
  selector: 'lendly-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-page" aria-labelledby="page-title">
      <div class="auth-intro">
        <p class="eyebrow">Twoje konto Lendly</p>
        <h1 id="page-title" tabindex="-1">Zaloguj się</h1>
        <p>Wróć do swoich pożyczek i profilu.</p>
      </div>

      <div class="auth-panel">
        @if (notice()) {
          <p class="form-notice" role="status">{{ notice() }}</p>
        }
        @if (errorMessage()) {
          <p class="form-error-summary" role="alert">{{ errorMessage() }}</p>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="form-field">
            <label for="email">Adres e-mail</label>
            <input
              id="email"
              name="email"
              type="email"
              formControlName="email"
              autocomplete="email"
              inputmode="email"
              maxlength="254"
              spellcheck="false"
              required
              (blur)="trimEmail()"
              [attr.aria-invalid]="showEmailError()"
              [attr.aria-describedby]="showEmailError() ? 'email-error' : null">
            @if (showEmailError()) {
              <p class="field-error" id="email-error">Wpisz poprawny adres e-mail.</p>
            }
          </div>

          <div class="form-field">
            <label for="password">Hasło</label>
            <div class="password-control">
              <input
                id="password"
                name="password"
                [type]="passwordVisible() ? 'text' : 'password'"
                formControlName="password"
                autocomplete="current-password"
                required
                [attr.aria-invalid]="showPasswordError()"
                [attr.aria-describedby]="showPasswordError() ? 'password-error' : null">
              <button class="password-toggle" type="button" [attr.aria-pressed]="passwordVisible()" (click)="passwordVisible.update((visible) => !visible)">
                {{ passwordVisible() ? 'Ukryj' : 'Pokaż' }}
              </button>
            </div>
            @if (showPasswordError()) {
              <p class="field-error" id="password-error">Wpisz hasło.</p>
            }
          </div>

          <button class="button-primary" type="submit" [disabled]="busy()">
            {{ busy() ? 'Logowanie…' : 'Zaloguj się' }}
          </button>
        </form>

        <div class="auth-divider" aria-hidden="true"><span>lub</span></div>
        <button
          class="button-secondary google-button"
          type="button"
          [disabled]="busy() || !googleAvailable()"
          [attr.aria-describedby]="googleAvailable() ? null : 'google-unavailable'"
          (click)="signInWithGoogle()">
          Kontynuuj z Google
        </button>
        @if (!googleAvailable()) {
          <p class="field-hint google-hint" id="google-unavailable">Logowanie przez Google jest teraz niedostępne. Użyj adresu e-mail i hasła.</p>
        }

        <p class="auth-switch">Nie masz konta? <a routerLink="/register" [queryParams]="returnQuery">Utwórz konto</a></p>
      </div>
    </section>
  `,
  styleUrl: './auth-page.component.scss'
})
export class LoginPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Zaloguj się | Lendly', 'Zaloguj się do swojego konta Lendly.');
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);
  private readonly auth = inject(AuthService);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.minLength(3), Validators.maxLength(254)]],
    password: ['', [Validators.required]]
  });
  readonly busy = signal(false);
  readonly passwordVisible = signal(false);
  readonly errorMessage = signal('');
  readonly notice = signal(this.getAuthNotice());
  readonly googleAvailable = computed(() => this.auth.features()?.googleSignIn !== false);
  readonly returnQuery = this.route.snapshot.queryParamMap.has('returnUrl')
    ? { returnUrl: safeReturnPath(this.route.snapshot.queryParamMap.get('returnUrl')) }
    : undefined;

  constructor() {
    this.auth.loadFeatures();
  }

  showEmailError(): boolean {
    return this.form.controls.email.invalid && this.form.controls.email.touched;
  }

  showPasswordError(): boolean {
    return this.form.controls.password.invalid && this.form.controls.password.touched;
  }

  trimEmail(): void {
    const control = this.form.controls.email;
    control.setValue(control.value.trim());
  }

  submit(): void {
    this.errorMessage.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      queueMicrotask(() => this.document.defaultView?.document.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus());
      return;
    }

    this.busy.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.signIn(email.trim().toLowerCase(), password).subscribe({
      next: (result) => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        if (result.status === 'mfa-required') {
          void this.router.navigate(['/login/mfa'], { queryParams: returnUrl ? { returnUrl: safeReturnPath(returnUrl) } : {} });
        } else if (!result.user.emailVerified) {
          void this.router.navigate(['/verify-email'], { queryParams: { returnUrl: safeReturnPath(returnUrl) } });
        } else {
          void this.router.navigateByUrl(safeReturnPath(returnUrl));
        }
      },
      error: (error: unknown) => {
        this.errorMessage.set(authenticationMessage(error, 'login'));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false)
    });
  }

  signInWithGoogle(): void {
    this.errorMessage.set('');
    this.busy.set(true);
    this.auth.requestGoogleSignIn().subscribe({
      next: ({ authorizationUrl }) => {
        if (!isGoogleAuthorizationUrl(authorizationUrl)) {
          this.errorMessage.set('Nie udało się rozpocząć logowania przez Google. Użyj adresu e-mail i hasła.');
          this.busy.set(false);
          return;
        }
        this.document.defaultView?.location.assign(authorizationUrl);
      },
      error: (error: unknown) => {
        this.errorMessage.set(authenticationMessage(error, 'google'));
        this.busy.set(false);
      }
    });
  }

  private getAuthNotice(): string {
    const code = this.route.snapshot.queryParamMap.get('auth');
    if (code === 'link-required') {
      return 'Ten adres jest już używany. Zaloguj się na istniejące konto, a potem połącz je z Google w profilu.';
    }
    if (code === 'no-account') {
      return 'Nie ma konta Lendly z adresem tego konta Google. Zaloguj się e-mailem i hasłem, a potem połącz konto Google w profilu.';
    }
    if (code === 'mfa-expired') {
      return 'Czas na wpisanie kodu minął albo kod podano zbyt wiele razy. Zaloguj się jeszcze raz.';
    }
    if (code === 'failed') {
      return 'Nie udało się zalogować przez Google. Spróbuj ponownie albo użyj hasła.';
    }
    return '';
  }
}
