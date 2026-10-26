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
