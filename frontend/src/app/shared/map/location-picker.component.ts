import { Component, DestroyRef, ElementRef, ViewEncapsulation, afterNextRender, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type { Map as MapLibreMap, Marker } from 'maplibre-gl';
import { MapPoint } from '../../catalog/catalog.models';
import { ThemeService } from '../../theme.service';
import { DEFAULT_CENTER, createMap, mapStyleUrl } from './map-loader';

@Component({
  selector: 'lendly-location-picker',
  standalone: true,
  template: `
    <div class="map-frame map-frame-picker" [class.map-frame-failed]="state() === 'failed'">
      <div #canvas class="map-canvas" role="region"
        aria-label="Mapa wyboru punktu odbioru. Kliknij mapę albo przesuń ją strzałkami i użyj przycisku poniżej."></div>
      <span class="map-crosshair" aria-hidden="true"></span>
      @if (state() === 'loading') {
        <p class="map-status" role="status">Ładowanie mapy…</p>
      } @else if (state() === 'failed') {
        <p class="map-status" role="status">Mapa jest teraz niedostępna. Ustaw punkt na podstawie swojej lokalizacji.</p>
      }
    </div>
    <div class="picker-actions">
      @if (state() === 'ready') {
        <button class="button-secondary" type="button" (click)="useMapCenter()">Ustaw punkt w środku mapy</button>
      }
      <button class="button-secondary" type="button" [disabled]="locating()" (click)="useMyLocation()">
        {{ locating() ? 'Ustalanie lokalizacji…' : 'Użyj mojej lokalizacji' }}
      </button>
    </div>
    @if (geolocationError()) {
      <p class="field-error" role="alert">{{ geolocationError() }}</p>
    }
  `,
  styleUrl: './map.component.scss',
  encapsulation: ViewEncapsulation.None
})
export class LocationPickerComponent {
  readonly point = input<MapPoint | null>(null);
  readonly pointChange = output<MapPoint>();

  readonly state = signal<'loading' | 'ready' | 'failed'>('loading');
  readonly locating = signal(false);
  readonly geolocationError = signal('');
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private readonly theme = inject(ThemeService);
  private map: MapLibreMap | null = null;
  private marker: Marker | null = null;
  private maplibre: typeof import('maplibre-gl') | null = null;
  private appliedDark: boolean | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => void this.initialize());
    destroyRef.onDestroy(() => this.map?.remove());

    effect(() => {
      const point = this.point();
      if (this.state() === 'ready') {
        this.placeMarker(point);
      }
    });
    effect(() => {
      const dark = this.theme.isDark();
      if (this.state() === 'ready' && this.map && this.appliedDark !== dark) {
        this.appliedDark = dark;
        this.map.setStyle(mapStyleUrl(dark));
      }
    });
  }

  useMapCenter(): void {
    const center = this.map?.getCenter();
    if (center) {
      this.select({ lat: center.lat, lng: center.lng });
    }
  }

  useMyLocation(): void {
    this.geolocationError.set('');
    if (!('geolocation' in navigator)) {
      this.geolocationError.set('Ta przeglądarka nie udostępnia lokalizacji.');
      return;
    }
    this.locating.set(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        this.locating.set(false);
        const point = { lat: position.coords.latitude, lng: position.coords.longitude };
        this.map?.jumpTo({ center: [point.lng, point.lat], zoom: 15 });
        this.select(point);
      },
      () => {
        this.locating.set(false);
        this.geolocationError.set('Nie udało się ustalić lokalizacji. Sprawdź uprawnienia przeglądarki albo wskaż punkt na mapie.');
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 }
    );
  }

  private async initialize(): Promise<void> {
    const point = this.point();
    try {
      const created = await createMap(this.canvas().nativeElement, point ? [point.lng, point.lat] : DEFAULT_CENTER,
        point ? 15 : 11, this.theme.isDark());
      this.map = created.map;
      this.maplibre = created.maplibre;
      this.appliedDark = this.theme.isDark();
      this.map.on('click', (event) => this.select({ lat: event.lngLat.lat, lng: event.lngLat.lng }));
      this.state.set('ready');
    } catch {
      this.state.set('failed');
    }
  }

  private select(point: MapPoint): void {
    const rounded = { lat: Math.round(point.lat * 1e6) / 1e6, lng: Math.round(point.lng * 1e6) / 1e6 };
    this.placeMarker(rounded);
    this.pointChange.emit(rounded);
  }

  private placeMarker(point: MapPoint | null): void {
    if (!this.map || !this.maplibre) {
      return;
    }
    if (!point) {
      this.marker?.remove();
      this.marker = null;
      return;
    }
    if (!this.marker) {
      const element = document.createElement('span');
      element.className = 'map-handover-pin';
      element.setAttribute('aria-hidden', 'true');
      this.marker = new this.maplibre.Marker({ element, anchor: 'bottom', draggable: true }).setLngLat([point.lng, point.lat]).addTo(this.map);
      this.marker.on('dragend', () => {
        const position = this.marker?.getLngLat();
        if (position) {
          this.select({ lat: position.lat, lng: position.lng });
        }
      });
    } else {
      this.marker.setLngLat([point.lng, point.lat]);
    }
  }
}
