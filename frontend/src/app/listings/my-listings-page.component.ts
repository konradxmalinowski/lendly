import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { setPrivatePageMetadata } from '../auth/private-page-metadata';
import { priceLabel, statusLabels } from '../catalog/catalog-format';
import { OwnerListing, Page } from '../catalog/catalog.models';
import { ListingsService } from './listings.service';
import { reservationErrorMessage } from '../reservations/reservation-format';

@Component({
  selector: 'lendly-my-listings-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="my-listings" aria-labelledby="page-title">
      <header class="my-heading">
        <div>
          <p class="page-eyebrow">Twoje konto</p>
          <h1 id="page-title" class="page-title" tabindex="-1">Moje ogłoszenia</h1>
        </div>
        <a class="button-primary" routerLink="/listings/new">Dodaj ogłoszenie</a>
      </header>

      @if (actionError()) {
        <p class="notice-error" role="alert">{{ actionError() }}</p>
      }
      @if (actionMessage()) {
        <p class="notice-success" role="status">{{ actionMessage() }}</p>
      }

      @if (loading() && !page()) {
        <p class="state-message" role="status">Wczytywanie ogłoszeń…</p>
      } @else if (loadError()) {
        <div class="notice-error" role="alert">
          <p>{{ loadError() }}</p>
          <button class="button-secondary" type="button" (click)="load(currentPage)">Spróbuj ponownie</button>
        </div>
      } @else if (page(); as results) {
        @if (results.items.length === 0) {
          <div class="empty-state">
            <h2>Nie masz jeszcze ogłoszeń</h2>
            <p>Dodaj rzecz, którą możesz komuś pożyczyć. Wystarczy zdjęcie, krótki opis i punkt odbioru.</p>
          </div>
        } @else {
          <ul class="owner-list">
            @for (item of results.items; track item.id) {
              <li class="owner-row">
                <span class="row-photo">
                  @if (item.images[0]; as cover) {
                    <img [src]="cover.url" alt="" width="96" height="72" loading="lazy">
                  }
                </span>
                <span class="row-main">
                  <span class="row-title">{{ item.title }}</span>
                  <span class="row-meta">
                    <span class="row-status" [attr.data-status]="item.status">{{ status(item) }}</span>
                    <span class="price-line">{{ price(item) }}</span>
                  </span>
                  @if (item.status === 'HIDDEN') {
                    <span class="row-moderation">Powód: {{ item.moderationReason }}</span>
                  }
                </span>
                <span class="row-actions">
                  @if (item.status === 'ACTIVE') {
                    <a class="button-quiet" [routerLink]="['/items', item.id]">Podgląd<span class="visually-hidden">: {{ item.title }}</span></a>
                  }
                  @if (item.status !== 'HIDDEN') {
                    <a class="button-secondary" [routerLink]="['/listings', item.id, 'edit']">Edytuj<span class="visually-hidden">: {{ item.title }}</span></a>
                  }
                  @if (item.status !== 'ARCHIVED' && item.status !== 'HIDDEN') {
                    <button class="button-quiet" type="button" [disabled]="busyId() === item.id" (click)="archive(item)">
                      {{ busyId() === item.id ? 'Archiwizowanie…' : 'Archiwizuj' }}<span class="visually-hidden">: {{ item.title }}</span>
                    </button>
                  }
                </span>
              </li>
            }
          </ul>
          @if (results.totalPages > 1) {
            <nav class="pagination" aria-label="Strony ogłoszeń">
              <button class="button-secondary" type="button" [disabled]="results.page === 0" (click)="load(results.page - 1)">Poprzednia</button>
              <span>Strona {{ results.page + 1 }} z {{ results.totalPages }}</span>
              <button class="button-secondary" type="button" [disabled]="results.page + 1 >= results.totalPages" (click)="load(results.page + 1)">Następna</button>
            </nav>
          }
        }
      }
    </section>
  `,
  styleUrl: './my-listings-page.component.scss'
})
export class MyListingsPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Moje ogłoszenia | Lendly', 'Zarządzaj swoimi ogłoszeniami w Lendly.');
  private readonly listings = inject(ListingsService);
  currentPage = 0;

  readonly page = signal<Page<OwnerListing> | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly actionError = signal('');
  readonly actionMessage = signal('');
  readonly busyId = signal<string | null>(null);

  constructor() {
    this.load(0);
  }

  status(item: OwnerListing): string {
    return statusLabels[item.status];
  }

  price(item: OwnerListing): string {
    return priceLabel(item.pricingType, item.dailyPrice);
  }

  load(page: number): void {
    this.currentPage = page;
    this.loading.set(true);
    this.loadError.set('');
    this.listings.mine(page).subscribe({
      next: (results) => {
        this.page.set(results);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(error instanceof HttpErrorResponse && error.status === 0
          ? 'Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.'
          : 'Nie udało się wczytać ogłoszeń. Spróbuj ponownie.');
        this.loading.set(false);
      }
    });
  }

  archive(item: OwnerListing): void {
    this.actionError.set('');
    this.actionMessage.set('');
    this.busyId.set(item.id);
    this.listings.archive(item.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.actionMessage.set(`Ogłoszenie „${item.title}” trafiło do archiwum. Możesz je ponownie opublikować w edycji.`);
        this.load(this.currentPage);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.actionError.set(reservationErrorMessage(error));
      }
    });
  }
}
