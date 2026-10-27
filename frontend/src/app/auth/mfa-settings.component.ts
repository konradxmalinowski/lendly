import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { apiErrorCode, formatCountdown, mfaCodeMessage, normalizeNumericCode, normalizeRecoveryCode, retryAfterSeconds } from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { MfaSetup, MfaStatus } from './auth.models';
import { Countdown } from './countdown';

type MfaView = 'loading' | 'failed' | 'off' | 'setup' | 'codes' | 'on';
type MfaAction = 'regenerate' | 'disable';

const QR_DATA_URI = /^data:image\/png;base64,[A-Za-z0-9+/=]+$/;
const LOW_CODES_THRESHOLD = 2;

@Component({
  selector: 'lendly-mfa-settings',
  standalone: true,
  template: `
    <section class="mfa-panel" id="mfa" aria-labelledby="mfa-title">
      <h2 id="mfa-title" tabindex="-1">Weryfikacja dwuetapowa</h2>

      @if (unavailable()) {
        <p class="state-message">Weryfikacja dwuetapowa jest teraz niedostępna.</p>
      } @else {
        @if (needsSetup()) {
          <p class="notice-info" role="status">
            Twoja rola wymaga weryfikacji dwuetapowej. Do czasu jej włączenia moderacja jest zablokowana.
          </p>
        }
        @if (reauth()) {
          <div class="notice-info" role="alert">
            <p class="reauth-text">Ze względów bezpieczeństwa ta zmiana wymaga świeżego logowania. Po zalogowaniu wrócisz do profilu.</p>
            <button class="button-primary" type="button" [disabled]="busy()" (click)="reauthenticate()">Zaloguj się ponownie</button>
          </div>
        }
        @if (notice()) {
          <p class="notice-success" role="status">{{ notice() }}</p>
        }
        @if (error()) {
          <p class="notice-error" role="alert">{{ error() }}</p>
        }

        @switch (view()) {
          @case ('loading') {
            <p class="state-message" role="status">Wczytywanie ustawień…</p>
          }
          @case ('failed') {
            <p class="state-message">Nie udało się wczytać ustawień weryfikacji.</p>
            <button class="button-secondary" type="button" (click)="loadStatus()">Spróbuj ponownie</button>
          }
          @case ('off') {
            <p class="lede">Oprócz hasła poprosimy o kod z aplikacji uwierzytelniającej na telefonie.</p>
            <button id="mfa-start" class="button-primary" type="button" [disabled]="busy()" (click)="startSetup()">
              {{ busy() ? 'Przygotowywanie…' : 'Włącz weryfikację dwuetapową' }}
            </button>
          }
          @case ('setup') {
            @if (setup(); as current) {
              <h3 id="mfa-setup-title" tabindex="-1">Dodaj konto do aplikacji</h3>
              <ol class="setup-steps">
                <li>
                  <p>Zeskanuj kod QR w aplikacji uwierzytelniającej.</p>
                  @if (qrSource(); as source) {
                    <div class="qr-frame">
                      <img [src]="source" width="220" height="220" alt="Kod QR do dodania konta Lendly w aplikacji uwierzytelniającej">
                    </div>
                  }
                  <p class="field-hint">{{ qrSource() ? 'Nie możesz zeskanować? Wpisz klucz ręcznie:' : 'Wpisz ten klucz w aplikacji:' }}</p>
                  <div class="secret-row">
                    <code class="secret" id="mfa-secret">{{ groupedSecret() }}</code>
                    <button class="button-secondary" type="button" (click)="copy(current.secret, 'Klucz skopiowany.')">Skopiuj klucz</button>
                  </div>
                </li>
                <li>
                  <form (submit)="confirmSetup($event)" novalidate>
                    <div class="form-field code-field">
                      <label for="mfa-setup-code">Wpisz 6-cyfrowy kod z aplikacji</label>
                      <input
                        id="mfa-setup-code"
                        name="code"
                        type="text"
                        inputmode="numeric"
                        autocomplete="one-time-code"
                        autocapitalize="off"
                        spellcheck="false"
                        [value]="code()"
                        (input)="onCodeInput($event)"
                        [attr.aria-invalid]="fieldError() ? true : null"
                        [attr.aria-describedby]="fieldError() ? 'mfa-setup-hint mfa-field-error' : 'mfa-setup-hint'">
                      <p class="field-hint" id="mfa-setup-hint">Po włączeniu wylogujemy Cię z innych urządzeń.</p>
                      @if (fieldError()) {
                        <p class="field-error" id="mfa-field-error">{{ fieldError() }}</p>
                      }
                      @if (lock.active()) {
                        <p class="field-hint">Spróbuj ponownie za {{ lockText() }}.</p>
                      }
                    </div>
                    <div class="action-row">
                      <button class="button-primary" type="submit" [disabled]="busy() || lock.active()">{{ busy() ? 'Sprawdzanie…' : 'Włącz' }}</button>
                      <button class="button-secondary" type="button" [disabled]="busy()" (click)="cancelSetup()">Anuluj</button>
                    </div>
                  </form>
                </li>
              </ol>
            }
          }
          @case ('codes') {
            <h3 id="mfa-codes-title" tabindex="-1">Kody odzyskiwania</h3>
            <p class="lede">
              Zapisz je teraz w bezpiecznym miejscu. Nie pokażemy ich ponownie.
              Każdy kod działa tylko raz, na przykład gdy zgubisz telefon.
            </p>
            <ul class="code-list">
              @for (item of recoveryCodes(); track item) {
                <li><code>{{ item }}</code></li>
              }
            </ul>
            <div class="action-row">
              <button class="button-secondary" type="button" (click)="copyCodes()">Skopiuj kody</button>
              <button class="button-secondary" type="button" (click)="downloadCodes()">Pobierz jako .txt</button>
            </div>
            <button class="button-primary done-button" type="button" (click)="finishCodes()">Kody są zapisane</button>
          }
          @case ('on') {
            @if (status(); as current) {
              <p class="lede"><strong>Weryfikacja dwuetapowa jest włączona.</strong></p>
              <p class="field-hint remaining">Pozostałe kody odzyskiwania: {{ current.recoveryCodesRemaining }}.</p>
              @if (current.recoveryCodesRemaining <= lowThreshold) {
                <p class="notice-info" role="status">Zostało mało kodów odzyskiwania. Wygeneruj nowe, zanim się skończą.</p>
              }

              <div class="action-row">
                <button
                  id="mfa-regenerate"
                  class="button-secondary"
                  type="button"
                  aria-controls="mfa-action-form"
                  [attr.aria-expanded]="action() === 'regenerate'"
                  [disabled]="busy()"
                  (click)="openAction('regenerate')">
                  Wygeneruj nowe kody
                </button>
                @if (!current.required) {
                  <button
                    id="mfa-disable"
                    class="button-secondary"
                    type="button"
                    aria-controls="mfa-action-form"
                    [attr.aria-expanded]="action() === 'disable'"
                    [disabled]="busy()"
                    (click)="openAction('disable')">
                    Wyłącz weryfikację
                  </button>
                }
              </div>
              @if (current.required) {
                <p class="field-hint">Nie możesz wyłączyć weryfikacji, bo wymaga jej Twoja rola.</p>
              }

              @if (action(); as currentAction) {
                <form id="mfa-action-form" (submit)="runAction($event)" novalidate>
                  <div class="form-field code-field">
                    <label for="mfa-action-code">{{ currentAction === 'disable' ? 'Kod z aplikacji lub kod odzyskiwania' : 'Kod z aplikacji' }}</label>
                    <input
                      id="mfa-action-code"
                      name="code"
                      type="text"
                      inputmode="numeric"
                      autocomplete="one-time-code"
                      autocapitalize="off"
                      spellcheck="false"
                      [value]="code()"
                      (input)="onCodeInput($event)"
                      [attr.aria-invalid]="fieldError() ? true : null"
                      [attr.aria-describedby]="fieldError() ? 'mfa-action-hint mfa-field-error' : 'mfa-action-hint'">
                    <p class="field-hint" id="mfa-action-hint">
                      {{ currentAction === 'regenerate'
                        ? 'Potrzebny jest kod z aplikacji, nie kod odzyskiwania. Stare kody przestaną działać.'
                        : 'Wyłączenie wyloguje Cię z innych urządzeń.' }}
                    </p>
                    @if (fieldError()) {
                      <p class="field-error" id="mfa-field-error">{{ fieldError() }}</p>
                    }
                    @if (lock.active()) {
                      <p class="field-hint">Spróbuj ponownie za {{ lockText() }}.</p>
                    }
                  </div>
                  <div class="action-row">
                    <button class="button-primary" type="submit" [disabled]="busy() || lock.active()">
                      {{ busy() ? 'Sprawdzanie…' : currentAction === 'regenerate' ? 'Wygeneruj kody' : 'Wyłącz weryfikację' }}
                    </button>
                    <button class="button-secondary" type="button" [disabled]="busy()" (click)="closeAction()">Anuluj</button>
                  </div>
                </form>
              }
            }
          }
        }
      }
    </section>
  `,
  styleUrl: './mfa-settings.component.scss'
})
export class MfaSettingsComponent {
  private readonly auth = inject(AuthService);
  private readonly document = inject(DOCUMENT);

  readonly lowThreshold = LOW_CODES_THRESHOLD;
  readonly view = signal<MfaView>('loading');
  readonly status = signal<MfaStatus | null>(null);
  readonly setup = signal<MfaSetup | null>(null);
  readonly recoveryCodes = signal<string[]>([]);
  readonly action = signal<MfaAction | null>(null);
  readonly code = signal('');
  readonly busy = signal(false);
  readonly error = signal('');
  readonly fieldError = signal('');
  readonly notice = signal('');
  readonly reauth = signal(false);
  readonly lock = new Countdown(inject(DestroyRef), () => {
    this.error.set('');
    this.notice.set('Możesz wpisać kod ponownie.');
  });
  readonly lockText = computed(() => formatCountdown(this.lock.remaining()));
  private readonly router = inject(Router);

  readonly serverUnavailable = signal(false);
  readonly unavailable = computed(() => this.auth.features()?.mfa === false || this.serverUnavailable());
  readonly needsSetup = computed(() => {
    const user = this.auth.user();
    return !!user && user.mfaRequired && !user.mfaEnabled && this.view() !== 'codes';
  });
  readonly qrSource = computed(() => {
    const image = this.setup()?.qrImage ?? '';
    return QR_DATA_URI.test(image) ? image : null;
  });
  readonly groupedSecret = computed(() => {
    const secret = (this.setup()?.secret ?? '').replace(/\s+/g, '');
    return secret.match(/.{1,4}/g)?.join(' ') ?? secret;
  });

  constructor() {
    this.auth.loadFeatures();
    this.loadStatus();
  }

  loadStatus(): void {
    this.view.set('loading');
    this.auth.mfaStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.view.set(status.enabled ? 'on' : 'off');
      },
      error: (error: unknown) => {
        if (apiErrorCode(error) === 'MFA_UNAVAILABLE') {
          this.serverUnavailable.set(true);
        }
        this.view.set('failed');
      }
    });
  }

  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    // Disabling accepts an app code or a recovery code; every other form takes the app code only.
    const value = this.action() === 'disable' && this.view() === 'on'
      ? normalizeRecoveryCode(input.value)
      : normalizeNumericCode(input.value);
    input.value = value;
    this.code.set(value);
    this.fieldError.set('');
  }

  startSetup(): void {
    this.resetMessages();
    this.busy.set(true);
    this.auth.mfaSetup().subscribe({
      next: (setup) => {
        this.busy.set(false);
        this.setup.set(setup);
        this.code.set('');
        this.view.set('setup');
        this.focusId('mfa-setup-title');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.showError(error);
        if (apiErrorCode(error) === 'MFA_ALREADY_ENABLED') {
          this.loadStatus();
        }
      }
    });
  }

  cancelSetup(): void {
    this.setup.set(null);
    this.code.set('');
    this.resetMessages();
    this.view.set('off');
    this.focusId('mfa-start');
  }

  confirmSetup(event: Event): void {
    event.preventDefault();
    if (this.busy() || !this.validCode()) {
      return;
    }
    this.resetMessages();
    this.busy.set(true);
    this.auth.mfaEnable(this.code()).subscribe({
      next: ({ recoveryCodes }) => {
        this.busy.set(false);
        this.setup.set(null);
        this.showCodes(recoveryCodes);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.showError(error);
        this.focusId('mfa-setup-code', true);
        if (apiErrorCode(error) === 'MFA_ALREADY_ENABLED') {
          this.setup.set(null);
          this.loadStatus();
        }
      }
    });
  }

  openAction(action: MfaAction): void {
    this.resetMessages();
    this.code.set('');
    this.action.set(this.action() === action ? null : action);
    if (this.action()) {
      this.focusId('mfa-action-code');
    }
  }

  closeAction(): void {
    const trigger = this.action() === 'disable' ? 'mfa-disable' : 'mfa-regenerate';
    this.action.set(null);
    this.code.set('');
    this.resetMessages();
    this.focusId(trigger);
  }

  runAction(event: Event): void {
    event.preventDefault();
    const action = this.action();
    if (this.busy() || !action || !this.validCode()) {
      return;
    }
    this.resetMessages();
    this.busy.set(true);

    if (action === 'regenerate') {
      this.auth.mfaRegenerateRecoveryCodes(this.code()).subscribe({
        next: ({ recoveryCodes }) => {
          this.busy.set(false);
          this.showCodes(recoveryCodes);
        },
        error: (error: unknown) => this.failAction(error)
      });
      return;
    }

    this.auth.mfaDisable(this.code()).subscribe({
      next: () => {
        this.busy.set(false);
        this.action.set(null);
        this.code.set('');
        this.status.set({ enabled: false, required: false, recoveryCodesRemaining: 0 });
        this.view.set('off');
        this.notice.set('Weryfikacja dwuetapowa została wyłączona.');
        this.focusId('mfa-title');
      },
      error: (error: unknown) => this.failAction(error)
    });
  }

  finishCodes(): void {
    this.recoveryCodes.set([]);
    this.resetMessages();
    this.loadStatus();
    this.focusId('mfa-title');
  }

  async copy(text: string, success: string): Promise<void> {
    this.resetMessages();
    try {
      await this.document.defaultView?.navigator.clipboard.writeText(text);
      this.notice.set(success);
    } catch {
      this.error.set('Nie udało się skopiować. Zaznacz tekst ręcznie albo pobierz plik.');
    }
  }

  copyCodes(): Promise<void> {
    return this.copy(this.recoveryCodes().join('\n'), 'Kody skopiowane.');
  }

  downloadCodes(): void {
    const body = [
      'Lendly - kody odzyskiwania',
      'Każdy kod działa tylko raz. Przechowuj ten plik w bezpiecznym miejscu.',
      '',
      ...this.recoveryCodes(),
      ''
    ].join('\n');
    const url = URL.createObjectURL(new Blob([body], { type: 'text/plain;charset=utf-8' }));
    const link = this.document.createElement('a');
    link.href = url;
    link.download = 'lendly-kody-odzyskiwania.txt';
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url));
  }

  private showCodes(codes: string[]): void {
    this.recoveryCodes.set(codes);
    this.status.set({ enabled: true, required: this.status()?.required ?? false, recoveryCodesRemaining: codes.length });
    this.action.set(null);
    this.code.set('');
    this.view.set('codes');
    this.focusId('mfa-codes-title');
  }

  private failAction(error: unknown): void {
    this.busy.set(false);
    this.showError(error);
    this.focusId('mfa-action-code', true);
    if (apiErrorCode(error) === 'MFA_REQUIRED_FOR_ROLE') {
      this.action.set(null);
      this.loadStatus();
    }
  }

  reauthenticate(): void {
    this.busy.set(true);
    const toLogin = () => void this.router.navigate(['/login'], { queryParams: { returnUrl: '/profile' } });
    this.auth.signOut().subscribe({
      next: toLogin,
      error: () => {
        this.auth.clearSession();
        toLogin();
      }
    });
  }

  private showError(error: unknown): void {
    const code = apiErrorCode(error);
    if (code === 'REAUTH_REQUIRED') {
      this.reauth.set(true);
      return;
    }
    if (code === 'MFA_LOCKED') {
      this.lock.start(retryAfterSeconds(error));
    }
    this.error.set(mfaCodeMessage(error));
  }

  private validCode(): boolean {
    const length = this.code().length;
    if (length === 6 || (this.action() === 'disable' && this.view() === 'on' && length >= 8)) {
      return true;
    }
    this.fieldError.set(this.action() === 'disable' && this.view() === 'on'
      ? 'Wpisz kod z aplikacji albo kod odzyskiwania.'
      : 'Wpisz 6-cyfrowy kod z aplikacji.');
    this.focusId(this.view() === 'setup' ? 'mfa-setup-code' : 'mfa-action-code');
    return false;
  }

  private resetMessages(): void {
    this.error.set('');
    this.notice.set('');
    this.fieldError.set('');
  }

  private focusId(id: string, select = false): void {
    setTimeout(() => {
      const element = this.document.getElementById(id) as HTMLInputElement | null;
      element?.focus();
      if (select) {
        element?.select();
      }
    });
  }
}
