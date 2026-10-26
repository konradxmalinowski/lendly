import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AvailabilityBlock } from './reservation.models';
import { addDays, formatRange, reservationErrorMessage, warsawToday } from './reservation-format';
import { ReservationsService } from './reservations.service';

@Component({
  selector: 'lendly-availability-blocks',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="blocks" aria-labelledby="blocks-title">
      <h2 id="blocks-title">Niedostępne terminy</h2>
      <p class="field-hint">Zaznacz dni, w które przedmiot jest u Ciebie zajęty. W tych dniach nikt nie wyśle prośby o rezerwację.</p>

      @if (loading()) {
        <p class="state-message" role="status">Wczytywanie terminów…</p>
      } @else if (blocks().length === 0) {
        <p class="state-message">Brak zablokowanych terminów.</p>
      } @else {
        <ul class="block-list">
          @for (block of blocks(); track block.id) {
            <li>
              <span>{{ formatRange(block) }}</span>
              <button class="button-quiet" type="button" [disabled]="removing() === block.id" (click)="remove(block)">
                {{ removing() === block.id ? 'Usuwanie…' : 'Usuń' }}<span class="visually-hidden"> termin {{ formatRange(block) }}</span>
              </button>
            </li>
          }
        </ul>
      }

      <div class="block-form">
        <div class="form-field">
          <label for="block-start">Od</label>
          <input id="block-start" type="date" [min]="today" [max]="maxDate" [(ngModel)]="start" name="blockStart">
        </div>
        <div class="form-field">
          <label for="block-end">Do (włącznie)</label>
          <input id="block-end" type="date" [min]="start || today" [max]="maxDate" [(ngModel)]="end" name="blockEnd">
        </div>
        <button class="button-secondary" type="button" [disabled]="adding()" (click)="add()">
          {{ adding() ? 'Zapisywanie…' : 'Zablokuj termin' }}
        </button>
      </div>
      @if (errorMessage()) {
        <p class="field-error" role="alert">{{ errorMessage() }}</p>
      }
    </section>
  `,
  styles: `
    .blocks { display: grid; gap: 0.75rem; padding-block: 1.5rem; border-top: 1px solid var(--border); }
    h2 { margin: 0; font-size: 1.15rem; font-weight: 600; }
    .state-message { margin: 0; }
    .block-list { display: grid; gap: 0.25rem; margin: 0; padding: 0; list-style: none; }
    .block-list li { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem;
      padding-block: 0.25rem; border-bottom: 1px solid var(--border); }
    .block-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)) auto; align-items: end; gap: 0.75rem; }
    @media (max-width: 560px) { .block-form { grid-template-columns: minmax(0, 1fr); } }
  `
})
export class AvailabilityBlocksComponent implements OnInit {
  readonly listingId = input.required<string>();
  private readonly reservations = inject(ReservationsService);
  readonly today = warsawToday();
  readonly maxDate = addDays(this.today, 365);
  readonly blocks = signal<AvailabilityBlock[]>([]);
  readonly loading = signal(true);
  readonly adding = signal(false);
  readonly removing = signal<string | null>(null);
  readonly errorMessage = signal('');
  readonly formatRange = formatRange;
  start = '';
  end = '';

  ngOnInit(): void {
    this.reservations.blocks(this.listingId()).subscribe({
      next: (blocks) => {
        this.blocks.set(blocks);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Nie udało się wczytać niedostępnych terminów.');
        this.loading.set(false);
      }
    });
  }

  add(): void {
    this.errorMessage.set('');
    const end = this.end || this.start;
    if (!this.start || end < this.start) {
      this.errorMessage.set('Wybierz datę początku i końca, koniec nie wcześniej niż początek.');
      return;
    }
    this.adding.set(true);
    this.reservations.addBlock(this.listingId(), this.start, end).subscribe({
      next: (block) => {
        this.blocks.update((items) => [...items, block].sort((a, b) => a.startDate.localeCompare(b.startDate)));
        this.start = '';
        this.end = '';
        this.adding.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(reservationErrorMessage(error));
        this.adding.set(false);
      }
    });
  }

  remove(block: AvailabilityBlock): void {
    this.errorMessage.set('');
    this.removing.set(block.id);
    this.reservations.deleteBlock(this.listingId(), block.id).subscribe({
      next: () => {
        this.blocks.update((items) => items.filter((item) => item.id !== block.id));
        this.removing.set(null);
      },
      error: (error: unknown) => {
        this.errorMessage.set(reservationErrorMessage(error));
        this.removing.set(null);
      }
    });
  }
}
