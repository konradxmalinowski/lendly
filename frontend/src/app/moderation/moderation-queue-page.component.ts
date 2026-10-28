import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { apiErrorCode } from '../auth/auth-ui.helpers';
import { MfaSetupPromptComponent } from '../auth/mfa-setup-prompt.component';
import { Page } from '../catalog/catalog.models';
import { ReportSummary, ReportTargetType, reportReasonLabels, reportStatusLabels } from './moderation.models';
import { ModerationService } from './moderation.service';

@Component({
  selector: 'lendly-moderation-queue-page',
  standalone: true,
  imports: [MfaSetupPromptComponent, RouterLink],
  template: `
    <section class="queue-page" aria-labelledby="page-title">
      <p class="page-eyebrow">Moderacja</p>
      <h1 id="page-title" class="page-title" tabindex="-1">Zgłoszenia</h1>

      @if (mfaSetupRequired()) {
        <lendly-mfa-setup-prompt />
      } @else {
      <div class="queue-controls">
        <nav class="state-tabs" aria-label="Stan zgłoszeń">
          <a routerLink="/moderation" [queryParams]="typeParam(null, 'OPEN')" [attr.aria-current]="state() === 'OPEN' ? 'page' : null">Otwarte</a>
          <a routerLink="/moderation" [queryParams]="typeParam(null, 'CLOSED')" [attr.aria-current]="state() === 'CLOSED' ? 'page' : null">Zamknięte</a>
        </nav>
        <div class="form-field type-filter">
          <label for="type-filter">Dotyczy</label>
          <select id="type-filter" [value]="targetType() ?? ''" (change)="changeType($any($event.target).value)">
            <option value="">Wszystkiego</option>
            <option value="LISTING">Ogłoszeń</option>
            <option value="USER">Użytkowników</option>
          </select>
        </div>
      </div>

      @if (loading() && !page()) {
        <p class="state-message" role="status">Wczytywanie zgłoszeń…</p>
      } @else if (errorMessage()) {
        <div class="notice-error" role="alert">
          <p>{{ errorMessage() }}</p>
          <button class="button-secondary" type="button" (click)="load(currentPage)">Spróbuj ponownie</button>
        </div>
      } @else if (page(); as results) {
        @if (results.items.length === 0) {
          <div class="empty-state">
            <h2>{{ state() === 'OPEN' ? 'Brak otwartych zgłoszeń' : 'Brak zamkniętych zgłoszeń' }}</h2>
            <p>{{ state() === 'OPEN' ? 'Nowe zgłoszenia od użytkowników pojawią się tutaj.' : 'Rozstrzygnięte i odrzucone zgłoszenia pojawią się tutaj.' }}</p>
          </div>
        } @else {
          <ul class="report-list">
            @for (item of results.items; track item.id) {
              <li>
                <a class="report-row" [routerLink]="['/moderation/reports', item.id]">
                  <span class="row-main">
                    <span class="row-title">{{ item.target.type === 'LISTING' ? 'Ogłoszenie' : 'Użytkownik' }}: {{ item.target.title }}</span>
                    <span class="row-meta">{{ reasons[item.reason] }} · zgłasza {{ item.reporter.displayName }} · {{ date(item.createdAt) }}</span>
                    @if (item.details) {
                      <span class="row-details">{{ item.details }}</span>
                    }
                  </span>
                  <span class="row-side">
                    <span class="row-status" [attr.data-status]="item.status">{{ statuses[item.status] }}</span>
                    @if (item.openReportsOnTarget > 1) {
                      <span class="row-count">{{ item.openReportsOnTarget }} otwarte zgłoszenia tego celu</span>
                    }
                  </span>
                </a>
              </li>
            }
          </ul>
          @if (results.totalPages > 1) {
            <nav class="pagination" aria-label="Strony zgłoszeń">
              <button class="button-secondary" type="button" [disabled]="results.page === 0" (click)="load(results.page - 1)">Poprzednia</button>
              <span>Strona {{ results.page + 1 }} z {{ results.totalPages }}</span>
              <button class="button-secondary" type="button" [disabled]="results.page + 1 >= results.totalPages" (click)="load(results.page + 1)">Następna</button>
            </nav>
          }
        }
      }
      }
    </section>
  `,
  styles: `
    .queue-page { max-width: 960px; padding-block: clamp(2rem, 5vw, 3.5rem) 4rem; }
    .queue-controls { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 1rem;
      margin: 1.5rem 0 1rem; border-bottom: 1px solid var(--border); }
    .state-tabs { display: flex; gap: 0.25rem; }
    .state-tabs a { display: inline-flex; min-height: 44px; align-items: center; padding-inline: 1rem; border-bottom: 3px solid transparent;
      color: var(--text-secondary); font-weight: 600; text-decoration: none; }
    .state-tabs a[aria-current="page"] { border-bottom-color: var(--primary); color: var(--primary); }
    .type-filter { min-width: 180px; padding-bottom: 0.75rem; }
    .report-list { margin: 0; padding: 0; list-style: none; }
    .report-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 1rem; padding: 0.85rem 0.5rem;
      border-bottom: 1px solid var(--border); color: inherit; text-decoration: none; }
    .report-row:hover .row-title { color: var(--primary); text-decoration: underline; text-underline-offset: 0.2em; }
    .row-main, .row-side { display: grid; gap: 0.25rem; min-width: 0; }
    .row-side { justify-items: end; text-align: right; }
    .row-title { font-weight: 600; overflow-wrap: anywhere; }
    .row-meta, .row-count { color: var(--text-secondary); font-size: 0.88rem; }
    .row-details { color: var(--text-primary); font-size: 0.92rem; overflow-wrap: anywhere; display: -webkit-box;
      -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .row-status { font-weight: 600; font-size: 0.9rem; }
    .row-status[data-status="OPEN"] { color: var(--warning); }
    .row-status[data-status="RESOLVED"] { color: var(--success); }
    .notice-error p { margin: 0 0 0.75rem; }
    @media (max-width: 560px) {
      .report-row { grid-template-columns: minmax(0, 1fr); }
      .row-side { justify-items: start; text-align: left; }
    }
  `
})
export class ModerationQueuePageComponent {
  private readonly moderation = inject(ModerationService);
  private readonly router = inject(Router);
  private subscription: Subscription | null = null;
  currentPage = 0;

  readonly state = signal<'OPEN' | 'CLOSED'>('OPEN');
  readonly targetType = signal<ReportTargetType | null>(null);
  readonly page = signal<Page<ReportSummary> | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly mfaSetupRequired = signal(false);
  readonly reasons = reportReasonLabels;
  readonly statuses = reportStatusLabels;

  constructor() {
    inject(ActivatedRoute).queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.state.set(params.get('state') === 'CLOSED' ? 'CLOSED' : 'OPEN');
      const type = params.get('type');
      this.targetType.set(type === 'LISTING' || type === 'USER' ? type : null);
      this.page.set(null);
      this.load(0);
    });
  }

  typeParam(type: ReportTargetType | null, state: 'OPEN' | 'CLOSED'): Record<string, string> {
    const params: Record<string, string> = {};
    if (state === 'CLOSED') params['state'] = 'CLOSED';
    const effectiveType = type ?? this.targetType();
    if (effectiveType) params['type'] = effectiveType;
    return params;
  }

  changeType(value: string): void {
    const type = value === 'LISTING' || value === 'USER' ? value : null;
    const params: Record<string, string | null> = { type, state: this.state() === 'CLOSED' ? 'CLOSED' : null };
    void this.router.navigate(['/moderation'], { queryParams: params });
  }

  date(value: string): string {
    return new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Warsaw' }).format(new Date(value));
  }

  load(page: number): void {
    this.currentPage = page;
    this.loading.set(true);
    this.errorMessage.set('');
    this.mfaSetupRequired.set(false);
    this.subscription?.unsubscribe();
    this.subscription = this.moderation.queue(this.state(), this.targetType(), page).subscribe({
      next: (results) => {
        this.page.set(results);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (apiErrorCode(error) === 'MFA_SETUP_REQUIRED') {
          this.mfaSetupRequired.set(true);
        } else {
          this.errorMessage.set('Nie udało się wczytać zgłoszeń. Spróbuj ponownie.');
        }
        this.loading.set(false);
      }
    });
  }
}
