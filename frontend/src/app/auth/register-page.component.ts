import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { authenticationMessage, isGoogleAuthorizationUrl, safeReturnPath } from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { setPrivatePageMetadata } from './private-page-metadata';
import { noControlCharacters, passwordRules, trimmedRequired, unicodeLength } from './auth.validators';

@Component({
  selector: 'lendly-register-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-page" aria-labelledby="page-title">
      <div class="auth-intro">
        <p class="eyebrow">Dołącz do Lendly</p>
        <h1 id="page-title" tabindex="-1">Utwórz konto</h1>
        <p>Załóż konto, aby pożyczać i udostępniać rzeczy w swojej okolicy.</p>
      </div>

      <div class="auth-panel">
        @if (!registrationOpen()) {
          <p class="form-notice" role="status">Rejestracja jest teraz wyłączona. Nowe konta nie są przyjmowane.</p>
          <a class="button-primary" routerLink="/login" [queryParams]="returnQuery">Zaloguj się na istniejące konto</a>
        } @else {
        @if (errorMessage()) {
          <p class="form-error-summary" role="alert">{{ errorMessage() }}</p>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="form-field">
            <label for="display-name">Jak mamy Cię nazywać?</label>
            <input
              id="display-name"
              name="displayName"
              type="text"
              formControlName="displayName"
              autocomplete="name"
              maxlength="160"
              required
              [attr.aria-invalid]="showDisplayNameError()"
              [attr.aria-describedby]="showDisplayNameError() ? 'display-name-error' : null">
            @if (showDisplayNameError()) {
              <p class="field-error" id="display-name-error">Wpisz nazwę od 2 do 80 znaków.</p>
            }
          </div>

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
                autocomplete="new-password"
                required
                [attr.aria-invalid]="showPasswordError()"
                [attr.aria-describedby]="showPasswordError() ? 'password-hint password-error' : 'password-hint'">
              <button class="password-toggle" type="button" [attr.aria-pressed]="passwordVisible()" (click)="passwordVisible.update((visible) => !visible)">
                {{ passwordVisible() ? 'Ukryj' : 'Pokaż' }}
              </button>
            </div>
            <p class="field-hint" id="password-hint">Co najmniej 12 znaków. Możesz użyć menedżera haseł.</p>
            @if (showPasswordError()) {
              <p class="field-error" id="password-error">Hasło musi mieć co najmniej 12 znaków i najwyżej 72 bajty UTF-8.</p>
            }
          </div>

          <button class="button-primary" type="submit" [disabled]="busy()">
            {{ busy() ? 'Tworzenie konta…' : 'Utwórz konto' }}
          </button>
          <p class="field-hint google-hint">Zakładając konto, akceptujesz <a routerLink="/terms">Regulamin</a> i <a routerLink="/privacy">Politykę prywatności</a>.</p>
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

        <p class="auth-switch">Masz już konto? <a routerLink="/login" [queryParams]="returnQuery">Zaloguj się</a></p>
        }
      </div>
    </section>
  `,
  styleUrl: './auth-page.component.scss'
})
export class RegisterPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Utwórz konto | Lendly', 'Utwórz konto, aby korzystać z Lendly.');
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);
  private readonly auth = inject(AuthService);

  readonly form = this.formBuilder.nonNullable.group({
    displayName: ['', [Validators.required, trimmedRequired, unicodeLength(2, 80), noControlCharacters]],
    email: ['', [Validators.required, Validators.email, Validators.minLength(3), Validators.maxLength(254)]],
    password: ['', [Validators.required, passwordRules]]
  });
  readonly busy = signal(false);
  readonly passwordVisible = signal(false);
  readonly errorMessage = signal('');
  readonly registrationOpen = computed(() => this.auth.features()?.registration !== false);
  readonly googleAvailable = computed(() => this.auth.features()?.googleSignIn !== false);
  readonly returnQuery = this.route.snapshot.queryParamMap.has('returnUrl')
    ? { returnUrl: safeReturnPath(this.route.snapshot.queryParamMap.get('returnUrl')) }
    : undefined;

  constructor() {
    this.auth.loadFeatures();
  }

  showDisplayNameError(): boolean {
    return this.form.controls.displayName.invalid && this.form.controls.displayName.touched;
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
    const values = this.form.getRawValue();
    this.auth.register(values.displayName.trim(), values.email.trim().toLowerCase(), values.password).subscribe({
      next: ({ user }) => {
        const returnUrl = safeReturnPath(this.route.snapshot.queryParamMap.get('returnUrl'));
        void (user.emailVerified
          ? this.router.navigateByUrl(returnUrl)
          : this.router.navigate(['/verify-email'], { queryParams: { returnUrl }, state: { codeSentAt: Date.now() } }));
      },
      error: (error: unknown) => {
        this.errorMessage.set(authenticationMessage(error, 'register'));
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
          this.errorMessage.set('Nie udało się rozpocząć logowania przez Google. Spróbuj ponownie później.');
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
}
