import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { ListingSummary } from '../catalog/catalog.models';

export interface ScanResult {
  itemRecognized: boolean;
  title: string | null;
  categoryKey: string | null;
  description: string | null;
  keywords: string[];
  attributes: { name: string; value: string }[];
  mustProvide: string[];
  note: string | null;
}

export interface MissionGroup {
  name: string;
  essential: boolean;
  why: string | null;
  listings: ListingSummary[];
}

export interface MissionResult {
  aiUsed: boolean;
  summary: string | null;
  safetyNote: string | null;
  groups: MissionGroup[];
  fallbackListings: ListingSummary[];
}

export interface MissionRequest {
  task: string;
  startDate?: string;
  endDate?: string;
  lat?: number;
  lng?: number;
  radiusKm?: number;
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private statusRequest: Observable<boolean> | null = null;

  available(): Observable<boolean> {
    if (!this.statusRequest) {
      this.statusRequest = this.http.get<{ available: boolean }>('/api/ai/status').pipe(
        map((status) => status.available),
        catchError(() => of(false)),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.statusRequest;
  }

  scan(image: Blob): Observable<ScanResult> {
    const body = new FormData();
    body.append('file', image, 'photo.jpg');
    return this.http.post<ScanResult>('/api/ai/scan', body);
  }

  mission(request: MissionRequest): Observable<MissionResult> {
    return this.http.post<MissionResult>('/api/ai/missions', request);
  }
}
