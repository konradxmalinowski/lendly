import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { HomePageComponent } from './home/home-page.component';

const signedInGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  return auth.user() ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

const moderatorGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const role = auth.user()?.role;
  if (!auth.user()) return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  return role === 'MODERATOR' || role === 'ADMIN' ? true : router.createUrlTree(['/']);
};

export const appRoutes: Routes = [
  { path: '', component: HomePageComponent, pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./auth/login-page.component').then((module) => module.LoginPageComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register-page.component').then((module) => module.RegisterPageComponent)
  },
  {
    path: 'profile',
    canActivate: [signedInGuard],
    loadComponent: () => import('./auth/profile-page.component').then((module) => module.ProfilePageComponent)
  },
  {
    path: 'items',
    loadComponent: () => import('./catalog/items-page.component').then((module) => module.ItemsPageComponent)
  },
  {
    path: 'items/:id',
    loadComponent: () => import('./catalog/item-detail-page.component').then((module) => module.ItemDetailPageComponent)
  },
  {
    path: 'listings/new',
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/listing-form-page.component').then((module) => module.ListingFormPageComponent)
  },
  {
    path: 'listings/:id/edit',
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/listing-form-page.component').then((module) => module.ListingFormPageComponent)
  },
  {
    path: 'account/listings',
    canActivate: [signedInGuard],
    loadComponent: () => import('./listings/my-listings-page.component').then((module) => module.MyListingsPageComponent)
  },
  {
    path: 'reservations',
    canActivate: [signedInGuard],
    loadComponent: () => import('./reservations/reservations-page.component').then((module) => module.ReservationsPageComponent)
  },
  {
    path: 'reservations/:id',
    canActivate: [signedInGuard],
    loadComponent: () => import('./reservations/reservation-detail-page.component').then((module) => module.ReservationDetailPageComponent)
  },
  {
    path: 'missions',
    canActivate: [signedInGuard],
    loadComponent: () => import('./ai/missions-page.component').then((module) => module.MissionsPageComponent)
  },
  {
    path: 'moderation',
    canActivate: [moderatorGuard],
    loadComponent: () => import('./moderation/moderation-queue-page.component').then((module) => module.ModerationQueuePageComponent)
  },
  {
    path: 'moderation/reports/:id',
    canActivate: [moderatorGuard],
    loadComponent: () => import('./moderation/report-detail-page.component').then((module) => module.ReportDetailPageComponent)
  },
  { path: '**', redirectTo: '' }
];
