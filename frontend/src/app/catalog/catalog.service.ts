import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Category, ListingDetail, ListingSummary, Page, SearchQuery } from './catalog.models';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private categoryRequest: Observable<Category[]> | null = null;

  categories(): Observable<Category[]> {
    if (!this.categoryRequest) {
      this.categoryRequest = this.http.get<Category[]>('/api/categories').pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.categoryRequest;
  }

  search(query: SearchQuery): Observable<Page<ListingSummary>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<ListingSummary>>('/api/items', { params });
  }

  detail(id: string): Observable<ListingDetail> {
    return this.http.get<ListingDetail>(`/api/items/${encodeURIComponent(id)}`);
  }
}
