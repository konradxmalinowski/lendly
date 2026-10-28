import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { distanceLabel, priceLabel } from '../catalog/catalog-format';
import { ListingSummary } from '../catalog/catalog.models';
import { addDays, warsawToday } from '../reservations/reservation-format';
import { AiService, MissionRequest, MissionResult } from './ai.service';

const EXAMPLES = [
  'Chcę zamontować półkę na betonowej ścianie, ale nie mam narzędzi.',
  'Chcę wyczyścić tapicerkę w samochodzie.',
  'Jadę pierwszy raz pod namiot na weekend.'
];

@Component({
  selector: 'lendly-missions-page',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './missions-page.component.html',
  styleUrl: './missions-page.component.scss'
})
export class MissionsPageComponent {
  private readonly ai = inject(AiService);
  readonly examples = EXAMPLES;
  readonly today = warsawToday();
  readonly maxStart = addDays(this.today, 180);
  task = inject(ActivatedRoute).snapshot.queryParamMap.get('task')?.slice(0, 500) ?? '';
  startDate = '';
  endDate = '';
  readonly useLocation = signal(false);
  readonly location = signal<{ lat: number; lng: number } | null>(null);
  readonly locationError = signal('');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly result = signal<MissionResult | null>(null);

  useExample(example: string): void {
    this.task = example;
  }

  toggleLocation(enabled: boolean): void {
    this.useLocation.set(enabled);
    this.locationError.set('');
    if (!enabled) {
      this.location.set(null);
      return;
    }
    if (!('geolocation' in navigator)) {
      this.locationError.set('Ta przeglądarka nie udostępnia lokalizacji.');
      this.useLocation.set(false);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => this.location.set({
        lat: Math.round(position.coords.latitude * 100) / 100,
        lng: Math.round(position.coords.longitude * 100) / 100
      }),
      () => {
        this.useLocation.set(false);
        this.locationError.set('Nie udało się ustalić lokalizacji. Szukamy bez filtra odległości.');
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 }
    );
  }

  submit(): void {
    const task = this.task.trim();
    this.error.set('');
    if (task.length < 10) {
      this.error.set('Opisz zadanie w co najmniej 10 znakach.');
      return;
    }
    if ((this.startDate && !this.endDate) || (!this.startDate && this.endDate) || (this.endDate && this.endDate < this.startDate)) {
      this.error.set('Podaj obie daty, koniec nie wcześniej niż początek, albo zostaw je puste.');
      return;
    }
    const request: MissionRequest = { task };
    if (this.startDate) {
      request.startDate = this.startDate;
      request.endDate = this.endDate;
    }
    const point = this.location();
    if (point) {
      request.lat = point.lat;
      request.lng = point.lng;
      request.radiusKm = 10;
    }
    this.loading.set(true);
    this.result.set(null);
    this.ai.mission(request).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
        setTimeout(() => document.getElementById('mission-results')?.focus());
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(error instanceof HttpErrorResponse && error.status === 429
          ? 'Wykorzystano limit misji na tę godzinę. Możesz skorzystać ze zwykłej wyszukiwarki.'
          : error instanceof HttpErrorResponse && error.status === 400
            ? 'Sprawdź opis zadania i daty.'
            : 'Nie udało się wyszukać. Spróbuj ponownie albo skorzystaj ze zwykłej wyszukiwarki.');
      }
    });
  }

  price(item: ListingSummary): string {
    return priceLabel(item.pricingType, item.dailyPrice);
  }

  distance(item: ListingSummary): string | null {
    return distanceLabel(item.distanceKm);
  }

  foundCount(result: MissionResult): number {
    return result.groups.reduce((sum, group) => sum + group.listings.length, 0) + result.fallbackListings.length;
  }
}
