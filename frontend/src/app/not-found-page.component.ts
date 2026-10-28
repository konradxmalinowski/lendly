import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lendly-not-found-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="not-found" aria-labelledby="not-found-title">
      <p class="page-eyebrow">Błąd 404</p>
      <h1 id="not-found-title" class="page-title" tabindex="-1">Nie ma takiej strony</h1>
      <p class="lead">Adres jest błędny albo strona została usunięta. Ogłoszenia znajdziesz w katalogu.</p>
      <div class="actions">
        <a class="button-primary" routerLink="/items">Przeglądaj ogłoszenia</a>
        <a class="button-secondary" routerLink="/">Strona główna</a>
      </div>
    </section>
  `,
  styles: `
    .not-found {
      max-width: 40rem;
      padding-block: clamp(3rem, 10vw, 6rem);
    }

    .lead {
      margin: 1rem 0 2rem;
      color: var(--text-secondary);
      line-height: 1.6;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
  `
})
export class NotFoundPageComponent {}
