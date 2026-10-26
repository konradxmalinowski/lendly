import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../catalog/catalog.models';
import { AvailabilityBlock, DateRange, ReservationAction, ReservationDetail, ReservationSummary } from './reservation.models';

const ACTION_PATHS: Record<ReservationAction, string> = {
  ACCEPT: 'accept',
  REJECT: 'reject',
  CANCEL: 'cancel',
  HANDOVER: 'handover',
  RETURN: 'return'
};

@Injectable({ providedIn: 'root' })
export class ReservationsService {
  private readonly http = inject(HttpClient);

  availability(listingId: string): Observable<DateRange[]> {
    return this.http.get<DateRange[]>(`/api/items/${encodeURIComponent(listingId)}/availability`);
  }

  request(listingId: string, startDate: string, endDate: string, note: string | null): Observable<ReservationDetail> {
    return this.http.post<ReservationDetail>('/api/reservations', { listingId, startDate, endDate, note });
  }

  list(role: 'borrower' | 'owner', page = 0): Observable<Page<ReservationSummary>> {
    return this.http.get<Page<ReservationSummary>>('/api/reservations', { params: { role, page, size: 20 } });
  }

  detail(id: string): Observable<ReservationDetail> {
    return this.http.get<ReservationDetail>(`/api/reservations/${encodeURIComponent(id)}`);
  }

  act(id: string, action: ReservationAction, reason: string | null): Observable<ReservationDetail> {
    const body = action === 'REJECT' || action === 'CANCEL' ? { reason } : null;
    return this.http.post<ReservationDetail>(`/api/reservations/${encodeURIComponent(id)}/${ACTION_PATHS[action]}`, body);
  }

  blocks(listingId: string): Observable<AvailabilityBlock[]> {
    return this.http.get<AvailabilityBlock[]>(`/api/me/items/${encodeURIComponent(listingId)}/blocks`);
  }

  addBlock(listingId: string, startDate: string, endDate: string): Observable<AvailabilityBlock> {
    return this.http.post<AvailabilityBlock>(`/api/me/items/${encodeURIComponent(listingId)}/blocks`, { startDate, endDate });
  }

  deleteBlock(listingId: string, blockId: string): Observable<void> {
    return this.http.delete<void>(`/api/me/items/${encodeURIComponent(listingId)}/blocks/${encodeURIComponent(blockId)}`);
  }
}
