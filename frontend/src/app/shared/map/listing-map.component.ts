import { Component, DestroyRef, ViewEncapsulation, ElementRef, afterNextRender, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type { Feature, Polygon } from 'geojson';
import type { Map as MapLibreMap, Marker } from 'maplibre-gl';
import { MapBounds, MapPoint } from '../../catalog/catalog.models';
import { ThemeService } from '../../theme.service';
import { DEFAULT_CENTER, createMap, mapStyleUrl } from './map-loader';

export interface MapPin {
  id: string;
  lat: number;
  lng: number;
  text: string;
  label: string;
}

const AREA_SOURCE = 'lendly-area';
const AREA_RADIUS_METERS = 600;

@Component({
  selector: 'lendly-listing-map',
  standalone: true,
  template: `
    <div class="map-frame" [class.map-frame-failed]="state() === 'failed'">
      <div #canvas class="map-canvas" role="region" [attr.aria-label]="regionLabel()"></div>
      @if (state() === 'loading') {
        <p class="map-status" role="status">Ładowanie mapy…</p>
      } @else if (state() === 'failed') {
        <p class="map-status" role="status">{{ failureText() }}</p>
      }
    </div>
  `,
  styleUrl: './map.component.scss',
  encapsulation: ViewEncapsulation.None
})
export class ListingMapComponent {
  readonly pins = input<MapPin[]>([]);
  readonly area = input<MapPoint | null>(null);
  readonly fitToPins = input(true);
  readonly regionLabel = input('Mapa ogłoszeń');
  readonly failureText = input('Mapa jest teraz niedostępna. Lista wyników działa bez niej.');
  readonly moved = output<MapBounds>();
  readonly pinActivated = output<string>();

  readonly state = signal<'loading' | 'ready' | 'failed'>('loading');
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private readonly theme = inject(ThemeService);
  private map: MapLibreMap | null = null;
  private maplibre: typeof import('maplibre-gl') | null = null;
  private markers: Marker[] = [];
  private appliedDark: boolean | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => void this.initialize());
    destroyRef.onDestroy(() => this.map?.remove());

    effect(() => {
      const pins = this.pins();
      if (this.state() === 'ready') {
        this.renderPins(pins);
      }
    });
    effect(() => {
      const area = this.area();
      if (this.state() === 'ready') {
        this.renderArea(area);
      }
    });
    effect(() => {
      const dark = this.theme.isDark();
      if (this.state() === 'ready' && this.map && this.appliedDark !== dark) {
        this.appliedDark = dark;
        this.map.setStyle(mapStyleUrl(dark));
        this.map.once('style.load', () => this.renderArea(this.area()));
      }
    });
  }

  private async initialize(): Promise<void> {
    const area = this.area();
    const center: [number, number] = area ? [area.lng, area.lat] : DEFAULT_CENTER;
    try {
      const created = await createMap(this.canvas().nativeElement, center, area ? 13.5 : 11, this.theme.isDark());
      this.map = created.map;
      this.maplibre = created.maplibre;
      this.appliedDark = this.theme.isDark();
      this.map.on('moveend', (event) => {
        // Only user gestures report movement, so fitting the view to results does not trigger a new search.
        if (!('originalEvent' in event) || !event.originalEvent || !this.map) {
          return;
        }
        const bounds = this.map.getBounds();
        this.moved.emit({ west: bounds.getWest(), south: bounds.getSouth(), east: bounds.getEast(), north: bounds.getNorth() });
      });
      this.state.set('ready');
    } catch {
      this.state.set('failed');
    }
  }

  private renderPins(pins: MapPin[]): void {
    if (!this.map || !this.maplibre) {
      return;
    }
    this.markers.forEach((marker) => marker.remove());
    this.markers = pins.map((pin) => {
      const element = document.createElement('button');
      element.type = 'button';
      element.className = 'map-pin';
      element.textContent = pin.text;
      element.setAttribute('aria-label', pin.label);
      element.addEventListener('click', (event) => {
        event.stopPropagation();
        this.pinActivated.emit(pin.id);
      });
      return new this.maplibre!.Marker({ element, anchor: 'bottom' }).setLngLat([pin.lng, pin.lat]).addTo(this.map!);
    });
    if (pins.length > 0 && !this.area() && this.fitToPins()) {
      const bounds = new this.maplibre.LngLatBounds();
      pins.forEach((pin) => bounds.extend([pin.lng, pin.lat]));
      this.map.fitBounds(bounds, { padding: 56, maxZoom: 14, duration: 0 });
    }
  }

  private renderArea(area: MapPoint | null): void {
    const map = this.map;
    if (!map || !map.isStyleLoaded()) {
      return;
    }
    if (map.getLayer(`${AREA_SOURCE}-line`)) map.removeLayer(`${AREA_SOURCE}-line`);
    if (map.getLayer(`${AREA_SOURCE}-fill`)) map.removeLayer(`${AREA_SOURCE}-fill`);
    if (map.getSource(AREA_SOURCE)) map.removeSource(AREA_SOURCE);
    if (!area) {
      return;
    }
    const color = getComputedStyle(document.documentElement).getPropertyValue('--primary').trim() || '#244b3c';
    map.addSource(AREA_SOURCE, { type: 'geojson', data: circle(area, AREA_RADIUS_METERS) });
    map.addLayer({ id: `${AREA_SOURCE}-fill`, type: 'fill', source: AREA_SOURCE, paint: { 'fill-color': color, 'fill-opacity': 0.16 } });
    map.addLayer({ id: `${AREA_SOURCE}-line`, type: 'line', source: AREA_SOURCE, paint: { 'line-color': color, 'line-width': 2 } });
    map.jumpTo({ center: [area.lng, area.lat] });
  }
}

function circle(center: MapPoint, radiusMeters: number): Feature<Polygon> {
  const coordinates: [number, number][] = [];
  const latRadius = radiusMeters / 111_320;
  const lngRadius = radiusMeters / (111_320 * Math.cos((center.lat * Math.PI) / 180));
  for (let step = 0; step <= 64; step++) {
    const angle = (step / 64) * 2 * Math.PI;
    coordinates.push([center.lng + lngRadius * Math.cos(angle), center.lat + latRadius * Math.sin(angle)]);
  }
  return { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates: [coordinates] } };
}
