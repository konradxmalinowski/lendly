import { Component, DestroyRef, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ListingMapComponent, MapPin } from '../shared/map/listing-map.component';
import { catalogErrorMessage, conditionLabels, distanceLabel, priceLabel } from './catalog-format';
import { Category, ListingSummary, MapBounds, Page, PricingType, SearchQuery, SearchSort } from './catalog.models';
import { CatalogService } from './catalog.service';

const SORTS: SearchSort[] = ['newest', 'price_asc', 'price_desc', 'distance'];
const prefersReducedMotion = () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const PRICE_PATTERN = /^\d{1,4}([.,]\d{1,2})?$/;

@Component({
  selector: 'lendly-items-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ListingMapComponent],
  template: `
    <section class="items-page" aria-labelledby="page-title">
      <header class="items-header">
        <div>
          <p class="page-eyebrow">Katalog</p>
          <h1 id="page-title" class="page-title" tabindex="-1">Rzeczy do pożyczenia</h1>
        </div>
        <p class="result-count" aria-live="polite">{{ resultCountText() }}</p>
      </header>

      <form class="search-form" [formGroup]="form" (ngSubmit)="applyFilters()" role="search" aria-label="Szukaj ogłoszeń">
        <div class="search-row">
          <label class="visually-hidden" for="search-q">Czego szukasz?</label>
          <input id="search-q" type="search" formControlName="q" maxlength="100" placeholder="Np. wiertarka, namiot, projektor" autocomplete="off">
          <button class="button-primary" type="submit">Szukaj</button>
        </div>

        <div class="filter-row">
          <div class="form-field">
            <label for="filter-category">Kategoria</label>
            <select id="filter-category" formControlName="category" (change)="applyFilters()">
              <option value="">Wszystkie</option>
              @for (category of categories(); track category.key) {
                <option [value]="category.key">{{ category.displayName }}</option>
              }
            </select>
          </div>

          <fieldset class="form-field pricing-field">
            <legend>Cena</legend>
            <div class="segmented">
              <label><input type="radio" formControlName="pricing" (change)="applyFilters()" value=""> Wszystkie</label>
              <label><input type="radio" formControlName="pricing" (change)="applyFilters()" value="FREE"> Za darmo</label>
              <label><input type="radio" formControlName="pricing" (change)="applyFilters()" value="PAID"> Płatne</label>
            </div>
          </fieldset>

          <div class="form-field price-range">
            <span class="range-label" id="price-range-label">Cena za dzień (zł)</span>
            <div class="range-inputs" role="group" aria-labelledby="price-range-label">
              <label class="visually-hidden" for="filter-min">Od</label>
              <input id="filter-min" type="text" inputmode="decimal" formControlName="minPrice" placeholder="od">
              <label class="visually-hidden" for="filter-max">Do</label>
              <input id="filter-max" type="text" inputmode="decimal" formControlName="maxPrice" placeholder="do">
            </div>
          </div>

          <div class="form-field">
            <label for="filter-sort">Sortowanie</label>
            <select id="filter-sort" formControlName="sort" (change)="applyFilters()">
              <option value="newest">Najnowsze</option>
              <option value="price_asc">Cena: od najniższej</option>
              <option value="price_desc">Cena: od najwyższej</option>
              @if (hasCenter()) {
                <option value="distance">Najbliżej</option>
              }
            </select>
          </div>
        </div>

        @if (priceError()) {
          <p class="field-error" role="alert">{{ priceError() }}</p>
        }

        <div class="area-row">
          @if (hasCenter()) {
            <div class="form-field radius-field">
              <label for="filter-radius">Odległość od Twojej lokalizacji</label>
              <select id="filter-radius" formControlName="radiusKm" (change)="applyFilters()">
                <option value="2">do 2 km</option>
                <option value="5">do 5 km</option>
                <option value="10">do 10 km</option>
                <option value="25">do 25 km</option>
                <option value="50">do 50 km</option>
              </select>
            </div>
            <button class="button-quiet" type="button" (click)="clearArea()">Usuń filtr odległości</button>
          } @else if (hasBox()) {
            <p class="area-note">Wyniki z zaznaczonego obszaru mapy.</p>
            <button class="button-quiet" type="button" (click)="clearArea()">Pokaż wszystkie obszary</button>
          } @else {
            <button class="button-secondary" type="button" [disabled]="locating()" (click)="searchNearMe()">
              {{ locating() ? 'Ustalanie lokalizacji…' : 'Szukaj blisko mnie' }}
            </button>
          }
          @if (locationError()) {
            <p class="field-error" role="alert">{{ locationError() }}</p>
          }
          @if (hasActiveFilters()) {
            <button class="button-quiet clear-button" type="button" (click)="clearFilters()">Wyczyść filtry</button>
          }
        </div>
      </form>

      <div class="view-toggle" role="group" aria-label="Widok wyników">
        <button type="button" [attr.aria-pressed]="mobileView() === 'list'" (click)="mobileView.set('list')">Lista</button>
        <button type="button" [attr.aria-pressed]="mobileView() === 'map'" (click)="mobileView.set('map')">Mapa</button>
      </div>

      <div class="results-layout" [attr.data-mobile-view]="mobileView()">
        <div class="results-column" [attr.aria-busy]="loading()">
          @if (loading() && !page()) {
            <p class="state-message" role="status">Wyszukiwanie ogłoszeń…</p>
          } @else if (errorMessage()) {
            <div class="notice-error" role="alert">
              <p>{{ errorMessage() }}</p>
              <button class="button-secondary" type="button" (click)="reload()">Spróbuj ponownie</button>
            </div>
          } @else if (page(); as results) {
            @if (results.items.length === 0) {
              <div class="empty-state">
                <h2>Brak ogłoszeń dla tych filtrów</h2>
                <p>Spróbuj innego słowa, szerszej kategorii albo większego obszaru.</p>
                @if (hasActiveFilters()) {
                  <button class="button-secondary" type="button" (click)="clearFilters()">Wyczyść filtry</button>
                }
              </div>
            } @else {
              <ol class="result-list" [class.is-refreshing]="loading()">
                @for (item of results.items; track item.id) {
                  <li class="result-card" [id]="'result-' + item.id" [class.is-active]="activeId() === item.id">
                    <a class="result-link" [routerLink]="['/items', item.id]">
                      <span class="result-photo">
                        @if (item.coverImageUrl) {
                          <img [src]="item.coverImageUrl" alt="" loading="lazy" width="160" height="120">
                        }
                      </span>
                      <span class="result-body">
                        <span class="result-title">{{ item.title }}</span>
                        <span class="price-line">{{ price(item) }}</span>
                        <span class="result-meta">{{ meta(item) }}</span>
                      </span>
                    </a>
                  </li>
                }
              </ol>

              @if (results.totalPages > 1) {
                <nav class="pagination" aria-label="Strony wyników">
                  <button class="button-secondary" type="button" [disabled]="results.page === 0" (click)="goToPage(results.page - 1)">Poprzednia</button>
                  <span>Strona {{ results.page + 1 }} z {{ results.totalPages }}</span>
                  <button class="button-secondary" type="button" [disabled]="results.page + 1 >= results.totalPages" (click)="goToPage(results.page + 1)">Następna</button>
                </nav>
              }
            }
          }
        </div>

        <div class="map-column">
          <button class="skip-map" type="button" (click)="skipMap()">Pomiń mapę</button>
          <lendly-listing-map [pins]="pins()" [fitToPins]="!hasBox()" (moved)="mapMoved($event)" (pinActivated)="showResult($event)" />
          @if (pendingBounds()) {
            <button class="button-primary map-search-button" type="button" (click)="searchMapArea()">Szukaj w tym obszarze</button>
          }
          <div id="after-map" class="map-end" tabindex="-1"></div>
        </div>
      </div>
    </section>
  `,
  styleUrl: './items-page.component.scss'
})
export class ItemsPageComponent {
  private readonly catalog = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private searchSubscription: Subscription | null = null;
  private query: SearchQuery = {};

  readonly categories = signal<Category[]>([]);
  readonly page = signal<Page<ListingSummary> | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly priceError = signal('');
  readonly locationError = signal('');
  readonly locating = signal(false);
  readonly hasCenter = signal(false);
  readonly hasBox = signal(false);
  readonly hasActiveFilters = signal(false);
  readonly pendingBounds = signal<MapBounds | null>(null);
  readonly activeId = signal<string | null>(null);
  readonly mobileView = signal<'list' | 'map'>('list');
  readonly form = this.formBuilder.nonNullable.group({
    q: [''],
    category: [''],
    pricing: [''],
    minPrice: [''],
    maxPrice: [''],
    sort: ['newest'],
    radiusKm: ['5']
  });

  readonly pins = computed<MapPin[]>(() => (this.page()?.items ?? []).map((item) => ({
    id: item.id,
    lat: item.lat,
    lng: item.lng,
    text: item.pricingType === 'FREE' ? 'Za darmo' : `${Math.round(item.dailyPrice ?? 0)} zł`,
    label: `${item.title}, ${this.price(item)}`
  })));

  readonly resultCountText = computed(() => {
    const results = this.page();
    if (!results || this.errorMessage()) {
      return '';
    }
    const total = results.totalElements;
    if (total === 1) return 'Znaleziono 1 ogłoszenie';
    const lastDigit = total % 10;
    const lastTwo = total % 100;
    const word = lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14) ? 'ogłoszenia' : 'ogłoszeń';
    return `Znaleziono ${total} ${word}`;
  });

  constructor() {
    // The prerendered page ships the search form and headings; results load in the browser.
    if (!isPlatformBrowser(inject(PLATFORM_ID))) {
      return;
    }
    this.catalog.categories().pipe(takeUntilDestroyed()).subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.categories.set([])
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => this.load(params));
  }

  price(item: ListingSummary): string {
    return priceLabel(item.pricingType, item.dailyPrice);
  }

  meta(item: ListingSummary): string {
    return [conditionLabels[item.condition], item.areaLabel, distanceLabel(item.distanceKm)].filter(Boolean).join(' · ');
  }

  applyFilters(): void {
    const values = this.form.getRawValue();
    const minPrice = values.minPrice.trim().replace(',', '.');
    const maxPrice = values.maxPrice.trim().replace(',', '.');
    if ((minPrice && !PRICE_PATTERN.test(minPrice)) || (maxPrice && !PRICE_PATTERN.test(maxPrice))) {
      this.priceError.set('Podaj cenę jako liczbę, np. 15 albo 12,50.');
      return;
    }
    if (minPrice && maxPrice && Number(minPrice) > Number(maxPrice)) {
      this.priceError.set('Cena „od” nie może być wyższa niż cena „do”.');
      return;
    }
    this.priceError.set('');
    this.navigate({
      ...this.areaParams(),
      q: values.q.trim() || undefined,
      category: values.category || undefined,
      pricing: (values.pricing || undefined) as PricingType | undefined,
      minPrice: minPrice || undefined,
      maxPrice: maxPrice || undefined,
      sort: values.sort as SearchSort,
      radiusKm: this.query.lat !== undefined ? Number(values.radiusKm) : undefined
    });
  }

  clearFilters(): void {
    this.form.reset({ q: '', category: '', pricing: '', minPrice: '', maxPrice: '', sort: 'newest', radiusKm: '5' });
    this.priceError.set('');
    this.navigate({});
  }

  clearArea(): void {
    this.pendingBounds.set(null);
    this.navigate({ ...this.query, bbox: undefined, lat: undefined, lng: undefined, radiusKm: undefined,
      sort: this.query.sort === 'distance' ? 'newest' : this.query.sort, page: undefined });
  }

  searchNearMe(): void {
    this.locationError.set('');
    if (!('geolocation' in navigator)) {
      this.locationError.set('Ta przeglądarka nie udostępnia lokalizacji.');
      return;
    }
    this.locating.set(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.locating.set(false);
        // About 1 km precision: the position ends up in a shareable URL, and public pins are already displaced.
        this.navigate({ ...this.query, bbox: undefined, page: undefined, sort: 'distance',
          lat: Math.round(position.coords.latitude * 100) / 100,
          lng: Math.round(position.coords.longitude * 100) / 100,
          radiusKm: Number(this.form.controls.radiusKm.value) });
      },
      () => {
        this.locating.set(false);
        this.locationError.set('Nie udało się ustalić lokalizacji. Możesz przesunąć mapę i wyszukać w wybranym obszarze.');
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 }
    );
  }

  mapMoved(bounds: MapBounds): void {
    this.pendingBounds.set(bounds);
  }

  searchMapArea(): void {
    const bounds = this.pendingBounds();
    if (!bounds) return;
    const round = (value: number) => Math.round(value * 1e4) / 1e4;
    const clampLng = (value: number) => Math.max(-180, Math.min(180, round(value)));
    const clampLat = (value: number) => Math.max(-90, Math.min(90, round(value)));
    this.pendingBounds.set(null);
    this.navigate({ ...this.query, lat: undefined, lng: undefined, radiusKm: undefined, page: undefined,
      sort: this.query.sort === 'distance' ? 'newest' : this.query.sort,
      bbox: [clampLng(bounds.west), clampLat(bounds.south), clampLng(bounds.east), clampLat(bounds.north)].join(',') });
    this.mobileView.set('list');
  }

  showResult(id: string): void {
    this.activeId.set(id);
    this.mobileView.set('list');
    queueMicrotask(() => {
      const card = document.getElementById(`result-${id}`);
      card?.scrollIntoView({ block: 'nearest', behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
      card?.querySelector<HTMLAnchorElement>('a')?.focus({ preventScroll: true });
    });
  }

  goToPage(page: number): void {
    this.navigate({ ...this.query, page: page > 0 ? page : undefined });
    const title = document.getElementById('page-title');
    title?.scrollIntoView({ block: 'start' });
    // The pressed pagination button may be disabled after the page changes; keep focus at the top of the results.
    title?.focus({ preventScroll: true });
  }

  skipMap(): void {
    document.getElementById('after-map')?.focus();
  }

  reload(): void {
    this.load(this.route.snapshot.queryParamMap);
  }

  private areaParams(): SearchQuery {
    return { bbox: this.query.bbox, lat: this.query.lat, lng: this.query.lng };
  }

  private navigate(query: SearchQuery): void {
    const queryParams: Record<string, string | number | null> = {};
    for (const key of ['q', 'category', 'pricing', 'minPrice', 'maxPrice', 'bbox', 'lat', 'lng', 'radiusKm', 'sort', 'page'] as const) {
      const value = query[key];
      queryParams[key] = value === undefined || value === '' || (key === 'sort' && value === 'newest') ? null : value;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private load(params: ParamMap): void {
    const number = (key: string) => {
      const raw = params.get(key);
      const value = raw === null ? NaN : Number(raw);
      return Number.isFinite(value) ? value : undefined;
    };
    const lat = number('lat');
    const lng = number('lng');
    const center = lat !== undefined && lng !== undefined;
    const sort = params.get('sort') as SearchSort | null;
    const pricing = params.get('pricing');
    this.query = {
      q: params.get('q') ?? undefined,
      category: params.get('category') ?? undefined,
      pricing: pricing === 'FREE' || pricing === 'PAID' ? pricing : undefined,
      minPrice: params.get('minPrice') ?? undefined,
      maxPrice: params.get('maxPrice') ?? undefined,
      bbox: center ? undefined : params.get('bbox') ?? undefined,
      lat: center ? lat : undefined,
      lng: center ? lng : undefined,
      radiusKm: center ? number('radiusKm') ?? 5 : undefined,
      sort: sort && SORTS.includes(sort) && (sort !== 'distance' || center) ? sort : 'newest',
      page: Math.max(0, Math.floor(number('page') ?? 0)) || undefined
    };
    this.hasCenter.set(center);
    this.hasBox.set(!!this.query.bbox);
    this.hasActiveFilters.set(!!(this.query.q || this.query.category || this.query.pricing || this.query.minPrice
      || this.query.maxPrice || this.query.bbox || center));
    this.form.setValue({
      q: this.query.q ?? '',
      category: this.query.category ?? '',
      pricing: this.query.pricing ?? '',
      minPrice: (this.query.minPrice ?? '').replace('.', ','),
      maxPrice: (this.query.maxPrice ?? '').replace('.', ','),
      sort: this.query.sort ?? 'newest',
      radiusKm: String(this.query.radiusKm ?? 5)
    });

    this.searchSubscription?.unsubscribe();
    this.loading.set(true);
    this.errorMessage.set('');
    this.searchSubscription = this.catalog.search({ ...this.query, size: 20 }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(catalogErrorMessage(error));
        this.loading.set(false);
      }
    });
  }
}
