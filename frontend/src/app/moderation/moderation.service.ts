import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../catalog/catalog.models';
import { ReportDetail, ReportReason, ReportSummary, ReportTargetType } from './moderation.models';

@Injectable({ providedIn: 'root' })
export class ModerationService {
  private readonly http = inject(HttpClient);

  report(targetType: ReportTargetType, targetId: string, reason: ReportReason, details: string | null): Observable<{ id: string }> {
    return this.http.post<{ id: string }>('/api/reports', { targetType, targetId, reason, details });
  }

  queue(state: 'OPEN' | 'CLOSED', targetType: ReportTargetType | null, page = 0): Observable<Page<ReportSummary>> {
    const params: Record<string, string | number> = { state, page, size: 20 };
    if (targetType) params['targetType'] = targetType;
    return this.http.get<Page<ReportSummary>>('/api/moderation/reports', { params });
  }

  detail(id: string): Observable<ReportDetail> {
    return this.http.get<ReportDetail>(`/api/moderation/reports/${encodeURIComponent(id)}`);
  }

  close(id: string, outcome: 'RESOLVED' | 'DISMISSED', note: string | null): Observable<ReportDetail> {
    return this.http.post<ReportDetail>(`/api/moderation/reports/${encodeURIComponent(id)}/resolve`, { outcome, note });
  }

  listingAction(listingId: string, action: 'hide' | 'restore', reason: string | null, reportId: string): Observable<void> {
    return this.http.post<void>(`/api/moderation/listings/${encodeURIComponent(listingId)}/${action}`, { reason, reportId });
  }

  userAction(userId: string, action: 'suspend' | 'reinstate', reason: string | null, reportId: string): Observable<void> {
    return this.http.post<void>(`/api/moderation/users/${encodeURIComponent(userId)}/${action}`, { reason, reportId });
  }
}
