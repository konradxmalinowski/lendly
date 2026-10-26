import { HttpErrorResponse } from '@angular/common/http';
import { PricingType } from '../catalog/catalog.models';
import { DateRange, ReservationStatus } from './reservation.models';

const priceFormat = new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' });
const dayFormat = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const deadlineFormat = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Warsaw' });

export const reservationStatusLabels: Record<ReservationStatus, string> = {
  PENDING: 'Czeka na decyzję',
  ACCEPTED: 'Zaakceptowana',
  REJECTED: 'Odrzucona',
  CANCELLED: 'Anulowana',
  EXPIRED: 'Wygasła',
  ACTIVE: 'Przedmiot wydany',
  COMPLETED: 'Zakończona'
};

/** Today's date in the Europe/Warsaw calendar as YYYY-MM-DD, matching the server's notion of "today". */
export function warsawToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Warsaw' }).format(new Date());
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function daysBetweenInclusive(start: string, end: string): number {
  return Math.round((Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`)) / 86_400_000) + 1;
}

export function formatDay(isoDate: string): string {
  return dayFormat.format(new Date(`${isoDate}T00:00:00Z`));
}

export function formatRange(range: DateRange): string {
  return range.startDate === range.endDate ? formatDay(range.startDate) : `${formatDay(range.startDate)} - ${formatDay(range.endDate)}`;
}

export function formatDeadline(isoInstant: string): string {
  return deadlineFormat.format(new Date(isoInstant));
}

export function daysLabel(days: number): string {
  return days === 1 ? '1 dzień' : `${days} dni`;
}

export function costLabel(pricingType: PricingType, cost: number): string {
  return pricingType === 'FREE' ? 'Za darmo' : priceFormat.format(cost);
}

export function overlapsAny(start: string, end: string, ranges: DateRange[]): boolean {
  return ranges.some((range) => start <= range.endDate && end >= range.startDate);
}

export function reservationErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Nie udało się wykonać tej czynności. Spróbuj ponownie.';
  }
  const code = (error.error as { code?: string } | null)?.code;
  switch (code) {
    case 'DATES_UNAVAILABLE':
      return 'Ten termin jest już zajęty. Wybierz inne daty.';
    case 'DATES_RESERVED':
      return 'W tym okresie jest już prośba albo rezerwacja. Wybierz inne daty.';
    case 'BLOCK_OVERLAP':
      return 'Ten okres pokrywa się z innym niedostępnym terminem.';
    case 'OWN_LISTING':
      return 'Nie możesz zarezerwować własnego ogłoszenia.';
    case 'LISTING_UNAVAILABLE':
      return 'To ogłoszenie nie przyjmuje już rezerwacji.';
    case 'TOO_MANY_PENDING':
      return 'Masz już 5 próśb czekających na odpowiedź. Poczekaj na decyzję albo anuluj którąś z nich.';
    case 'INVALID_DATES':
      return 'Wybierz termin od dziś do 180 dni naprzód, najwyżej 30 dni.';
    case 'REASON_REQUIRED':
      return 'Podaj powód anulowania.';
    case 'INVALID_TRANSITION':
      return 'Stan rezerwacji właśnie się zmienił. Odśwież stronę.';
    case 'RATE_LIMITED':
      return 'Wysłano zbyt wiele próśb. Spróbuj ponownie później.';
    case 'LISTING_HAS_RESERVATIONS':
      return 'Ogłoszenie ma zaakceptowane albo trwające rezerwacje. Zakończ je lub anuluj przed archiwizacją.';
  }
  if (error.status === 0) {
    return 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.';
  }
  if (error.status === 401) {
    return 'Sesja wygasła. Zaloguj się ponownie.';
  }
  if (error.status === 404) {
    return 'Nie znaleziono tej rezerwacji.';
  }
  return 'Nie udało się wykonać tej czynności. Spróbuj ponownie.';
}
