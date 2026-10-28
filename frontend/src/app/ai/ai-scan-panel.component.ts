import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Category } from '../catalog/catalog.models';
import { AiService, ScanResult } from './ai.service';

export type ScanField = 'title' | 'categoryKey' | 'description';
export interface ScanApply {
  field: ScanField;
  value: string;
}

const MUST_PROVIDE_LABELS: Record<string, string> = {
  title: 'tytuł',
  categoryKey: 'kategorię',
  description: 'opis',
  condition: 'stan przedmiotu',
  pricing: 'cenę albo „Za darmo”',
  handover: 'punkt odbioru'
};

/** Suggests listing fields from the first photo. Nothing is applied or saved until the owner chooses it. */
@Component({
  selector: 'lendly-ai-scan-panel',
  standalone: true,
  template: `
    @if (available()) {
      <section class="scan" aria-labelledby="scan-title">
        <div class="scan-head">
          <h3 id="scan-title">Podpowiedzi ze zdjęcia</h3>
          <button class="button-secondary" type="button" [disabled]="busy() || !hasImage()" (click)="run()">
            {{ busy() ? 'Analizowanie zdjęcia…' : result() ? 'Rozpoznaj ponownie' : 'Rozpoznaj ze zdjęcia' }}
          </button>
        </div>
        @if (!hasImage()) {
          <p class="field-hint">Dodaj zdjęcie, a zaproponujemy tytuł, kategorię i opis. Ty decydujesz, co wstawić.</p>
        }
        @if (error()) {
          <p class="field-error" role="alert">{{ error() }}</p>
        }
        @if (result(); as scan) {
          <div class="suggestions" role="status">
            @if (!scan.itemRecognized) {
              <p>{{ scan.note }}</p>
            } @else {
              <p class="field-hint">Propozycje mogą być błędne. Sprawdź je przed użyciem.</p>
              <dl>
                @if (scan.title) {
                  <div><dt>Tytuł</dt><dd>{{ scan.title }}</dd>
                    <button class="button-quiet" type="button" (click)="use('title', scan.title)">Użyj<span class="visually-hidden"> tytułu</span></button></div>
                }
                @if (scan.categoryKey) {
                  <div><dt>Kategoria</dt><dd>{{ categoryName(scan.categoryKey) }}</dd>
                    <button class="button-quiet" type="button" (click)="use('categoryKey', scan.categoryKey)">Użyj<span class="visually-hidden"> kategorii</span></button></div>
                }
                @if (scan.description) {
                  <div><dt>Opis</dt><dd>{{ describe(scan) }}</dd>
                    <button class="button-quiet" type="button" (click)="use('description', describe(scan))">Użyj<span class="visually-hidden"> opisu</span></button></div>
                }
              </dl>
              @if (scan.keywords.length > 0) {
                <p class="keywords">Słowa, po których ktoś może szukać: {{ scan.keywords.join(', ') }}</p>
              }
              <button class="button-primary" type="button" (click)="useAll(scan)">Użyj wszystkich propozycji</button>
            }
            <p class="must">Musisz sam uzupełnić: {{ mustProvide() }}.</p>
          </div>
        }
      </section>
    }
  `,
  styles: `
    .scan { display: grid; gap: 0.75rem; padding: 1rem; border: 1px solid var(--border); border-radius: var(--radius-small); background: var(--surface); }
    .scan-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; }
    h3 { margin: 0; font-size: 1rem; font-weight: 600; }
    .suggestions { display: grid; gap: 0.75rem; }
    .suggestions p { margin: 0; }
    dl { display: grid; gap: 0.5rem; margin: 0; }
    dl > div { display: grid; grid-template-columns: 6rem minmax(0, 1fr) auto; align-items: start; gap: 0.75rem; }
    dt { color: var(--text-secondary); }
    dd { margin: 0; overflow-wrap: anywhere; white-space: pre-line; }
    dl .button-quiet { min-height: 44px; padding-block: 0; }
    .keywords, .must { color: var(--text-secondary); font-size: 0.9rem; }
    @media (max-width: 560px) { dl > div { grid-template-columns: minmax(0, 1fr) auto; } dt { grid-column: 1 / -1; } }
  `
})
export class AiScanPanelComponent {
  readonly image = input<Blob | string | null>(null);
  readonly categories = input<Category[]>([]);
  readonly apply = output<ScanApply>();
  private readonly ai = inject(AiService);
  readonly available = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<ScanResult | null>(null);
  readonly hasImage = computed(() => !!this.image());
  readonly mustProvide = computed(() => (this.result()?.mustProvide ?? []).map((field) => MUST_PROVIDE_LABELS[field] ?? field).join(', '));

  constructor() {
    this.ai.available().pipe(takeUntilDestroyed()).subscribe((available) => this.available.set(available));
  }

  async run(): Promise<void> {
    const source = this.image();
    if (!source) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const blob = typeof source === 'string' ? await (await fetch(source, { credentials: 'same-origin' })).blob() : source;
      this.ai.scan(blob).subscribe({
        next: (result) => {
          this.result.set(result);
          this.busy.set(false);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(error instanceof HttpErrorResponse && error.error?.code === 'AI_DAILY_LIMIT'
            ? 'Dzienny limit podpowiedzi AI w Lendly został wykorzystany. Wypełnij pola ręcznie, wszystko działa bez nich.'
            : error instanceof HttpErrorResponse && error.status === 429
            ? 'Wykorzystano limit analiz. Wypełnij pola ręcznie albo spróbuj później.'
            : 'Podpowiedzi są teraz niedostępne. Wypełnij pola ręcznie, wszystko działa bez nich.');
        }
      });
    } catch {
      this.busy.set(false);
      this.error.set('Nie udało się wczytać zdjęcia do analizy.');
    }
  }

  categoryName(key: string): string {
    return this.categories().find((category) => category.key === key)?.displayName ?? key;
  }

  describe(scan: ScanResult): string {
    const attributes = scan.attributes.map((attribute) => `${attribute.name}: ${attribute.value}`).join('\n');
    return attributes ? `${scan.description}\n\n${attributes}` : scan.description ?? '';
  }

  use(field: ScanField, value: string): void {
    this.apply.emit({ field, value });
  }

  useAll(scan: ScanResult): void {
    if (scan.title) this.use('title', scan.title);
    if (scan.categoryKey) this.use('categoryKey', scan.categoryKey);
    if (scan.description) this.use('description', this.describe(scan));
  }
}
