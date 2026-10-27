import { Component, PLATFORM_ID, inject, signal } from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AccountBannerComponent } from './auth/account-banner.component';
import { AuthService } from './auth/auth.service';
import { PwaStatusComponent } from './pwa/pwa-status.component';
import { ThemeService } from './theme.service';

@Component({
  selector: 'lendly-root',
  standalone: true,
  imports: [AccountBannerComponent, PwaStatusComponent, RouterLink, RouterOutlet],
  template: `
    <a class="skip-link" href="#main">Przejdź do treści</a>

    <header class="site-header">
      <div class="header-inner">
        <a class="wordmark" routerLink="/" aria-label="Lendly, strona główna">lendly.</a>
        <button
          class="menu-toggle"
          type="button"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="primary-navigation"
          (click)="toggleMenu()">
          {{ menuOpen() ? 'Zamknij menu' : 'Otwórz menu' }}
        </button>

        <nav id="primary-navigation" class="primary-navigation" [class.mobile-open]="menuOpen()" aria-label="Nawigacja główna">
          <a routerLink="/" (click)="closeMenu()">Start</a>
          <a routerLink="/items" (click)="closeMenu()">Przeglądaj</a>
          @if (auth.user()) {
            <a routerLink="/missions" (click)="closeMenu()">Misje</a>
            <a routerLink="/reservations" (click)="closeMenu()">Rezerwacje</a>
            <a routerLink="/account/listings" (click)="closeMenu()">Moje ogłoszenia</a>
            @if (auth.user()?.role === 'MODERATOR' || auth.user()?.role === 'ADMIN') {
              <a routerLink="/moderation" (click)="closeMenu()">Moderacja</a>
            }
            <a routerLink="/profile" (click)="closeMenu()">Mój profil</a>
          } @else {
            <a routerLink="/login" (click)="closeMenu()">Zaloguj się</a>
          }
          <button
            class="theme-toggle"
            type="button"
            [attr.aria-pressed]="theme.isDark()"
            [attr.aria-label]="theme.isDark() ? 'Tryb ciemny, włączony' : 'Tryb ciemny, wyłączony'"
            (click)="theme.toggle()">
            {{ theme.isDark() ? 'Ciemny motyw' : 'Jasny motyw' }}
          </button>
        </nav>
      </div>
    </header>

    <lendly-account-banner />

    <main #mainContent id="main" tabindex="-1">
      <router-outlet (activate)="focusPageHeading()" />
    </main>

    <footer class="site-footer">
      <span class="footer-wordmark">lendly.</span>
      <span>Pożyczaj lokalnie.</span>
    </footer>

    <lendly-pwa-status />
  `,
  styleUrl: './app.component.scss'
})
export class AppComponent {
  readonly menuOpen = signal(false);
  private readonly document = inject(DOCUMENT);

  constructor(readonly theme: ThemeService, readonly auth: AuthService) {
    // Session state is resolved in the browser, after static prerendering.
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      void this.auth.initialize();
    }
  }

  toggleMenu(): void {
    this.menuOpen.update((isOpen) => !isOpen);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  focusPageHeading(): void {
    queueMicrotask(() => this.document.querySelector<HTMLElement>('#main h1')?.focus());
  }
}
