import { HttpErrorResponse } from '@angular/common/http';

export function authenticationMessage(error: unknown, action: 'login' | 'register' | 'google' | 'profile'): string {
  const code = error instanceof HttpErrorResponse
    ? (error.error as { code?: string } | null)?.code
    : undefined;

  if (error instanceof HttpErrorResponse && error.status === 503 && action === 'google') {
    return 'Logowanie przez Google nie jest teraz skonfigurowane. Użyj adresu e-mail i hasła.';
  }
  switch (code) {
    case 'INVALID_CREDENTIALS':
      return 'Nieprawidłowy adres e-mail lub hasło.';
    case 'EMAIL_ALREADY_REGISTERED':
      return 'Nie można utworzyć konta dla tego adresu e-mail.';
    case 'REGISTRATION_DISABLED':
      return 'Rejestracja jest obecnie wyłączona. Zaloguj się na istniejące konto.';
    case 'RATE_LIMITED':
      return 'Wykonano zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie.';
    case 'OIDC_LINK_REQUIRED':
      return 'Zaloguj się na istniejące konto, a potem połącz je z Google w profilu.';
    case 'GOOGLE_IDENTITY_ALREADY_LINKED':
      return 'To konto Google jest już połączone z innym kontem.';
    case 'CSRF_INVALID':
    case 'ORIGIN_NOT_ALLOWED':
      return 'Nie udało się bezpiecznie wysłać formularza. Odśwież stronę i spróbuj ponownie.';
    case 'ACCOUNT_SUSPENDED':
      return 'To konto jest obecnie niedostępne.';
    case 'MAIL_UNAVAILABLE':
      return 'Nie możemy teraz wysłać wiadomości. Spróbuj ponownie za chwilę.';
    case 'EMAIL_NOT_VERIFIED':
      return 'Najpierw potwierdź adres e-mail.';
    case 'MFA_SETUP_REQUIRED':
      return 'Najpierw włącz weryfikację dwuetapową w profilu.';
    default:
      if (error instanceof HttpErrorResponse && error.status === 0) {
        return 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.';
      }
      return action === 'profile'
        ? 'Nie udało się zapisać zmian. Spróbuj ponownie.'
        : 'Nie udało się wykonać tej czynności. Spróbuj ponownie.';
  }
}

export function safeReturnPath(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return '/';
  }
  try {
    const target = new URL(value, window.location.origin);
    return target.origin === window.location.origin ? `${target.pathname}${target.search}${target.hash}` : '/';
  } catch {
    return '/';
  }
}

export function isGoogleAuthorizationUrl(value: string): boolean {
  try {
    const target = new URL(value);
    return target.protocol === 'https:' && target.hostname === 'accounts.google.com';
  } catch {
    return false;
  }
}

export function apiErrorCode(error: unknown): string | null {
  return error instanceof HttpErrorResponse
    ? (error.error as { code?: string } | null)?.code ?? null
    : null;
}

export function normalizeNumericCode(value: string, length = 6): string {
  return value.replace(/\D/g, '').slice(0, length);
}

export function normalizeRecoveryCode(value: string): string {
  return value
    .toLowerCase()
    .replace(/[\u2010-\u2015\u2212]/g, '-')
    .replace(/\s+/g, '')
    .slice(0, 32);
}

export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

export function attemptsLabel(count: number): string {
  if (count === 1) {
    return 'Została 1 próba.';
  }
  const lastTwo = count % 100;
  const last = count % 10;
  return last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14)
    ? `Zostały ${count} próby.`
    : `Zostało ${count} prób.`;
}

/** Seconds from the Retry-After header, falling back to the 60 s per-user resend window. */
export function retryAfterSeconds(error: unknown): number {
  const header = error instanceof HttpErrorResponse ? error.headers.get('Retry-After') : null;
  const seconds = header ? Number.parseInt(header, 10) : Number.NaN;
  return Number.isFinite(seconds) && seconds > 0 ? Math.min(seconds, 3600) : 60;
}

export function attemptsRemaining(error: unknown): number | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const body = error.error as {
    attemptsRemaining?: unknown;
    details?: { attemptsRemaining?: unknown } | null;
    fieldErrors?: Record<string, unknown> | null;
  } | null;
  const candidate = body?.attemptsRemaining ?? body?.details?.attemptsRemaining ?? body?.fieldErrors?.['attemptsRemaining'];
  const first = Array.isArray(candidate) ? candidate[0] : candidate;
  const value = typeof first === 'string' ? Number.parseInt(first, 10) : first;
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null;
}

export function emailCodeMessage(error: unknown, remaining: number | null): string {
  switch (apiErrorCode(error)) {
    case 'VERIFICATION_CODE_INVALID':
      return remaining === null ? 'Kod jest nieprawidłowy.' : `Kod jest nieprawidłowy. ${attemptsLabel(remaining)}`;
    case 'VERIFICATION_CODE_EXPIRED':
      return 'Ten kod wygasł. Wyślij nowy.';
    case 'VERIFICATION_ATTEMPTS_EXCEEDED':
      return 'Wykorzystano wszystkie próby dla tego kodu. Wyślij nowy.';
    default:
      return authenticationMessage(error, 'login');
  }
}

export function mfaCodeMessage(error: unknown, kind: 'app' | 'recovery' = 'app'): string {
  switch (apiErrorCode(error)) {
    case 'MFA_CODE_INVALID':
      return kind === 'recovery'
        ? 'Kod odzyskiwania jest nieprawidłowy albo został już użyty.'
        : 'Kod jest nieprawidłowy. Sprawdź, czy czas w telefonie jest ustawiony automatycznie, i wpisz nowy kod.';
    case 'MFA_ALREADY_ENABLED':
      return 'Weryfikacja dwuetapowa jest już włączona.';
    case 'MFA_LOCKED':
      return 'Zbyt wiele błędnych kodów. Weryfikacja jest chwilowo zablokowana.';
    case 'REAUTH_REQUIRED':
      return 'Ta zmiana wymaga ponownego zalogowania.';
    case 'MFA_NOT_ENABLED':
      return 'Weryfikacja dwuetapowa nie jest włączona.';
    case 'MFA_SETUP_NOT_STARTED':
      return 'Konfiguracja wygasła. Zacznij ją od nowa.';
    case 'MFA_UNAVAILABLE':
      return 'Weryfikacja dwuetapowa jest teraz niedostępna na tym serwerze.';
    case 'MFA_REQUIRED_FOR_ROLE':
      return 'Twoja rola wymaga weryfikacji dwuetapowej, więc nie można jej wyłączyć.';
    default:
      return authenticationMessage(error, 'login');
  }
}
