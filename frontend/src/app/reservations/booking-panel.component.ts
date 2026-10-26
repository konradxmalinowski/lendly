import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ListingDetail } from '../catalog/catalog.models';
import { DateRange } from './reservation.models';
import {
  addDays, costLabel, daysBetweenInclusive, daysLabel, formatRange, overlapsAny, reservationErrorMessage, warsawToday
} from './reservation-format';
import { ReservationsService } from './reservations.service';

@Component({
  selector: 'lendly-booking-panel',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <section class="booking" aria-labelledby="booking-title">
      <h2 id="booking-title">Rezerwacja</h2>
      @if (!auth.initialized()) {
        <p class="state-message" role="status">Sprawdzanie sesji…</p>
      } @else if (!auth.user()) {
        <p>Aby poprosić o termin, zaloguj się.</p>
        <a class="button-primary" routerLink="/login" [queryParams]="{ returnUrl: '/items/' + listing().id }">Zaloguj się</a>
      } @else if (isOwner()) {
        <p>To Twoje ogłoszenie.</p>
        <a class="button-secondary" [routerLink]="['/listings', listing().id, 'edit']">Zarządzaj ogłoszeniem</a>
      } @else {
        <form (ngSubmit)="submit()" novalidate>
          <div class="date-pair">
            <div class="form-field">
              <label for="booking-start">Od</label>
              <input id="booking-start" type="date" name="start" [min]="today" [max]="maxStart" [(ngModel)]="start" required>
            </div>
            <div class="form-field">
              <label for="booking-end">Do (włącznie)</label>
              <input id="booking-end" type="date" name="end" [min]="start || today" [max]="maxEnd()" [(ngModel)]="end" required>
            </div>
          </div>

          @if (selectionError()) {
            <p class="field-error" role="alert">{{ selectionError() }}</p>
          } @else if (days() > 0) {
            <p class="estimate" aria-live="polite">
              {{ daysLabel(days()) }}, <span class="price-line">{{ estimate() }}</span>
              @if (listing().pricingType === 'PAID') { <span class="estimate-note">&nbsp;(szacunkowo)</span> }
            </p>
          }

          <div class="form-field">
            <label for="booking-note">Wiadomość do właściciela <span class="optional">(opcjonalnie)</span></label>
            <textarea id="booking-note" name="note" maxlength="500" rows="3" [(ngModel)]="note"
              placeholder="Np. do czego potrzebujesz przedmiotu"></textarea>
          </div>

          @if (errorMessage()) {
            <p class="notice-error" role="alert">{{ errorMessage() }}</p>
          }
          <button class="button-primary" type="submit" [disabled]="submitting() || !!selectionError() || days() === 0">
            {{ submitting() ? 'Wysyłanie…' : 'Wyślij prośbę o rezerwację' }}
          </button>
          <p class="field-hint">Właściciel ma 48 godzin na odpowiedź. Lendly nie pobiera płatności, rozliczacie się bezpośrednio przy odbiorze.</p>
        </form>
      }

      <div class="busy">
        <h3>Zajęte terminy</h3>
        @if (busyError()) {
          <p class="field-error">Nie udało się wczytać zajętych terminów.</p>
        } @else if (busy().length === 0) {
          <p class="state-message">Wszystkie terminy w najbliższych 6 miesiącach są wolne.</p>
        } @else {
          <ul>
            @for (range of busy(); track range.startDate) {
              <li>{{ formatRange(range) }}</li>
            }
          </ul>
        }
      </div>
    </section>
  `,
  styleUrl: './booking-panel.component.scss'
})
export class BookingPanelComponent {
  readonly listing = input.required<ListingDetail>();
  readonly auth = inject(AuthService);
  private readonly reservations = inject(ReservationsService);
  private readonly router = inject(Router);

  readonly today = warsawToday();
  readonly maxStart = addDays(this.today, 180);
  readonly busy = signal<DateRange[]>([]);
  readonly busyError = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal('');
  readonly startValue = signal('');
  readonly endValue = signal('');
  note = '';
  readonly formatRange = formatRange;
  readonly daysLabel = daysLabel;

  get start(): string { return this.startValue(); }
  set start(value: string) {
    this.startValue.set(value);
    if (value && (!this.endValue() || this.endValue() < value)) this.endValue.set(value);
  }
  get end(): string { return this.endValue(); }
  set end(value: string) { this.endValue.set(value); }

  readonly isOwner = computed(() => this.auth.user()?.id === this.listing().owner.id);
  readonly maxEnd = computed(() => this.startValue() ? addDays(this.startValue(), 29) : this.maxStart);
  readonly days = computed(() => {
    const start = this.startValue();
    const end = this.endValue();
    return start && end && end >= start ? daysBetweenInclusive(start, end) : 0;
  });
  readonly estimate = computed(() => {
    const item = this.listing();
    // Whole grosze avoid floating-point drift; the server computes the binding amount with BigDecimal.
    const cents = Math.round((item.dailyPrice ?? 0) * 100) * this.days();
    return costLabel(item.pricingType, cents / 100);
  });
  readonly selectionError = computed(() => {
    const start = this.startValue();
    const end = this.endValue();
    if (!start || !end) return '';
    if (start < this.today) return 'Termin nie może zaczynać się w przeszłości.';
    if (start > this.maxStart) return 'Możesz rezerwować najwyżej 180 dni naprzód.';
    if (end < start) return 'Data końca nie może być wcześniejsza niż data początku.';
    if (this.days() > 30) return 'Jedna rezerwacja może trwać najwyżej 30 dni.';
    if (overlapsAny(start, end, this.busy())) return 'Wybrany termin pokrywa się z zajętym terminem.';
    return '';
  });

  constructor() {
    toObservable(this.listing).pipe(
      switchMap((item) => this.reservations.availability(item.id)),
      takeUntilDestroyed()
    ).subscribe({
      next: (ranges) => this.busy.set(ranges),
      error: () => this.busyError.set(true)
    });
  }

  submit(): void {
    if (this.days() === 0 || this.selectionError()) return;
    this.errorMessage.set('');
    this.submitting.set(true);
    const note = this.note.trim() || null;
    this.reservations.request(this.listing().id, this.startValue(), this.endValue(), note).subscribe({
      next: (reservation) => void this.router.navigate(['/reservations', reservation.id], { queryParams: { created: 1 } }),
      error: (error: unknown) => {
        this.errorMessage.set(reservationErrorMessage(error));
        this.submitting.set(false);
        this.reservations.availability(this.listing().id).subscribe({ next: (ranges) => this.busy.set(ranges), error: () => undefined });
      }
    });
  }
}
