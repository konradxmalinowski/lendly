import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './auth.service';

type BannerKind = 'verify-email' | 'mfa-setup';

@Component({
  selector: 'lendly-account-banner',
  standalone: true,
  imports: [RouterLink],
  template: `
    @switch (kind()) {
      @case ('verify-email') {
        <aside class="banner" aria-label="Powiadomienie o koncie">
          <p>Adres e-mail nie jest jeszcze potwierdzony.</p>
          <a routerLink="/verify-email">Wpisz kod z wiadomości</a>
        </aside>
      }
      @case ('mfa-setup') {
        <aside class="banner" aria-label="Powiadomienie o koncie">
          <p>Twoja rola wymaga weryfikacji dwuetapowej.</p>
          <a routerLink="/profile">Włącz ją w profilu</a>
        </aside>
      }
    }
  `,
  styles: `
    :host { display: block; width: min(calc(100% - 3rem), 1180px); margin-inline: auto; }
    .banner { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 1rem; margin-top: 1rem;
      padding: 0.35rem 1rem; border: 1px solid var(--border-strong); border-radius: var(--radius-small);
      background: var(--surface); color: var(--text-primary); }
    .banner p { margin: 0; padding-block: 0.5rem; }
    .banner a { display: inline-flex; min-height: 44px; align-items: center; color: var(--primary); font-weight: 600;
      text-underline-offset: 0.2em; }
  `
})
export class AccountBannerComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd), map((event) => event.urlAfterRedirects)),
    { initialValue: this.router.url }
  );

  readonly kind = computed<BannerKind | null>(() => {
    const user = this.auth.user();
    const path = this.url().split(/[?#]/)[0];
    if (!user) {
      return null;
    }
    if (!user.emailVerified) {
      return path === '/verify-email' || path.startsWith('/login') ? null : 'verify-email';
    }
    if (user.mfaRequired && !user.mfaEnabled) {
      return path === '/profile' || path.startsWith('/login') ? null : 'mfa-setup';
    }
    return null;
  });
}
