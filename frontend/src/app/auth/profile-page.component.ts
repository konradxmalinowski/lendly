import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountDataComponent } from './account-data.component';
import { authenticationMessage, isGoogleAuthorizationUrl } from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { MfaSettingsComponent } from './mfa-settings.component';
import { areaLabelRules, noControlCharacters, trimmedRequired, unicodeLength } from './auth.validators';

@Component({
  selector: 'lendly-profile-page',
  standalone: true,
  imports: [AccountDataComponent, MfaSettingsComponent, ReactiveFormsModule, RouterLink],
  template: `
    <section class="profile-page" aria-labelledby="page-title">
      <div class="profile-heading">
        <div>
          <p class="eyebrow">Twoje konto</p>
          <h1 id="page-title" tabindex="-1">Mój profil</h1>
        </div>
        <button class="button-secondary signout-button" type="button" [disabled]="busy()" (click)="signOut()">Wyloguj się</button>
      </div>

      @if (notice()) {
        <p class="form-notice" role="status">{{ notice() }}</p>
      }
      @if (errorMessage()) {
        <p class="form-error-summary" role="alert">{{ errorMessage() }}</p>
      }
      @if (successMessage()) {
        <p class="form-success" role="status">{{ successMessage() }}</p>
      }

      @if (user(); as profile) {
        <div class="profile-layout" [class.single]="!googleAvailable()">
          <div class="profile-stack">
          <section class="profile-panel" aria-labelledby="account-details-title">
            <h2 id="account-details-title">Dane konta</h2>
            <dl class="account-details">
              <div>
                <dt>Adres e-mail</dt>
                <dd>
                  {{ profile.email }}
                  @if (!profile.emailVerified) {
                    <span class="unverified">Niepotwierdzony.</span>
                    <a routerLink="/verify-email">Wpisz kod</a>
                  }
                </dd>
              </div>
              <div>
                <dt>Data dołączenia</dt>
                <dd>{{ formatRegisteredAt(profile.registeredAt) }}</dd>
              </div>
            </dl>

            <form [formGroup]="form" (ngSubmit)="saveProfile()" novalidate>
              <div class="form-field">
                <label for="display-name">Nazwa wyświetlana</label>
                <input
                  id="display-name"
                  name="displayName"
                  type="text"
                  formControlName="displayName"
                  autocomplete="nickname"
                  maxlength="160"
                  required
                  [attr.aria-invalid]="showDisplayNameError()"
                  [attr.aria-describedby]="showDisplayNameError() ? 'display-name-error' : null">
                @if (showDisplayNameError()) {
                  <p class="field-error" id="display-name-error">Wpisz nazwę od 2 do 80 znaków.</p>
                }
              </div>

              <div class="form-field">
                <label for="area-label">Okolica <span class="optional-label">(opcjonalnie)</span></label>
                <input
                  id="area-label"
                  name="areaLabel"
                  type="text"
                  formControlName="areaLabel"
                  autocomplete="address-level2"
                  maxlength="240"
                  aria-describedby="area-label-hint"
                  [attr.aria-invalid]="showAreaLabelError()"
                  [attr.aria-describedby]="showAreaLabelError() ? 'area-label-hint area-label-error' : 'area-label-hint'">
                <p class="field-hint" id="area-label-hint">Podaj ogólną nazwę okolicy, bez ulicy ani dokładnego adresu.</p>
                @if (showAreaLabelError()) {
                  <p class="field-error" id="area-label-error">Wpisz od 2 do 120 znaków albo zostaw to pole puste.</p>
                }
              </div>

              <button class="button-primary" type="submit" [disabled]="busy() || form.pristine">
                {{ saving() ? 'Zapisywanie…' : 'Zapisz zmiany' }}
              </button>
            </form>
          </section>

          @if (profile.emailVerified) {
            <lendly-mfa-settings />
          } @else {
            <section class="profile-panel" aria-labelledby="mfa-pending-title">
              <h2 id="mfa-pending-title">Weryfikacja dwuetapowa</h2>
              <p class="field-hint">Najpierw <a routerLink="/verify-email">potwierdź adres e-mail</a>, a potem włączysz weryfikację dwuetapową.</p>
            </section>
          }
          </div>

          @if (googleAvailable()) {
          <section class="profile-panel connection-panel" aria-labelledby="connection-title">
            <h2 id="connection-title">Połączone konto</h2>
            <p>Połącz Google z tym kontem, aby używać go do logowania.</p>
            <button class="button-secondary" type="button" [disabled]="busy() || !profile.emailVerified"
              [attr.aria-describedby]="profile.emailVerified ? null : 'google-unavailable'" (click)="linkGoogle()">
              Połącz z Google
            </button>
            @if (!profile.emailVerified) {
              <p class="field-hint google-hint" id="google-unavailable">Najpierw <a routerLink="/verify-email">potwierdź adres e-mail</a>, a potem połączysz konto z Google.</p>
            }
          </section>
          }
        </div>
        <lendly-account-data [class.single]="!googleAvailable()" />
      } @else {
        <p class="form-error-summary" role="status">Nie udało się wczytać profilu. <a routerLink="/login">Zaloguj się ponownie</a>.</p>
      }
    </section>
  `,
  styleUrl: './auth-page.component.scss',
  styles: `
    .profile-stack { display: grid; min-width: 0; gap: 1.25rem; }
    .unverified { margin-inline: 0.25rem; color: var(--warning); font-size: 0.9rem; }
    .account-details a { color: var(--primary); font-weight: 600; text-underline-offset: 0.2em; }
  `
})
export class ProfilePageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly document = inject(DOCUMENT);

  readonly user = this.auth.user;
  readonly googleAvailable = computed(() => this.auth.features()?.googleSignIn === true);
  readonly saving = signal(false);
  readonly linking = signal(false);
  readonly signingOut = signal(false);
  readonly busy = computed(() => this.saving() || this.linking() || this.signingOut());
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly notice = signal(this.getGoogleNotice());
  readonly form = this.formBuilder.nonNullable.group({
    displayName: [this.user()?.displayName ?? '', [Validators.required, trimmedRequired, unicodeLength(2, 80), noControlCharacters]],
    areaLabel: [this.user()?.areaLabel ?? '', [areaLabelRules]]
  });

  constructor() {
    this.auth.loadFeatures();
  }

  showDisplayNameError(): boolean {
    return this.form.controls.displayName.invalid && this.form.controls.displayName.touched;
  }

  formatRegisteredAt(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? 'Nieznana'
      : new Intl.DateTimeFormat('pl-PL', { dateStyle: 'long' }).format(date);
  }

  showAreaLabelError(): boolean {
    return this.form.controls.areaLabel.invalid && this.form.controls.areaLabel.touched;
  }

  saveProfile(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      queueMicrotask(() => this.document.defaultView?.document.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus());
      return;
    }

    this.saving.set(true);
    const values = this.form.getRawValue();
    this.auth.updateProfile(values.displayName.trim(), values.areaLabel.trim() || null).subscribe({
      next: (profile) => {
        this.form.patchValue({ displayName: profile.displayName, areaLabel: profile.areaLabel ?? '' });
        this.form.markAsPristine();
        this.successMessage.set('Zmiany zostały zapisane.');
      },
      error: (error: unknown) => this.errorMessage.set(authenticationMessage(error, 'profile')),
      complete: () => this.saving.set(false)
    });
  }

  linkGoogle(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.linking.set(true);
    this.auth.requestGoogleLink().subscribe({
      next: ({ authorizationUrl }) => {
        if (!isGoogleAuthorizationUrl(authorizationUrl)) {
          this.errorMessage.set('Nie udało się rozpocząć łączenia z Google. Spróbuj ponownie później.');
          this.linking.set(false);
          return;
        }
        this.document.defaultView?.location.assign(authorizationUrl);
      },
      error: (error: unknown) => {
        this.errorMessage.set(authenticationMessage(error, 'google'));
        this.linking.set(false);
      }
    });
  }

  signOut(): void {
    this.errorMessage.set('');
    this.signingOut.set(true);
    this.auth.signOut().subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: () => {
        this.errorMessage.set('Nie udało się wylogować. Spróbuj ponownie.');
        this.signingOut.set(false);
      }
    });
  }

  private getGoogleNotice(): string {
    const code = this.route.snapshot.queryParamMap.get('google');
    if (code === 'linked') {
      return 'Konto Google zostało połączone.';
    }
    if (code === 'link-failed') {
      return 'Nie udało się połączyć konta Google. Spróbuj ponownie.';
    }
    return '';
  }
}
