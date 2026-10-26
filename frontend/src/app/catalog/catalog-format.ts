import { HttpErrorResponse } from '@angular/common/http';
import { ItemCondition, ListingStatus, PricingType } from './catalog.models';

const priceFormat = new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' });

export const conditionLabels: Record<ItemCondition, string> = {
  NEW: 'Nowy',
  VERY_GOOD: 'Bardzo dobry',
  GOOD: 'Dobry',
  FAIR: 'Widoczne ślady użytkowania'
};

export const statusLabels: Record<ListingStatus, string> = {
  DRAFT: 'Szkic',
  ACTIVE: 'Opublikowane',
  ARCHIVED: 'W archiwum',
  HIDDEN: 'Ukryte przez moderację'
};

export function priceLabel(pricingType: PricingType, dailyPrice: number | null): string {
  if (pricingType === 'FREE' || dailyPrice === null) {
    return 'Za darmo';
  }
  return `${priceFormat.format(dailyPrice)} / dzień`;
}

export function distanceLabel(distanceKm: number | null): string | null {
  if (distanceKm === null) {
    return null;
  }
  return distanceKm < 1 ? 'mniej niż 1 km' : `ok. ${distanceKm.toLocaleString('pl-PL', { maximumFractionDigits: 1 })} km`;
}

export function catalogErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.';
    }
    if (error.status === 429) {
      return 'Wysłano zbyt wiele zapytań. Odczekaj chwilę i spróbuj ponownie.';
    }
    if (error.status === 404) {
      return 'Nie znaleziono tego ogłoszenia. Mogło zostać zarchiwizowane.';
    }
  }
  return 'Nie udało się wczytać danych. Spróbuj ponownie.';
}
