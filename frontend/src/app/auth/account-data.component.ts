import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { apiErrorCode, authenticationMessage, formatCountdown, normalizeRecoveryCode, retryAfterSeconds } from './auth-ui.helpers';
import { AuthService } from './auth.service';
import { Countdown } from './countdown';

type Field = 'password' | 'code' | 'confirm';

const CONFIRM_WORD = 'USUŃ';
const EXPORT_FILE = 'lendly-moje-dane.json';
const WRONG_CODE_ERRORS = new Set(['MFA_CODE_INVALID', 'INVALID_MFA_CODE']);

@Component({
  selector: 'lendly-account-data',
  standalone: true,
  template: `
    <section class="data-panel" aria-labelledby="data-title">
      <h2 id="data-title">Twoje dane</h2>

      <div class="export-block">
        <h3 id="export-title">Pobierz moje dane</h3>
        <p class="lede">Dostaniesz plik JSON z danymi Twojego konta: profilem, ogłoszeniami i rezerwacjami.</p>
        @if (exportError()) {
          <p class="notice-error" role="alert">{{ exportError() }}</p>
        }
        <button class="button-secondary" type="button" aria-describedby="export-title" [disabled]="exporting()" (click)="downloadData()">
          {{ exporting() ? 'Przygotowywanie pliku…' : 'Pobierz moje dane' }}
        </button>
      </div>

      <div class="danger-block" aria-labelledby="delete-title" role="group">
        <h3 id="delete-title">Usuń konto</h3>
        <p class="lede">Po usunięciu konta:</p>
        <ul class="consequences">
          <li>Twoje ogłoszenia znikną z katalogu.</li>
          <li>Otwarte rezerwacje zostaną anulowane.</li>
          <li>W zakończonych wypożyczeniach druga strona zobaczy „Usunięte konto”.</li>
          <li>Kopie zapasowe z Twoimi danymi znikną w ciągu 14 dni.</li>
        </ul>
        <p class="lede"><strong>Tej operacji nie da się cofnąć.</strong></p>

        @if (reauth()) {
          <div class="notice-info" role="alert">
            <p>Ze względów bezpieczeństwa usunięcie konta wymaga świeżego logowania. Zaloguj się ponownie przez Google i spróbuj jeszcze raz.</p>
            <button class="button-secondary" type="button" [disabled]="busy()" (click)="reauthenticate()">Zaloguj się ponownie</button>
          </div>
        }
        @if (summary()) {
          <p class="notice-error" id="delete-summary" role="alert" tabindex="-1">{{ summary() }}</p>
        }

        <form (submit)="submit($event)" novalidate>
          @if (!hasPassword()) {
            <p class="field-hint">Logujesz się przez Google, więc nie musisz podawać hasła.</p>
          } @else {
            <div class="form-field">
              <label for="delete-password">Hasło</label>
              <input
                id="delete-password"
                name="password"
                type="password"
                autocomplete="current-password"
                [value]="password()"
                (input)="onPassword($event)"
                [attr.aria-invalid]="errors().password ? true : null"
                [attr.aria-describedby]="errors().password ? 'delete-password-error' : null">
              @if (errors().password; as message) {
                <p class="field-error" id="delete-password-error">{{ message }}</p>
              }
            </div>
          }

          @if (showCode()) {
            <div class="form-field">
              <label for="delete-code">Kod z aplikacji lub kod odzyskiwania</label>
              <input
                id="delete-code"
                name="code"
                type="text"
                autocapitalize="off"
                autocomplete="one-time-code"
                spellcheck="false"
                [value]="code()"
                (input)="onCode($event)"
                [attr.aria-invalid]="errors().code ? true : null"
                [attr.aria-describedby]="errors().code ? 'delete-code-hint delete-code-error' : 'delete-code-hint'">
              <p class="field-hint" id="delete-code-hint">6 cyfr z aplikacji uwierzytelniającej albo jeden kod odzyskiwania.</p>
              @if (errors().code; as message) {
                <p class="field-error" id="delete-code-error">{{ message }}</p>
              }
              @if (lock.active()) {
                <p class="field-hint">Spróbuj ponownie za {{ lockText() }}.</p>
              }
            </div>
          }

          <div class="form-field">
            <label for="delete-confirm">Wpisz USUŃ, aby potwierdzić</label>
            <input
              id="delete-confirm"
              name="confirm"
              type="text"
              autocapitalize="characters"
              autocomplete="off"
              spellcheck="false"
              [value]="confirm()"
              (input)="onConfirm($event)"
              [attr.aria-invalid]="errors().confirm ? true : null"
              [attr.aria-describedby]="errors().confirm ? 'delete-confirm-error' : null">
            @if (errors().confirm; as message) {
              <p class="field-error" id="delete-confirm-error">{{ message }}</p>
            }
          </div>

          <button class="button-danger" type="submit" [disabled]="busy() || lock.active()">
            {{ busy() ? 'Usuwanie konta…' : 'Usuń konto na zawsze' }}
          </button>
          <p class="visually-hidden" role="status">{{ announcement() }}</p>
        </form>
      </div>
    </section>
  `,
  styleUrl: './account-data.component.scss'
})
export class AccountDataComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  readonly exporting = signal(false);
  readonly exportError = signal('');

  readonly password = signal('');
  readonly code = signal('');
  readonly confirm = signal('');
  readonly busy = signal(false);
  readonly summary = signal('');
  readonly announcement = signal('');
  readonly reauth = signal(false);
  readonly errors = signal<Partial<Record<Field, string>>>({});
  private readonly codeDemanded = signal(false);

  readonly lock = new Countdown(inject(DestroyRef), () => {
    this.summary.set('');
    this.announcement.set('Możesz wpisać kod ponownie.');
  });
  readonly lockText = computed(() => formatCountdown(this.lock.remaining()));
  readonly hasPassword = computed(() => this.auth.user()?.hasPassword !== false);
  readonly showCode = computed(() => !!this.auth.user()?.mfaEnabled || this.codeDemanded());

  downloadData(): void {
    this.exportError.set('');
    this.exporting.set(true);
    this.http.get('/api/me/export', { observe: 'response', responseType: 'blob' }).subscribe({
      next: (response) => {
        this.exporting.set(false);
        if (!response.body) {
          this.exportError.set('Nie udało się pobrać danych. Spróbuj ponownie.');
          return;
        }
        this.saveFile(response.body, fileNameFrom(response.headers.get('Content-Disposition')));
      },
      error: (error: unknown) => {
        this.exporting.set(false);
        this.exportError.set(error instanceof HttpErrorResponse && error.status === 429
          ? 'Pobrano dane zbyt wiele razy. Odczekaj chwilę i spróbuj ponownie.'
          : error instanceof HttpErrorResponse && error.status === 0
            ? 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.'
            : 'Nie udało się pobrać danych. Spróbuj ponownie.');
      }
    });
  }

  onPassword(event: Event): void {
    this.password.set((event.target as HTMLInputElement).value);
    this.clearError('password');
  }

  onCode(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = normalizeRecoveryCode(input.value);
    input.value = value;
    this.code.set(value);
    this.clearError('code');
  }

  onConfirm(event: Event): void {
    this.confirm.set((event.target as HTMLInputElement).value);
    this.clearError('confirm');
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.busy() || this.lock.active()) {
      return;
    }
    this.summary.set('');
    this.reauth.set(false);
    this.announcement.set('');

    const invalid = this.validate();
    if (invalid) {
      this.errors.set(invalid);
      this.focusField(firstField(invalid));
      return;
    }

    this.busy.set(true);
    this.announcement.set('Usuwanie konta…');
    this.auth.deleteAccount({
      password: this.hasPassword() ? this.password() : null,
      code: this.showCode() ? this.code() : null,
      confirm: CONFIRM_WORD
    }).subscribe({
      next: () => {
        void this.router.navigate(['/'], { queryParams: { account: 'deleted' } }).finally(() => this.auth.clearSession());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.announcement.set('');
        this.showServerError(error);
      }
    });
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

  private validate(): Partial<Record<Field, string>> | null {
    const found: Partial<Record<Field, string>> = {};
    if (this.hasPassword() && !this.password()) {
      found.password = 'Wpisz hasło.';
    }
    if (this.showCode() && !(/^\d{6}$/.test(this.code()) || this.code().length >= 8)) {
      found.code = 'Wpisz 6-cyfrowy kod z aplikacji albo kod odzyskiwania.';
    }
    if (this.confirm().trim().normalize('NFC').toLocaleUpperCase('pl') !== CONFIRM_WORD) {
      found.confirm = 'Wpisz słowo USUŃ, aby potwierdzić.';
    }
    return Object.keys(found).length ? found : null;
  }

  private showServerError(error: unknown): void {
    const code = apiErrorCode(error);
    const fieldErrors = error instanceof HttpErrorResponse
      ? (error.error as { fieldErrors?: Record<string, string> | null } | null)?.fieldErrors ?? null
      : null;

    if (code === 'REAUTH_REQUIRED') {
      this.reauth.set(true);
      return;
    }
    if (code === 'INVALID_CREDENTIALS') {
      this.failField('password', 'Hasło jest nieprawidłowe.');
      return;
    }
    if (code === 'MFA_CODE_REQUIRED') {
      this.codeDemanded.set(true);
      this.failField('code', 'Wpisz kod z aplikacji albo kod odzyskiwania.');
      return;
    }
    if (code && WRONG_CODE_ERRORS.has(code)) {
      this.failField('code', 'Kod jest nieprawidłowy albo został już użyty. Wpisz nowy kod.');
      return;
    }
    if (code === 'VALIDATION_ERROR' && fieldErrors) {
      const mapped: Partial<Record<Field, string>> = {};
      if (fieldErrors['confirm']) mapped.confirm = 'Wpisz słowo USUŃ, aby potwierdzić.';
      if (fieldErrors['password']) mapped.password = 'Wpisz hasło.';
      if (fieldErrors['code']) mapped.code = 'Wpisz 6-cyfrowy kod z aplikacji albo kod odzyskiwania.';
      if (Object.keys(mapped).length) {
        this.errors.set(mapped);
        this.focusField(firstField(mapped));
        return;
      }
    }

    if (code === 'MFA_LOCKED') {
      this.lock.start(retryAfterSeconds(error));
      this.summary.set('Zbyt wiele błędnych kodów. Weryfikacja jest chwilowo zablokowana.');
    } else if (code === 'LAST_ADMIN') {
      this.summary.set('Jesteś jedynym administratorem Lendly, więc nie możesz usunąć konta. Najpierw nadaj tę rolę innej osobie.');
    } else if (code === 'RATE_LIMITED') {
      this.summary.set('Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie.');
    } else if (code === 'CSRF_INVALID' || code === 'ORIGIN_NOT_ALLOWED') {
      this.summary.set(authenticationMessage(error, 'profile'));
    } else {
      this.summary.set(error instanceof HttpErrorResponse && error.status === 0
        ? 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.'
        : 'Nie udało się usunąć konta. Spróbuj ponownie.');
    }
    this.focusId('delete-summary');
  }

  private failField(field: Field, message: string): void {
    this.errors.set({ [field]: message });
    this.focusField(field);
  }

  private clearError(field: Field): void {
    if (this.errors()[field]) {
      this.errors.update(({ [field]: _removed, ...rest }) => rest);
    }
  }

  private focusField(field: Field): void {
    this.focusId(`delete-${field}`, true);
  }

  private focusId(id: string, select = false): void {
    setTimeout(() => {
      const element = this.document.getElementById(id) as HTMLInputElement | null;
      element?.focus();
      if (select && element?.select) {
        element.select();
      }
    });
  }

  private saveFile(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = name;
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url));
  }
}

function firstField(errors: Partial<Record<Field, string>>): Field {
  return (['password', 'code', 'confirm'] as const).find((field) => errors[field]) ?? 'confirm';
}

function fileNameFrom(header: string | null): string {
  const match = header?.match(/filename="?([^";]+)"?/i);
  const name = match?.[1]?.trim();
  return name && /^[\w.-]+$/.test(name) ? name : EXPORT_FILE;
}
