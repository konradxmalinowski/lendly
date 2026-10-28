import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { defer, finalize, firstValueFrom, map, Observable, of, shareReplay, tap } from 'rxjs';
import {
  ApiError,
  AuthFeatures,
  AuthResponse,
  CsrfResponse,
  GoogleAuthorizationResponse,
  HealthResponse,
  MfaChallengeResponse,
  MfaSetup,
  MfaStatus,
  PrivateProfile,
  RecoveryCodesResponse,
  SignInResult
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly featureFlags = signal<AuthFeatures | null>(null);
  private featuresRequested = false;
  private readonly currentUser = signal<PrivateProfile | null>(null);
  private readonly csrfToken = signal<string | null>(null);
  private initialization: Promise<void> | null = null;
  private csrfRequest: Observable<string> | null = null;
  private refreshRequest: Observable<boolean> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);
  readonly initialized = signal(false);
  /** Null until `/api/health` answers; consumers treat null as "available" so a slow health check never blocks sign-in. */
  readonly features = this.featureFlags.asReadonly();

  currentCsrfToken(): string | null {
    return this.csrfToken();
  }

  initialize(): Promise<void> {
    if (!this.initialization) {
      this.initialization = this.initializeSession();
    }
    return this.initialization;
  }

  ensureCsrfToken(): Observable<string> {
    const existingToken = this.csrfToken();
    if (existingToken) {
      return of(existingToken);
    }
    if (!this.csrfRequest) {
      this.csrfRequest = this.http.get<CsrfResponse>('/api/auth/csrf').pipe(
        map(({ csrfToken }) => csrfToken),
        tap((csrfToken) => this.csrfToken.set(csrfToken)),
        finalize(() => this.csrfRequest = null),
        shareReplay({ bufferSize: 1, refCount: false })
      );
    }
    return this.csrfRequest!;
  }

  async refreshSession(): Promise<boolean> {
    if (this.refreshRequest) {
      return firstValueFrom(this.refreshRequest);
    }

    this.refreshRequest = new Observable<boolean>((subscriber) => {
      this.ensureCsrfToken().subscribe({
        next: (token) => {
          this.http.post<void>('/api/auth/refresh', null, {
            headers: { 'X-CSRF-Token': token },
            withCredentials: true
          }).subscribe({
            next: () => subscriber.next(true),
            error: () => {
              this.clearSession();
              subscriber.next(false);
              subscriber.complete();
            },
            complete: () => subscriber.complete()
          });
        },
        error: () => {
          this.clearSession();
          subscriber.next(false);
          subscriber.complete();
        }
      });
    }).pipe(shareReplay({ bufferSize: 1, refCount: false }));

    try {
      return await firstValueFrom(this.refreshRequest);
    } finally {
      this.refreshRequest = null;
    }
  }

  loadFeatures(): void {
    if (!this.isBrowser || this.featuresRequested) {
      return;
    }
    this.featuresRequested = true;
    this.http.get<HealthResponse>('/api/health').subscribe({
      next: (health) => {
        if (health.features) {
          this.featureFlags.set({
            registration: health.features.registration !== false,
            googleSignIn: health.features.googleSignIn !== false,
            googleSignUp: health.features.googleSignUp === true,
            mfa: health.features.mfa !== false
          });
        }
      },
      error: () => {
        this.featuresRequested = false;
      }
    });
  }

  signIn(email: string, password: string): Observable<SignInResult> {
    return this.http.post<AuthResponse | MfaChallengeResponse>('/api/auth/login', { email, password }, { observe: 'response' }).pipe(
      map((response): SignInResult => {
        const body = response.body;
        if (response.status === 202 || !body || !('user' in body)) {
          return { status: 'mfa-required' };
        }
        this.applyAuthResponse(body);
        return { status: 'signed-in', user: body.user };
      }),
      finalize(() => {
        if (!this.currentUser()) {
          this.csrfToken.set(null);
        }
      })
    );
  }

  verifyMfa(code: string): Observable<AuthResponse> {
    // Every attempt consumes the anonymous CSRF context, so fetch a fresh token each time.
    return defer(() => {
      this.csrfToken.set(null);
      this.csrfRequest = null;
      return this.http.post<AuthResponse>('/api/auth/mfa/verify', { code });
    }).pipe(
      tap((response) => this.applyAuthResponse(response)),
      finalize(() => {
        if (!this.currentUser()) {
          this.csrfToken.set(null);
        }
      })
    );
  }

  verifyEmail(code: string): Observable<PrivateProfile> {
    return this.http.post<PrivateProfile>('/api/auth/email/verify', { code }).pipe(
      tap((profile) => this.currentUser.set(profile))
    );
  }

  resendEmailCode(): Observable<void> {
    return this.http.post<void>('/api/auth/email/resend', null);
  }

  refreshProfile(): Observable<PrivateProfile> {
    return this.http.get<PrivateProfile>('/api/me').pipe(
      tap((profile) => this.currentUser.set(profile))
    );
  }

  markEmailUnverified(): void {
    this.patchUser({ emailVerified: false });
  }

  mfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>('/api/me/mfa');
  }

  mfaSetup(): Observable<MfaSetup> {
    return this.http.post<MfaSetup>('/api/me/mfa/setup', null);
  }

  mfaEnable(code: string): Observable<RecoveryCodesResponse> {
    return this.http.post<RecoveryCodesResponse>('/api/me/mfa/enable', { code }).pipe(
      tap(() => this.patchUser({ mfaEnabled: true }))
    );
  }

  mfaRegenerateRecoveryCodes(code: string): Observable<RecoveryCodesResponse> {
    return this.http.post<RecoveryCodesResponse>('/api/me/mfa/recovery-codes', { code });
  }

  mfaDisable(code: string): Observable<void> {
    return this.http.post<void>('/api/me/mfa/disable', { code }).pipe(
      tap(() => this.patchUser({ mfaEnabled: false }))
    );
  }

  register(displayName: string, email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', { displayName, email, password }).pipe(
      tap((response) => this.applyAuthResponse(response)),
      finalize(() => {
        if (!this.currentUser()) {
          this.csrfToken.set(null);
        }
      })
    );
  }

  requestGoogleSignIn(): Observable<GoogleAuthorizationResponse> {
    return this.http.post<GoogleAuthorizationResponse>('/api/auth/google', {}).pipe(
      finalize(() => {
        if (!this.currentUser()) {
          this.csrfToken.set(null);
        }
      })
    );
  }

  requestGoogleLink(): Observable<GoogleAuthorizationResponse> {
    return this.http.post<GoogleAuthorizationResponse>('/api/auth/google/link', {});
  }

  updateProfile(displayName: string, areaLabel: string | null): Observable<PrivateProfile> {
    return this.http.patch<PrivateProfile>('/api/me', { displayName, areaLabel }).pipe(
      tap((profile) => this.currentUser.set(profile))
    );
  }

  signOut(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null).pipe(
      tap(() => this.clearSession())
    );
  }

  errorCode(error: unknown): string | null {
    if (!(error instanceof HttpErrorResponse)) {
      return null;
    }
    return (error.error as Partial<ApiError> | null)?.code ?? null;
  }

  clearSession(): void {
    this.currentUser.set(null);
    this.csrfToken.set(null);
    this.csrfRequest = null;
  }

  private async initializeSession(): Promise<void> {
    try {
      await firstValueFrom(this.ensureCsrfToken());
      const profile = await firstValueFrom(this.http.get<PrivateProfile>('/api/me'));
      this.currentUser.set(profile);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.clearSession();
      }
    } finally {
      this.initialized.set(true);
    }
  }

  private patchUser(changes: Partial<PrivateProfile>): void {
    this.currentUser.update((user) => user ? { ...user, ...changes } : user);
  }

  private applyAuthResponse(response: AuthResponse): void {
    this.currentUser.set(response.user);
    this.csrfToken.set(response.csrfToken);
    this.csrfRequest = null;
  }
}
