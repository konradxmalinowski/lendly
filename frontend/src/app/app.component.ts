import { Component, DestroyRef, ElementRef, PLATFORM_ID, effect, inject, signal, viewChild } from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AccountBannerComponent } from './auth/account-banner.component';
import { AuthService } from './auth/auth.service';
import { PwaStatusComponent } from './pwa/pwa-status.component';
import { ThemeService } from './theme.service';

@Component({
  selector: 'lendly-root',
  standalone: true,
  imports: [AccountBannerComponent, PwaStatusComponent, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="skip-link" href="#main" (click)="skipToMain($event)">Przejdź do treści</a>

    <header class="site-header">
      <div class="header-inner">
        <a class="wordmark" routerLink="/" aria-label="Lendly, strona główna">lendly.</a>
        <button
          #menuToggle
          class="menu-toggle"
          type="button"
          [attr.aria-expanded]="menuOpen()"
          aria-controls="primary-navigation"
          (click)="toggleMenu()">
          <span class="menu-icon" [class.open]="menuOpen()" aria-hidden="true"><span></span></span>
          {{ menuOpen() ? 'Zamknij' : 'Menu' }}
        </button>

        <nav id="primary-navigation" class="primary-navigation" [class.mobile-open]="menuOpen()" aria-label="Nawigacja główna">
          <a routerLink="/" routerLinkActive="active" ariaCurrentWhenActive="page" [routerLinkActiveOptions]="{ exact: true }" (click)="closeMenu()">Start</a>
          <a routerLink="/items" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Przeglądaj</a>
          @if (auth.user()) {
            <a routerLink="/missions" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Misje</a>
            <a routerLink="/reservations" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Rezerwacje</a>
            <a routerLink="/account/listings" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Moje ogłoszenia</a>
            @if (auth.user()?.role === 'MODERATOR' || auth.user()?.role === 'ADMIN') {
              <a routerLink="/moderation" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Moderacja</a>
            }
            <a routerLink="/profile" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Mój profil</a>
          } @else {
            <a routerLink="/login" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">Zaloguj się</a>
          }
          <button
            class="theme-toggle"
            type="button"
            (click)="theme.toggle()">
            Ciemny motyw: {{ theme.isDark() ? 'włączony' : 'wyłączony' }}
          </button>
        </nav>
      </div>
    </header>

    @if (menuOpen()) {
      <div class="menu-backdrop" aria-hidden="true" (click)="closeMenu()"></div>
    }

    <lendly-account-banner [attr.inert]="menuOpen() ? '' : null" />

    <main id="main" tabindex="-1" [attr.inert]="menuOpen() ? '' : null">
      <router-outlet (activate)="focusPageHeading()" />
    </main>

    <footer class="site-footer" [attr.inert]="menuOpen() ? '' : null">
      <div class="footer-brand">
        <span class="footer-wordmark">lendly.</span>
        <span>Pożyczaj lokalnie.</span>
      </div>
      <nav class="footer-links" aria-label="Informacje prawne i kontakt">
        <a routerLink="/privacy">Polityka prywatności</a>
        <a routerLink="/terms">Regulamin</a>
        <a href="mailto:kontakt@lendly.com.pl">kontakt&#64;lendly.com.pl</a>
      </nav>
    </footer>

    <lendly-pwa-status />
  `,
  styleUrl: './app.component.scss',
  host: { '(document:keydown.escape)': 'closeMenu(true)' }
})
export class AppComponent {
  readonly menuOpen = signal(false);
  private firstActivation = true;
  private readonly document = inject(DOCUMENT);
  private readonly menuToggle = viewChild.required<ElementRef<HTMLButtonElement>>('menuToggle');

  constructor(readonly theme: ThemeService, readonly auth: AuthService) {
    // Session state is resolved in the browser, after static prerendering.
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      void this.auth.initialize();
      // The open menu covers the page, so the page must not scroll behind it.
      effect(() => this.document.documentElement.classList.toggle('menu-open', this.menuOpen()));
      // Breakpoint shared with app.component.scss: above it the navigation is inline and the overlay must go away.
      const desktop = window.matchMedia('(min-width: 1025px)');
      const onChange = () => desktop.matches && this.closeMenu();
      desktop.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => desktop.removeEventListener('change', onChange));
    }
  }

  toggleMenu(): void {
    this.menuOpen.update((isOpen) => !isOpen);
  }

  closeMenu(returnFocus = false): void {
    if (!this.menuOpen()) {
      return;
    }
    this.menuOpen.set(false);
    if (returnFocus) {
      this.menuToggle().nativeElement.focus();
    }
  }

  skipToMain(event: Event): void {
    // A bare "#main" would resolve against <base href="/"> and navigate to the home page.
    event.preventDefault();
    this.document.querySelector<HTMLElement>('#main')?.focus();
  }

  focusPageHeading(): void {
    // The first page keeps the browser's own focus start, so the skip link stays first in the Tab order.
    if (this.firstActivation) {
      this.firstActivation = false;
      return;
    }
    queueMicrotask(() => this.document.querySelector<HTMLElement>('#main h1')?.focus());
  }
}
