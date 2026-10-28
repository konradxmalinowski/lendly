import { ApplicationConfig, isDevMode } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { authInterceptor } from './auth/auth.interceptor';
import { appRoutes } from './app.routes';
import { SeoTitleStrategy } from './seo/seo-title-strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(appRoutes),
    { provide: TitleStrategy, useExisting: SeoTitleStrategy },
    // The worker caches the app shell only; /api is never cached (see ngsw-config.json).
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    })
  ]
};
