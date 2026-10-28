import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { HomePageComponent } from './home/home-page.component';
import { DEFAULT_TITLE, RouteSeo } from './seo/seo-title-strategy';
import { siteJsonLd } from './seo/site-json-ld';

const loginTree = (router: Router, returnUrl: string) =>
  router.createUrlTree(['/login'], { queryParams: { returnUrl } });

// Any signed-in user, verified or not. Used by the profile and the verification screen.
const sessionGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  return auth.user() ? true : loginTree(router, state.url);
};

// Signed in with a confirmed e-mail address; unverified users are sent to the code screen.
const signedInGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const user = auth.user();
  if (!user) return loginTree(router, state.url);
  return user.emailVerified
    ? true
    : router.createUrlTree(['/verify-email'], { queryParams: { returnUrl: state.url } });
};

const moderatorGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const user = auth.user();
  if (!user) return loginTree(router, state.url);
  if (!user.emailVerified) {
    return router.createUrlTree(['/verify-email'], { queryParams: { returnUrl: state.url } });
  }
  return user.role === 'MODERATOR' || user.role === 'ADMIN' ? true : router.createUrlTree(['/']);
};

const privateSeo = (description: string): { seo: RouteSeo } => ({ seo: { description, noindex: true } });

export const appRoutes: Routes = [
  {
    path: '',
    component: HomePageComponent,
    pathMatch: 'full',
    title: DEFAULT_TITLE,
    data: {
      seo: {
        description: 'Lendly pomaga pożyczać przedmioty od osób z sąsiedztwa. Znajdź wiertarkę, namiot czy projektor w okolicy zamiast kupować je na chwilę.',
        jsonLd: siteJsonLd
      } satisfies RouteSeo
    }
  },
  {
    path: 'login',
    title: 'Zaloguj się | Lendly',
    data: privateSeo('Zaloguj się do swojego konta Lendly.'),
    loadComponent: () => import('./auth/login-page.component').then((module) => module.LoginPageComponent)
  },
  {
    path: 'login/mfa',
    title: 'Weryfikacja dwuetapowa | Lendly',
    data: privateSeo('Wpisz kod, aby dokończyć logowanie.'),
    loadComponent: () => import('./auth/login-mfa-page.component').then((module) => module.LoginMfaPageComponent)
  },
  {
    path: 'register',
    title: 'Utwórz konto | Lendly',
    data: privateSeo('Utwórz konto, aby korzystać z Lendly.'),
    loadComponent: () => import('./auth/register-page.component').then((module) => module.RegisterPageComponent)
  },
  {
    path: 'verify-email',
    title: 'Potwierdź adres e-mail | Lendly',
    data: privateSeo('Wpisz kod z wiadomości, aby potwierdzić adres e-mail.'),
    canActivate: [sessionGuard],
    loadComponent: () => import('./auth/verify-email-page.component').then((module) => module.VerifyEmailPageComponent)
  },
  {
    path: 'profile',
    title: 'Mój profil | Lendly',
    data: privateSeo('Zarządzaj ustawieniami swojego konta Lendly.'),
    canActivate: [sessionGuard],
    loadComponent: () => import('./auth/profile-page.component').then((module) => module.ProfilePageComponent)
  },
  {
    path: 'items',
    title: 'Rzeczy do pożyczenia | Lendly',
    data: {
      seo: {
        description: 'Przeglądaj przedmioty do pożyczenia od osób z okolicy. Filtruj po kategorii, cenie i odległości.',
        noindexWithQuery: true
      } satisfies RouteSeo
    },
    loadComponent: () => import('./catalog/items-page.component').then((module) => module.ItemsPageComponent)
  },
  {
    path: 'items/:id',
    title: 'Ogłoszenie | Lendly',
    data: { seo: { description: 'Ogłoszenie w Lendly.', dynamic: true } satisfies RouteSeo },
    loadComponent: () => import('./catalog/item-detail-page.component').then((module) => module.ItemDetailPageComponent)
  },
  {
    path: 'listings/new',
    title: 'Nowe ogłoszenie | Lendly',
    data: privateSeo('Dodaj swoje ogłoszenie w Lendly.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/listing-form-page.component').then((module) => module.ListingFormPageComponent)
  },
  {
    path: 'listings/:id/edit',
    title: 'Edytuj ogłoszenie | Lendly',
    data: privateSeo('Edytuj swoje ogłoszenie w Lendly.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/listing-form-page.component').then((module) => module.ListingFormPageComponent)
  },
  {
    path: 'account/listings',
    title: 'Moje ogłoszenia | Lendly',
    data: privateSeo('Zarządzaj swoimi ogłoszeniami w Lendly.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/my-listings-page.component').then((module) => module.MyListingsPageComponent)
  },
  {
    path: 'reservations',
    title: 'Rezerwacje | Lendly',
    data: privateSeo('Twoje prośby o rezerwację i rezerwacje Twoich ogłoszeń.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./reservations/reservations-page.component').then((module) => module.ReservationsPageComponent)
  },
  {
    path: 'reservations/:id',
    title: 'Rezerwacja | Lendly',
    data: privateSeo('Szczegóły rezerwacji w Lendly.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./reservations/reservation-detail-page.component').then((module) => module.ReservationDetailPageComponent)
  },
  {
    path: 'missions',
    title: 'Misje | Lendly',
    data: privateSeo('Opisz zadanie, a znajdziemy rzeczy do pożyczenia w okolicy.'),
    canActivate: [signedInGuard],
    loadComponent: () => import('./ai/missions-page.component').then((module) => module.MissionsPageComponent)
  },
  {
    path: 'moderation',
    title: 'Moderacja | Lendly',
    data: privateSeo('Kolejka zgłoszeń dla moderatorów.'),
    canActivate: [moderatorGuard],
    loadComponent: () => import('./moderation/moderation-queue-page.component').then((module) => module.ModerationQueuePageComponent)
  },
  {
    path: 'moderation/reports/:id',
    title: 'Zgłoszenie | Lendly',
    data: privateSeo('Szczegóły zgłoszenia dla moderatorów.'),
    canActivate: [moderatorGuard],
    loadComponent: () => import('./moderation/report-detail-page.component').then((module) => module.ReportDetailPageComponent)
  },
  {
    path: '**',
    title: 'Nie ma takiej strony | Lendly',
    data: privateSeo('Ta strona nie istnieje w Lendly.'),
    loadComponent: () => import('./not-found-page.component').then((module) => module.NotFoundPageComponent)
  }
];
