import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, firstValueFrom, map, Observable, of, shareReplay, tap } from 'rxjs';
import { ApiError, AuthResponse, CsrfResponse, GoogleAuthorizationResponse, PrivateProfile } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<PrivateProfile | null>(null);
  private readonly csrfToken = signal<string | null>(null);
  private initialization: Promise<void> | null = null;
  private csrfRequest: Observable<string> | null = null;
  private refreshRequest: Observable<boolean> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isSignedIn = computed(() => this.currentUser() !== null);
  readonly initialized = signal(false);

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

  signIn(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap((response) => this.applyAuthResponse(response)),
      finalize(() => {
        if (!this.currentUser()) {
          this.csrfToken.set(null);
        }
      })
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

  private applyAuthResponse(response: AuthResponse): void {
    this.currentUser.set(response.user);
    this.csrfToken.set(response.csrfToken);
    this.csrfRequest = null;
  }
}
