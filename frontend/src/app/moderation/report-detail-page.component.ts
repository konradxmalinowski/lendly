import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';
import { apiErrorCode } from '../auth/auth-ui.helpers';
import { MfaSetupPromptComponent } from '../auth/mfa-setup-prompt.component';
import { ModerationAction, ReportDetail, historyLabels, reportReasonLabels, reportStatusLabels } from './moderation.models';
import { ModerationService } from './moderation.service';

interface ActionConfig {
  label: string;
  confirmLabel: string;
  reasonLabel: string;
  reasonRequired: boolean;
  primary: boolean;
  success: string;
}

const ACTIONS: Record<ModerationAction, ActionConfig> = {
  HIDE_LISTING: { label: 'Ukryj ogłoszenie', confirmLabel: 'Ukryj ogłoszenie', reasonLabel: 'Powód ukrycia (zobaczy go właściciel)',
    reasonRequired: true, primary: true, success: 'Ogłoszenie zostało ukryte, a oczekujące prośby odrzucone.' },
  RESTORE_LISTING: { label: 'Przywróć ogłoszenie', confirmLabel: 'Przywróć ogłoszenie', reasonLabel: 'Notatka',
    reasonRequired: false, primary: false, success: 'Ogłoszenie jest znowu widoczne.' },
  SUSPEND_USER: { label: 'Zawieś konto', confirmLabel: 'Zawieś konto', reasonLabel: 'Powód zawieszenia',
    reasonRequired: true, primary: false, success: 'Konto zostało zawieszone i wylogowane.' },
  REINSTATE_USER: { label: 'Przywróć konto', confirmLabel: 'Przywróć konto', reasonLabel: 'Notatka',
    reasonRequired: false, primary: false, success: 'Konto zostało przywrócone.' },
  RESOLVE: { label: 'Rozstrzygnij', confirmLabel: 'Zamknij jako rozstrzygnięte', reasonLabel: 'Notatka moderatora',
    reasonRequired: false, primary: true, success: 'Zgłoszenie zostało rozstrzygnięte.' },
  DISMISS: { label: 'Odrzuć zgłoszenie', confirmLabel: 'Odrzuć zgłoszenie', reasonLabel: 'Dlaczego zgłoszenie jest bezzasadne',
    reasonRequired: false, primary: false, success: 'Zgłoszenie zostało odrzucone.' }
};

@Component({
  selector: 'lendly-report-detail-page',
  standalone: true,
  imports: [FormsModule, MfaSetupPromptComponent, RouterLink],
  templateUrl: './report-detail-page.component.html',
  styleUrl: './report-detail-page.component.scss'
})
export class ReportDetailPageComponent {
  private readonly moderation = inject(ModerationService);

  readonly report = signal<ReportDetail | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly pending = signal<ModerationAction | null>(null);
  readonly busy = signal(false);
  readonly actionError = signal('');
  readonly mfaSetupRequired = signal(false);
  readonly successMessage = signal('');
  reason = '';

  readonly actions = ACTIONS;
  readonly reasons = reportReasonLabels;
  readonly statuses = reportStatusLabels;
  readonly history = historyLabels;
  readonly config = computed(() => {
    const action = this.pending();
    return action ? ACTIONS[action] : null;
  });

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(
      tap(() => this.loading.set(true)),
      switchMap((params) => this.moderation.detail(params.get('id') ?? '')),
      takeUntilDestroyed()
    ).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.mfaSetupRequired.set(apiErrorCode(error) === 'MFA_SETUP_REQUIRED');
        this.loadError.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'Nie masz dostępu do moderacji.'
          : error instanceof HttpErrorResponse && error.status === 404 ? 'Nie znaleziono tego zgłoszenia.'
          : 'Nie udało się wczytać zgłoszenia. Spróbuj ponownie.');
        this.loading.set(false);
      }
    });
  }

  start(action: ModerationAction): void {
    this.reason = '';
    this.actionError.set('');
    this.successMessage.set('');
    this.pending.set(action);
    setTimeout(() => document.getElementById('moderation-reason')?.focus());
  }

  cancel(): void {
    this.pending.set(null);
    setTimeout(() => document.querySelector<HTMLElement>('.actions .button-row button')?.focus());
  }

  confirm(): void {
    const report = this.report();
    const action = this.pending();
    if (!report || !action) return;
    const reason = this.reason.trim() || null;
    if (ACTIONS[action].reasonRequired && !reason) {
      this.actionError.set('Podaj powód. Zostanie zapisany w historii.');
      return;
    }
    this.busy.set(true);
    this.actionError.set('');
    this.request(report, action, reason).pipe(switchMap(() => this.moderation.detail(report.id))).subscribe({
      next: (updated) => {
        this.report.set(updated);
        this.pending.set(null);
        this.successMessage.set(ACTIONS[action].success);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const code = apiErrorCode(error);
        this.mfaSetupRequired.set(code === 'MFA_SETUP_REQUIRED');
        this.actionError.set(code === 'FORBIDDEN' ? 'Ta akcja wymaga uprawnień administratora.'
          : code === 'CANNOT_SUSPEND_ADMIN' ? 'Konta administratora nie można tu zawiesić.'
          : code === 'CANNOT_SUSPEND_SELF' ? 'Nie możesz zawiesić własnego konta.'
          : error instanceof HttpErrorResponse && error.status === 409 ? 'Stan zgłoszenia albo celu właśnie się zmienił. Odśwież stronę.'
          : 'Nie udało się wykonać akcji. Spróbuj ponownie.');
      }
    });
  }

  date(value: string | null): string {
    return value ? new Intl.DateTimeFormat('pl-PL', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Warsaw' }).format(new Date(value)) : '';
  }

  private request(report: ReportDetail, action: ModerationAction, reason: string | null): Observable<unknown> {
    switch (action) {
      case 'RESOLVE': return this.moderation.close(report.id, 'RESOLVED', reason);
      case 'DISMISS': return this.moderation.close(report.id, 'DISMISSED', reason);
      case 'HIDE_LISTING': return this.moderation.listingAction(report.target.id, 'hide', reason, report.id);
      case 'RESTORE_LISTING': return this.moderation.listingAction(report.target.id, 'restore', reason, report.id);
      case 'SUSPEND_USER': return this.moderation.userAction(report.target.user.id, 'suspend', reason, report.id);
      case 'REINSTATE_USER': return this.moderation.userAction(report.target.user.id, 'reinstate', reason, report.id);
    }
  }
}
