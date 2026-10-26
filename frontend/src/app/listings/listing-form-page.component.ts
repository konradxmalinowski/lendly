import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Location } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { setPrivatePageMetadata } from '../auth/private-page-metadata';
import { conditionLabels, statusLabels } from '../catalog/catalog-format';
import { Category, ItemCondition, ListingImage, ListingRequest, MapPoint, OwnerListing, PricingType } from '../catalog/catalog.models';
import { CatalogService } from '../catalog/catalog.service';
import { AiScanPanelComponent, ScanApply } from '../ai/ai-scan-panel.component';
import { AvailabilityBlocksComponent } from '../reservations/availability-blocks.component';
import { LocationPickerComponent } from '../shared/map/location-picker.component';
import { ImagePreparationError, prepareImage } from './image-resize';
import { ListingsService } from './listings.service';

const MAX_IMAGES = 8;

interface PendingImage {
  key: number;
  name: string;
  previewUrl: string | null;
  blob: Blob | null;
  status: 'preparing' | 'queued' | 'uploading' | 'failed';
  error: string;
}

function trimmedLength(min: number, max: number) {
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const length = [...(control.value ?? '').trim()].length;
    return length < min || length > max ? { length: true } : null;
  };
}

function priceRule(control: AbstractControl<string>): ValidationErrors | null {
  const value = (control.value ?? '').trim().replace(',', '.');
  if (!/^\d{1,4}(\.\d{1,2})?$/.test(value) || Number(value) < 0.01) {
    return { price: true };
  }
  return null;
}

@Component({
  selector: 'lendly-listing-form-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LocationPickerComponent, AvailabilityBlocksComponent, AiScanPanelComponent],
  templateUrl: './listing-form-page.component.html',
  styleUrl: './listing-form-page.component.scss'
})
export class ListingFormPageComponent {
  private readonly pageMetadata = setPrivatePageMetadata('Ogłoszenie | Lendly', 'Dodaj lub edytuj swoje ogłoszenie w Lendly.');
  private readonly listings = inject(ListingsService);
  private readonly location = inject(Location);
  private readonly formBuilder = inject(FormBuilder);
  private pendingKey = 0;

  readonly conditions = Object.entries(conditionLabels) as [ItemCondition, string][];
  readonly categories = signal<Category[]>([]);
  readonly listing = signal<OwnerListing | null>(null);
  readonly editId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal('');
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly missing = signal<string[]>([]);
  readonly point = signal<MapPoint | null>(null);
  readonly pointTouched = signal(false);
  readonly images = signal<ListingImage[]>([]);
  readonly pending = signal<PendingImage[]>([]);
  readonly removingImage = signal<string | null>(null);
  readonly imageError = signal('');
  readonly imageCount = computed(() => this.images().length + this.pending().filter((item) => item.status !== 'failed').length);
  readonly status = computed(() => this.listing()?.status ?? 'DRAFT');
  readonly statusLabel = computed(() => statusLabels[this.status()]);
  readonly isActive = computed(() => this.status() === 'ACTIVE');
  readonly scanImage = computed<Blob | string | null>(() =>
    this.images()[0]?.url ?? this.pending().find((item) => item.blob)?.blob ?? null);

  readonly form = this.formBuilder.nonNullable.group({
    title: ['', [Validators.required, trimmedLength(3, 100)]],
    description: ['', [Validators.required, trimmedLength(10, 2000)]],
    categoryKey: ['', [Validators.required]],
    condition: ['GOOD' as ItemCondition, [Validators.required]],
    pricingType: ['FREE' as PricingType, [Validators.required]],
    dailyPrice: ['']
  });

  constructor() {
    inject(CatalogService).categories().pipe(takeUntilDestroyed()).subscribe({
      next: (categories) => this.categories.set(categories),
      error: () => this.loadError.set('Nie udało się wczytać kategorii. Odśwież stronę.')
    });
    this.form.controls.pricingType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => this.updatePriceValidator(type));

    const id = inject(ActivatedRoute).snapshot.paramMap.get('id');
    if (id) {
      this.editId.set(id);
      this.loading.set(true);
      this.listings.get(id).subscribe({
        next: (listing) => {
          this.applyListing(listing);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loadError.set(error instanceof HttpErrorResponse && error.status === 404
            ? 'Nie znaleziono tego ogłoszenia na Twoim koncie.'
            : 'Nie udało się wczytać ogłoszenia. Spróbuj ponownie.');
          this.loading.set(false);
        }
      });
    }
  }

  showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  applySuggestion(suggestion: ScanApply): void {
    const control = this.form.controls[suggestion.field];
    control.setValue(suggestion.value);
    control.markAsDirty();
    control.markAsTouched();
  }

  setPoint(point: MapPoint): void {
    this.point.set(point);
    this.pointTouched.set(true);
    this.missing.update((items) => items.filter((item) => item !== 'handoverLat'));
  }

  async selectFiles(input: HTMLInputElement): Promise<void> {
    this.imageError.set('');
    const files = Array.from(input.files ?? []);
    input.value = '';
    const free = MAX_IMAGES - this.imageCount();
    if (files.length > free) {
      this.imageError.set(free === 0 ? 'Ogłoszenie ma już 8 zdjęć.' : `Możesz dodać jeszcze ${free} ${free === 1 ? 'zdjęcie' : 'zdjęcia'}.`);
    }
    for (const file of files.slice(0, Math.max(0, free))) {
      const item: PendingImage = { key: ++this.pendingKey, name: file.name, previewUrl: null, blob: null, status: 'preparing', error: '' };
      this.pending.update((items) => [...items, item]);
      try {
        const blob = await prepareImage(file);
        this.updatePending(item.key, { blob, previewUrl: URL.createObjectURL(blob), status: 'queued' });
      } catch (error) {
        this.updatePending(item.key, { status: 'failed',
          error: error instanceof ImagePreparationError ? error.message : 'Nie udało się przygotować zdjęcia.' });
      }
    }
    this.missing.update((items) => items.filter((item) => item !== 'images'));
    const id = this.editId();
    if (id) {
      await this.uploadPending(id);
    }
  }

  dismissPending(key: number): void {
    const item = this.pending().find((entry) => entry.key === key);
    if (item?.previewUrl) URL.revokeObjectURL(item.previewUrl);
    this.pending.update((items) => items.filter((entry) => entry.key !== key));
  }

  removeImage(image: ListingImage): void {
    const id = this.editId();
    if (!id) return;
    this.imageError.set('');
    this.removingImage.set(image.id);
    this.listings.deleteImage(id, image.id).subscribe({
      next: () => {
        this.images.update((items) => items.filter((item) => item.id !== image.id));
        this.removingImage.set(null);
      },
      error: (error: unknown) => {
        this.removingImage.set(null);
        this.imageError.set(error instanceof HttpErrorResponse && error.status === 422
          ? 'Opublikowane ogłoszenie musi mieć co najmniej jedno zdjęcie. Dodaj nowe, zanim usuniesz ostatnie.'
          : 'Nie udało się usunąć zdjęcia. Spróbuj ponownie.');
      }
    });
  }

  async save(publish: boolean): Promise<void> {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.missing.set([]);
    this.pointTouched.set(true);
    if (this.form.invalid || (this.isActive() && !this.point())) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Popraw zaznaczone pola.');
      this.focusFirstInvalid();
      return;
    }

    this.saving.set(true);
    try {
      const request = this.request();
      const id = this.editId();
      let listing = id
        ? await firstValueFrom(this.listings.update(id, request))
        : await firstValueFrom(this.listings.create(request));
      if (!id) {
        this.editId.set(listing.id);
        // Swap the URL without re-creating the component, so queued photo uploads keep running.
        this.location.replaceState(`/listings/${listing.id}/edit`);
      }
      const uploadsOk = await this.uploadPending(listing.id);
      if (publish && listing.status !== 'ACTIVE') {
        if (!uploadsOk) {
          this.applyListing(listing, false);
          this.errorMessage.set('Szkic zapisano, ale nie wszystkie zdjęcia zostały przesłane. Usuń błędne pliki i spróbuj opublikować ponownie.');
          return;
        }
        listing = await firstValueFrom(this.listings.publish(listing.id));
        this.applyListing(listing, false);
        this.successMessage.set('Ogłoszenie zostało opublikowane.');
      } else {
        this.applyListing(listing, false);
        this.successMessage.set(listing.status === 'ACTIVE' ? 'Zmiany zostały zapisane.' : 'Szkic został zapisany.');
      }
    } catch (error) {
      this.handleSaveError(error);
    } finally {
      this.saving.set(false);
    }
  }

  private async uploadPending(id: string): Promise<boolean> {
    let allOk = true;
    for (const item of this.pending()) {
      if (item.status !== 'queued' || !item.blob) {
        allOk = allOk && item.status !== 'failed';
        continue;
      }
      this.updatePending(item.key, { status: 'uploading' });
      try {
        const image = await firstValueFrom(this.listings.uploadImage(id, item.blob));
        this.images.update((items) => [...items, image]);
        this.dismissPending(item.key);
      } catch (error) {
        allOk = false;
        this.updatePending(item.key, { status: 'failed', error: this.uploadMessage(error) });
      }
    }
    return allOk;
  }

  private uploadMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 413) return 'Zdjęcie jest zbyt duże.';
      if (error.status === 409) return 'Ogłoszenie ma już 8 zdjęć.';
      if (error.status === 422) return 'Tego pliku nie da się użyć jako zdjęcia.';
      if (error.status === 429) return 'Przesłano zbyt wiele zdjęć. Spróbuj za jakiś czas.';
      if (error.status === 503) return 'Serwer jest teraz zajęty. Spróbuj przesłać zdjęcie ponownie.';
    }
    return 'Nie udało się przesłać zdjęcia.';
  }

  private handleSaveError(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set('Nie udało się zapisać ogłoszenia. Spróbuj ponownie.');
      return;
    }
    const fields = Object.keys((error.error as { fieldErrors?: Record<string, string[]> } | null)?.fieldErrors ?? {});
    if (error.status === 422) {
      this.missing.set(fields);
      this.errorMessage.set('Szkic zapisano, ale do publikacji brakuje kilku informacji.');
    } else if (error.status === 400 && fields.length > 0) {
      fields.forEach((field) => this.form.get(field)?.setErrors({ server: true }));
      this.form.markAllAsTouched();
      this.errorMessage.set('Popraw zaznaczone pola.');
    } else if (error.status === 429) {
      this.errorMessage.set('Dodano dziś zbyt wiele ogłoszeń. Spróbuj ponownie jutro.');
    } else if (error.status === 401) {
      this.errorMessage.set('Sesja wygasła. Zaloguj się ponownie w nowej karcie i zapisz ogłoszenie jeszcze raz, żeby nie stracić wpisanych danych.');
    } else if (error.status === 404) {
      this.errorMessage.set('To ogłoszenie nie jest już dostępne na Twoim koncie.');
    } else if (error.status === 0) {
      this.errorMessage.set('Brak połączenia z serwerem. Sprawdź internet i spróbuj ponownie.');
    } else {
      this.errorMessage.set('Nie udało się zapisać ogłoszenia. Spróbuj ponownie.');
    }
  }

  private request(): ListingRequest {
    const values = this.form.getRawValue();
    const point = this.point();
    return {
      title: values.title.trim(),
      description: values.description.trim(),
      categoryKey: values.categoryKey,
      condition: values.condition,
      pricingType: values.pricingType,
      dailyPrice: values.pricingType === 'PAID' ? values.dailyPrice.trim().replace(',', '.') : null,
      handoverLat: point?.lat ?? null,
      handoverLng: point?.lng ?? null
    };
  }

  private applyListing(listing: OwnerListing, resetForm = true): void {
    this.listing.set(listing);
    this.images.set(listing.images);
    if (resetForm) {
      this.form.setValue({
        title: listing.title,
        description: listing.description,
        categoryKey: listing.categoryKey,
        condition: listing.condition,
        pricingType: listing.pricingType,
        dailyPrice: listing.dailyPrice === null ? '' : listing.dailyPrice.toFixed(2).replace('.', ',')
      });
      this.point.set(listing.handoverLat !== null && listing.handoverLng !== null
        ? { lat: listing.handoverLat, lng: listing.handoverLng } : null);
    }
    this.form.markAsPristine();
  }

  private updatePriceValidator(type: PricingType): void {
    const control = this.form.controls.dailyPrice;
    control.setValidators(type === 'PAID' ? [Validators.required, priceRule] : []);
    control.updateValueAndValidity({ emitEvent: false });
  }

  private updatePending(key: number, patch: Partial<PendingImage>): void {
    this.pending.update((items) => items.map((item) => item.key === key ? { ...item, ...patch } : item));
  }

  private focusFirstInvalid(): void {
    setTimeout(() => document.querySelector<HTMLElement>('.listing-form [aria-invalid="true"]')?.focus());
  }
}
