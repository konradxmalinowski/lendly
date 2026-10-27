import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { apiErrorCode, safeReturnPath } from './auth-ui.helpers';
import { AuthService } from './auth.service';

const unsafeMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const AUTH_RETRY = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isApiRequest = request.url.startsWith('/api/');
  const isUnsafe = unsafeMethods.has(request.method.toUpperCase());
  const isAuthEndpoint = request.url.startsWith('/api/auth/');
  const csrfRequest = isApiRequest && isUnsafe
    ? auth.ensureCsrfToken().pipe(
      switchMap((token) => next(handleApiRequest(request, token)))
    )
    : next(handleApiRequest(request));

  return csrfRequest.pipe(catchError((error: unknown) => {
    if (
      error instanceof HttpErrorResponse &&
      error.status === 403 &&
      isApiRequest &&
      !isAuthEndpoint &&
      apiErrorCode(error) === 'EMAIL_NOT_VERIFIED' &&
      typeof window !== 'undefined'
    ) {
      auth.markEmailUnverified();
      if (!router.url.startsWith('/verify-email')) {
        void router.navigate(['/verify-email'], { queryParams: { returnUrl: safeReturnPath(router.url) } });
      }
      return throwError(() => error);
    }

    if (
      !(error instanceof HttpErrorResponse) ||
      error.status !== 401 ||
      !isApiRequest ||
      isAuthEndpoint ||
      request.context.get(AUTH_RETRY)
    ) {
      return throwError(() => error);
    }

    return from(auth.refreshSession()).pipe(
      switchMap((refreshed) => {
        if (!refreshed) {
          return throwError(() => error);
        }
        return next(handleApiRequest(request, auth.currentCsrfToken(), true));
      })
    );
  }));
};

function handleApiRequest(request: HttpRequest<unknown>, token?: string | null, retried = false) {
  const context = request.context;
  if (retried) {
    context.set(AUTH_RETRY, true);
  }
  const headers = token && unsafeMethods.has(request.method.toUpperCase())
    ? request.headers.set('X-CSRF-Token', token)
    : request.headers;
  return request.clone({
    context,
    headers,
    withCredentials: request.url.startsWith('/api/')
  });
}
