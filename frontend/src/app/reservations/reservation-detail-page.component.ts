import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap, tap } from 'rxjs';
import { setPrivatePageMetadata } from '../auth/private-page-metadata';
import { priceLabel } from '../catalog/catalog-format';
import { ReportButtonComponent } from '../moderation/report-button.component';
import { ListingMapComponent, MapPin } from '../shared/map/listing-map.component';
import { ReservationAction, ReservationDetail } from './reservation.models';
import {
  costLabel, daysLabel, formatDeadline, formatRange, reservationErrorMessage, reservationStatusLabels
} from './reservation-format';
import { ReservationsService } from './reservations.service';

const SUCCESS: Record<ReservationAction, string> = {
  ACCEPT: 'Rezerwacja została zaakceptowana. Pożyczający widzi teraz punkt odbioru.',
  REJECT: 'Prośba została odrzucona.',
  CANCEL: 'Rezerwacja została anulowana, a termin zwolniony.',
  HANDOVER: 'Oznaczono przedmiot jako wydany.',
  RETURN: 'Oznaczono przedmiot jako zwrócony. Rezerwacja jest zakończona.'
};

@Component({
  selector: 'lendly-reservation-detail-page',
  standalone: true,
  imports: [FormsModule, RouterLink, ListingMapComponent, ReportButtonComponent],
  templateUrl: './reservation-detail-page.component.html',
  styleUrl: './reservation-detail-page.component.scss'
})
export class ReservationDetailPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Rezerwacja | Lendly', 'Szczegóły rezerwacji w Lendly.');
  private readonly reservations = inject(ReservationsService);
  private readonly route = inject(ActivatedRoute);

  readonly reservation = signal<ReservationDetail | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly busy = signal(false);
  readonly actionError = signal('');
  readonly successMessage = signal(this.route.snapshot.queryParamMap.get('created') ? 'Prośba została wysłana do właściciela.' : '');
  readonly confirming = signal<'REJECT' | 'CANCEL' | null>(null);
  reason = '';

  readonly statusLabels = reservationStatusLabels;
  readonly daysLabel = daysLabel;
  readonly formatDeadline = formatDeadline;

  readonly isOwner = computed(() => this.reservation()?.role === 'OWNER');
  readonly range = computed(() => {
    const item = this.reservation();
    return item ? formatRange(item) : '';
  });
  readonly cost = computed(() => {
    const item = this.reservation();
    return item ? costLabel(item.pricingType, item.estimatedCost) : '';
  });
  readonly dailyPrice = computed(() => {
    const item = this.reservation();
    return item ? priceLabel(item.pricingType, item.dailyPrice) : '';
  });
  readonly handoverPins = computed<MapPin[]>(() => {
    const item = this.reservation();
    if (!item || item.handoverLat === null || item.handoverLng === null) return [];
    return [{ id: 'handover', lat: item.handoverLat, lng: item.handoverLng, text: 'Odbiór', label: 'Dokładny punkt odbioru' }];
  });
  readonly area = computed(() => {
    const item = this.reservation();
    if (!item || item.handoverLat !== null || item.approximateLat === null || item.approximateLng === null) return null;
    return { lat: item.approximateLat, lng: item.approximateLng };
  });
  readonly reasonRequired = computed(() => this.confirming() === 'CANCEL' && this.isOwner());

  constructor() {
    this.route.paramMap.pipe(
      tap(() => this.loading.set(true)),
      switchMap((params) => this.reservations.detail(params.get('id') ?? '')),
      takeUntilDestroyed()
    ).subscribe({
      next: (item) => {
        this.reservation.set(item);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(reservationErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  can(action: ReservationAction): boolean {
    return this.reservation()?.allowedActions.includes(action) ?? false;
  }

  startConfirm(action: 'REJECT' | 'CANCEL'): void {
    this.reason = '';
    this.actionError.set('');
    this.confirming.set(action);
    setTimeout(() => document.getElementById('action-reason')?.focus());
  }

  closeConfirm(): void {
    this.confirming.set(null);
    // The confirmation form is removed, so focus goes back to the actions instead of falling to the page start.
    setTimeout(() => document.querySelector<HTMLElement>('.actions .button-row button')?.focus());
  }

  run(action: ReservationAction): void {
    const item = this.reservation();
    if (!item) return;
    const reason = this.reason.trim() || null;
    if (action === 'CANCEL' && this.isOwner() && !reason) {
      this.actionError.set('Podaj powód anulowania. Pożyczający go zobaczy.');
      return;
    }
    this.busy.set(true);
    this.actionError.set('');
    this.successMessage.set('');
    this.reservations.act(item.id, action, reason).subscribe({
      next: (updated) => {
        this.reservation.set(updated);
        this.confirming.set(null);
        this.successMessage.set(SUCCESS[action]);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        this.actionError.set(reservationErrorMessage(error));
        this.busy.set(false);
      }
    });
  }

  timestamp(value: string | null): string {
    return value ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Warsaw' }).format(new Date(value)) : '';
  }
}
