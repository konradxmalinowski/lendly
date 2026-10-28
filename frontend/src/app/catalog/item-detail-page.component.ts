import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap, tap } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ReportButtonComponent } from '../moderation/report-button.component';
import { BookingPanelComponent } from '../reservations/booking-panel.component';
import { ListingMapComponent } from '../shared/map/listing-map.component';
import { catalogErrorMessage, conditionLabels, priceLabel } from './catalog-format';
import { listingSeo } from '../seo/listing-seo';
import { SeoService } from '../seo/seo.service';
import { Category, ListingDetail } from './catalog.models';
import { CatalogService } from './catalog.service';

@Component({
  selector: 'lendly-item-detail-page',
  standalone: true,
  imports: [RouterLink, ListingMapComponent, BookingPanelComponent, ReportButtonComponent],
  template: `
    <article class="detail-page" aria-labelledby="page-title">
      <a class="back-link" routerLink="/items">Wróć do wyników</a>

      @if (loading()) {
        <h1 id="page-title" class="visually-hidden" tabindex="-1">Ogłoszenie</h1>
        <p class="state-message" role="status">Wczytywanie ogłoszenia…</p>
      } @else if (errorMessage()) {
        <h1 id="page-title" class="page-title" tabindex="-1">Ogłoszenie niedostępne</h1>
        <p class="notice-error" role="alert">{{ errorMessage() }}</p>
        <a class="button-secondary" routerLink="/items">Przeglądaj inne ogłoszenia</a>
      } @else if (listing(); as item) {
        <div class="detail-layout">
          <section class="gallery" aria-label="Zdjęcia">
            @if (selectedImage(); as image) {
              <img class="gallery-main" [src]="image.url" [alt]="'Zdjęcie: ' + item.title" [width]="image.width" [height]="image.height">
            }
            @if (item.images.length > 1) {
              <div class="gallery-thumbs">
                @for (image of item.images; track image.id; let index = $index) {
                  <button type="button" [attr.aria-pressed]="selectedIndex() === index"
                    [attr.aria-label]="'Pokaż zdjęcie ' + (index + 1) + ' z ' + item.images.length" (click)="selectedIndex.set(index)">
                    <img [src]="image.url" alt="" loading="lazy" width="96" height="72">
                  </button>
                }
              </div>
            }
          </section>

          <div class="summary">
            <p class="page-eyebrow">{{ categoryName() }}</p>
            <h1 id="page-title" class="page-title" tabindex="-1">{{ item.title }}</h1>
            <p class="detail-price price-line">{{ price() }}</p>
            <dl class="facts">
              <div><dt>Stan</dt><dd>{{ condition() }}</dd></div>
              <div><dt>Okolica</dt><dd>{{ item.owner.areaLabel || 'Nie podano' }}</dd></div>
              <div><dt>Dodano</dt><dd>{{ date(item.publishedAt) }}</dd></div>
            </dl>
            <section class="owner" aria-labelledby="owner-title">
              <h2 id="owner-title">Udostępnia</h2>
              <p class="owner-name">{{ item.owner.displayName }}</p>
              <p class="owner-since">W Lendly od {{ date(item.owner.registeredAt) }}</p>
            </section>
            <lendly-booking-panel [listing]="item" />
            @if (canReport()) {
              <div class="report-links">
                <lendly-report-button targetType="LISTING" [targetId]="item.id" label="Zgłoś ogłoszenie" />
                <lendly-report-button targetType="USER" [targetId]="item.owner.id" label="Zgłoś użytkownika" />
              </div>
            }
          </div>
        </div>

        <section class="description" aria-labelledby="description-title">
          <h2 id="description-title">Opis</h2>
          <p>{{ item.description }}</p>
        </section>

        <section class="location" aria-labelledby="location-title">
          <h2 id="location-title">Okolica odbioru</h2>
          <p class="location-note">Zaznaczony obszar ma promień około 600 m. Dokładne miejsce odbioru ustalicie po akceptacji rezerwacji.</p>
          <lendly-listing-map class="location-map" [area]="{ lat: item.lat, lng: item.lng }" regionLabel="Mapa z przybliżoną okolicą odbioru"
            failureText="Mapa jest teraz niedostępna. Okolica jest podana powyżej." />
        </section>
      }
    </article>
  `,
  styleUrl: './item-detail-page.component.scss'
})
export class ItemDetailPageComponent {
  private readonly catalog = inject(CatalogService);
  private readonly seo = inject(SeoService);

  readonly listing = signal<ListingDetail | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly selectedIndex = signal(0);
  private readonly auth = inject(AuthService);
  readonly canReport = computed(() => {
    const user = this.auth.user();
    const item = this.listing();
    return !!user && !!item && user.id !== item.owner.id;
  });
  readonly selectedImage = computed(() => this.listing()?.images[this.selectedIndex()] ?? null);
  readonly price = computed(() => {
    const item = this.listing();
    return item ? priceLabel(item.pricingType, item.dailyPrice) : '';
  });
  readonly condition = computed(() => {
    const item = this.listing();
    return item ? conditionLabels[item.condition] : '';
  });
  readonly categoryName = computed(() => {
    const key = this.listing()?.categoryKey;
    return this.categories().find((category) => category.key === key)?.displayName ?? '';
  });

  constructor() {
    effect(() => {
      const item = this.listing();
      if (item) {
        this.seo.apply(listingSeo(item, this.categoryName()));
      }
    });
    this.catalog.categories().pipe(takeUntilDestroyed()).subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.categories.set([])
    });
    inject(ActivatedRoute).paramMap.pipe(
      tap(() => {
        this.loading.set(true);
        this.errorMessage.set('');
        this.selectedIndex.set(0);
      }),
      switchMap((params) => this.catalog.detail(params.get('id') ?? '')),
      takeUntilDestroyed()
    ).subscribe({
      next: (listing) => {
        this.listing.set(listing);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(catalogErrorMessage(error));
        this.loading.set(false);
        this.seo.apply({ title: 'Ogłoszenie niedostępne | Lendly', description: 'To ogłoszenie nie jest już dostępne.', noindex: true });
      }
    });
  }

  date(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('pl-PL', { dateStyle: 'long' }).format(date);
  }
}
