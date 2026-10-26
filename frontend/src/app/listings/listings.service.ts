import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ListingImage, ListingRequest, OwnerListing, Page } from '../catalog/catalog.models';

@Injectable({ providedIn: 'root' })
export class ListingsService {
  private readonly http = inject(HttpClient);

  mine(page = 0): Observable<Page<OwnerListing>> {
    return this.http.get<Page<OwnerListing>>('/api/me/items', { params: { page, size: 20 } });
  }

  get(id: string): Observable<OwnerListing> {
    return this.http.get<OwnerListing>(`/api/me/items/${encodeURIComponent(id)}`);
  }

  create(request: ListingRequest): Observable<OwnerListing> {
    return this.http.post<OwnerListing>('/api/items', request);
  }

  update(id: string, request: ListingRequest): Observable<OwnerListing> {
    return this.http.put<OwnerListing>(`/api/items/${encodeURIComponent(id)}`, request);
  }

  publish(id: string): Observable<OwnerListing> {
    return this.http.post<OwnerListing>(`/api/items/${encodeURIComponent(id)}/publish`, null);
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`/api/items/${encodeURIComponent(id)}`);
  }

  uploadImage(id: string, file: Blob): Observable<ListingImage> {
    const body = new FormData();
    body.append('file', file, 'photo.jpg');
    return this.http.post<ListingImage>(`/api/items/${encodeURIComponent(id)}/images`, body);
  }

  deleteImage(id: string, imageId: string): Observable<void> {
    return this.http.delete<void>(`/api/items/${encodeURIComponent(id)}/images/${encodeURIComponent(imageId)}`);
  }
}
