import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { setPrivatePageMetadata } from '../auth/private-page-metadata';
import { Page } from '../catalog/catalog.models';
import { ReservationSummary } from './reservation.models';
import { costLabel, daysLabel, formatDeadline, formatRange, reservationErrorMessage, reservationStatusLabels } from './reservation-format';
import { ReservationsService } from './reservations.service';

@Component({
  selector: 'lendly-reservations-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="reservations-page" aria-labelledby="page-title">
      <p class="page-eyebrow">Twoje konto</p>
      <h1 id="page-title" class="page-title" tabindex="-1">Rezerwacje</h1>

      <nav class="role-tabs" aria-label="Rodzaj rezerwacji">
        <a routerLink="/reservations" [queryParams]="{}" [attr.aria-current]="role() === 'borrower' ? 'page' : null">Pożyczam</a>
        <a routerLink="/reservations" [queryParams]="{ role: 'owner' }" [attr.aria-current]="role() === 'owner' ? 'page' : null">Udostępniam</a>
      </nav>

      @if (loading() && !page()) {
        <p class="state-message" role="status">Wczytywanie rezerwacji…</p>
      } @else if (errorMessage()) {
        <div class="notice-error" role="alert">
          <p>{{ errorMessage() }}</p>
          <button class="button-secondary" type="button" (click)="load(currentPage)">Spróbuj ponownie</button>
        </div>
      } @else if (page(); as results) {
        @if (results.items.length === 0) {
          <div class="empty-state">
            @if (role() === 'owner') {
              <h2>Nikt jeszcze nie poprosił o Twoje rzeczy</h2>
              <p>Prośby o rezerwację Twoich ogłoszeń pojawią się tutaj.</p>
              <a class="button-secondary" routerLink="/account/listings">Moje ogłoszenia</a>
            } @else {
              <h2>Nie masz jeszcze rezerwacji</h2>
              <p>Znajdź przedmiot i wyślij prośbę o termin.</p>
              <a class="button-secondary" routerLink="/items">Przeglądaj ogłoszenia</a>
            }
          </div>
        } @else {
          <ul class="reservation-list">
            @for (item of results.items; track item.id) {
              <li>
                <a class="reservation-row" [routerLink]="['/reservations', item.id]" [class.needs-decision]="needsDecision(item)">
                  <span class="row-photo">
                    @if (item.listing.coverImageUrl) {
                      <img [src]="item.listing.coverImageUrl" alt="" width="96" height="72" loading="lazy">
                    }
                  </span>
                  <span class="row-main">
                    <span class="row-title">{{ item.listing.title }}</span>
                    <span class="row-meta">{{ formatRange(item) }} · {{ daysLabel(item.days) }} · {{ cost(item) }}</span>
                    <span class="row-meta">{{ item.role === 'OWNER' ? 'Pożycza' : 'Udostępnia' }}: {{ item.counterpart.displayName }}</span>
                  </span>
                  <span class="row-status" [attr.data-status]="item.status">
                    {{ needsDecision(item) ? 'Czeka na Twoją decyzję' : statusLabels[item.status] }}
                    @if (item.status === 'PENDING') {
                      <span class="row-deadline">do {{ formatDeadline(item.expiresAt) }}</span>
                    }
                  </span>
                </a>
              </li>
            }
          </ul>
          @if (results.totalPages > 1) {
            <nav class="pagination" aria-label="Strony rezerwacji">
              <button class="button-secondary" type="button" [disabled]="results.page === 0" (click)="load(results.page - 1)">Poprzednia</button>
              <span>Strona {{ results.page + 1 }} z {{ results.totalPages }}</span>
              <button class="button-secondary" type="button" [disabled]="results.page + 1 >= results.totalPages" (click)="load(results.page + 1)">Następna</button>
            </nav>
          }
        }
      }
    </section>
  `,
  styleUrl: './reservations-page.component.scss'
})
export class ReservationsPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Rezerwacje | Lendly', 'Twoje prośby o rezerwację i rezerwacje Twoich ogłoszeń.');
  private readonly reservations = inject(ReservationsService);
  private subscription: Subscription | null = null;
  currentPage = 0;

  readonly role = signal<'borrower' | 'owner'>('borrower');
  readonly page = signal<Page<ReservationSummary> | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly statusLabels = reservationStatusLabels;
  readonly daysLabel = daysLabel;
  readonly formatDeadline = formatDeadline;

  constructor() {
    inject(ActivatedRoute).queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.role.set(params.get('role') === 'owner' ? 'owner' : 'borrower');
      this.page.set(null);
      this.load(0);
    });
  }

  formatRange(item: ReservationSummary): string {
    return formatRange(item);
  }

  cost(item: ReservationSummary): string {
    return costLabel(item.pricingType, item.estimatedCost);
  }

  needsDecision(item: ReservationSummary): boolean {
    return item.role === 'OWNER' && item.status === 'PENDING';
  }

  load(page: number): void {
    this.currentPage = page;
    this.loading.set(true);
    this.errorMessage.set('');
    this.subscription?.unsubscribe();
    this.subscription = this.reservations.list(this.role(), page).subscribe({
      next: (results) => {
        this.page.set(results);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(reservationErrorMessage(error));
        this.loading.set(false);
      }
    });
  }
}
