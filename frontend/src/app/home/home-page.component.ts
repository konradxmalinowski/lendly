import { Component, afterNextRender, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

@Component({
  selector: 'lendly-home-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (accountDeleted()) {
      <p class="account-notice" role="status">Twoje konto zostało usunięte.</p>
    }
    <section id="start" class="intro" aria-labelledby="intro-title">
      <div class="intro-copy">
        <p class="eyebrow">Pożyczaj w swojej okolicy</p>
        <h1 id="intro-title" tabindex="-1">Dobra rzecz nie musi być Twoja, żeby była pod ręką.</h1>
        <p class="intro-description">Lendly pomaga pożyczać przedmioty od osób z sąsiedztwa. Korzystasz wtedy, kiedy potrzebujesz, a rzeczy służą dłużej.</p>
        <form class="search-preview" role="search" (submit)="search($event, query.value)">
          <label class="visually-hidden" for="home-search">Czego potrzebujesz?</label>
          <input #query id="home-search" type="search" maxlength="100" placeholder="Czego potrzebujesz?" autocomplete="off">
          <button class="search-submit" type="submit">Szukaj</button>
        </form>
        <p class="intro-links"><a routerLink="/items">Zobacz wszystkie ogłoszenia</a> <a routerLink="/listings/new">Udostępnij swoją rzecz</a></p>
      </div>
      <div class="intro-side" aria-hidden="true">
        <span class="side-rule"></span>
        <p>Pożyczaj lokalnie.<br>Używaj wspólnie.</p>
      </div>
    </section>

    <section id="about" class="about" aria-labelledby="about-title">
      <div class="section-heading">
        <p class="eyebrow">Mniej kupowania na chwilę</p>
        <h2 id="about-title">Rzeczy krążą. Sąsiedzi korzystają.</h2>
      </div>
      <div class="about-copy">
        <p>Wiertarka, namiot czy projektor często są potrzebne tylko od czasu do czasu. Lendly ma ułatwić znalezienie ich u osób w pobliżu.</p>
        <p>Nie wiesz, czego szukać? Opisz zadanie, na przykład „chcę powiesić półkę”, a Misje podpowiedzą potrzebne rzeczy z ogłoszeń w okolicy.</p>
        <a class="mission-link" routerLink="/missions">Opisz swoje zadanie</a>
      </div>
    </section>
  `,
  styleUrl: './home-page.component.scss'
})
export class HomePageComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly accountDeleted = signal(false);

  constructor() {
    // Set after hydration so the server-rendered markup stays identical to the prerendered page.
    afterNextRender(() => {
      if (this.route.snapshot.queryParamMap.get('account') === 'deleted') {
        this.accountDeleted.set(true);
        void this.router.navigate([], { queryParams: { account: null }, queryParamsHandling: 'merge', replaceUrl: true });
      }
    });
  }

  search(event: Event, value: string): void {
    event.preventDefault();
    const q = value.trim();
    void this.router.navigate(['/items'], { queryParams: q ? { q } : {} });
  }
}
