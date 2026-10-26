import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReportReason, ReportTargetType, reportReasonLabels } from './moderation.models';
import { ModerationService } from './moderation.service';

let nextId = 0;

/** Inline report form: a quiet trigger that expands into reason, details, and send. */
@Component({
  selector: 'lendly-report-button',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (sent()) {
      <p class="report-sent" role="status">Dziękujemy. Zgłoszenie trafiło do moderacji.</p>
    } @else if (!open()) {
      <button class="button-quiet report-trigger" type="button" (click)="toggle(true)">{{ label() }}</button>
    } @else {
      <form class="report-form" (ngSubmit)="send()" novalidate>
        <fieldset>
          <legend>{{ label() }}: wybierz powód</legend>
          @for (entry of reasons; track entry[0]) {
            <label class="reason"><input type="radio" [name]="'reason-' + uid" [value]="entry[0]" [(ngModel)]="reason"> {{ entry[1] }}</label>
          }
        </fieldset>
        <div class="form-field">
          <label [for]="'report-details-' + uid">Szczegóły {{ reason === 'OTHER' ? '' : '(opcjonalnie)' }}</label>
          <textarea [id]="'report-details-' + uid" [name]="'details-' + uid" rows="3" maxlength="1000" [(ngModel)]="details"></textarea>
          <p class="field-hint">Zgłaszający pozostaje anonimowy dla zgłoszonej osoby. Zgłoszenie widzą tylko moderatorzy.</p>
        </div>
        @if (error()) {
          <p class="field-error" role="alert">{{ error() }}</p>
        }
        <div class="report-actions">
          <button class="button-primary" type="submit" [disabled]="sending()">{{ sending() ? 'Wysyłanie…' : 'Wyślij zgłoszenie' }}</button>
          <button class="button-quiet" type="button" (click)="toggle(false)">Anuluj</button>
        </div>
      </form>
    }
  `,
  styles: `
    .report-trigger { padding-inline: 0; font-size: 0.9rem; font-weight: 500; }
    .report-sent { margin: 0; color: var(--success); font-size: 0.92rem; }
    .report-form { display: grid; gap: 0.75rem; padding: 1rem; border: 1px solid var(--border); border-radius: var(--radius-small); background: var(--surface); }
    fieldset { display: grid; gap: 0.25rem; margin: 0; padding: 0; border: 0; }
    legend { margin-bottom: 0.4rem; padding: 0; font-weight: 600; }
    .reason { display: flex; min-height: 44px; align-items: center; gap: 0.5rem; cursor: pointer; }
    .reason input { width: 1.1rem; height: 1.1rem; accent-color: var(--primary); }
    .report-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  `
})
export class ReportButtonComponent {
  readonly targetType = input.required<ReportTargetType>();
  readonly targetId = input.required<string>();
  readonly label = input('Zgłoś');
  private readonly moderation = inject(ModerationService);
  readonly uid = ++nextId;
  readonly reasons = Object.entries(reportReasonLabels) as [ReportReason, string][];
  readonly open = signal(false);
  readonly sending = signal(false);
  readonly sent = signal(false);
  readonly error = signal('');
  reason: ReportReason | null = null;
  details = '';

  toggle(open: boolean): void {
    this.open.set(open);
    this.error.set('');
    if (open) {
      setTimeout(() => document.querySelector<HTMLInputElement>(`input[name="reason-${this.uid}"]`)?.focus());
    }
  }

  send(): void {
    const details = this.details.trim() || null;
    if (!this.reason) {
      this.error.set('Wybierz powód zgłoszenia.');
      return;
    }
    if (this.reason === 'OTHER' && !details) {
      this.error.set('Opisz krótko, co jest nie tak.');
      return;
    }
    this.sending.set(true);
    this.error.set('');
    this.moderation.report(this.targetType(), this.targetId(), this.reason, details).subscribe({
      next: () => {
        this.sent.set(true);
        this.sending.set(false);
      },
      error: (error: unknown) => {
        this.sending.set(false);
        const code = error instanceof HttpErrorResponse ? (error.error as { code?: string } | null)?.code : null;
        this.error.set(code === 'REPORT_ALREADY_OPEN' ? 'Już to zgłaszasz. Twoje poprzednie zgłoszenie czeka na moderację.'
          : code === 'RATE_LIMITED' ? 'Wysłano dziś zbyt wiele zgłoszeń. Spróbuj jutro.'
          : error instanceof HttpErrorResponse && error.status === 401 ? 'Sesja wygasła. Zaloguj się ponownie.'
          : 'Nie udało się wysłać zgłoszenia. Spróbuj ponownie.');
      }
    });
  }
}
