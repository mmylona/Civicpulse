import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  finalize,
  firstValueFrom,
  map,
  of,
  retry,
  shareReplay,
  tap,
  throwError,
  timeout,
  timer,
} from 'rxjs';
import { API_BASE } from '../api-base';
import { Role, TokenResponse, UserProfile } from './auth.models';

const AUTH = `${API_BASE}/auth`;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<{ token: string; user: UserProfile } | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly accessToken = computed(() => this.session()?.token ?? null);
  readonly isSignedIn = computed(() => this.session() !== null);

  hasRole(role: Role): boolean {
    return this.user()?.roles.includes(role) ?? false;
  }

  login(email: string, password: string): Observable<UserProfile> {
    return this.http
      .post<TokenResponse>(`${AUTH}/login`, { email, password })
      .pipe(map((response) => this.start(response)));
  }

  register(email: string, password: string, displayName: string): Observable<UserProfile> {
    return this.http
      .post<TokenResponse>(`${AUTH}/register`, { email, password, displayName })
      .pipe(map((response) => this.start(response)));
  }

  // share one request between concurrent callers
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.http.post<TokenResponse>(`${AUTH}/refresh`, null).pipe(
      retry({
        count: 1,
        delay: (error: unknown) =>
          isRotatedElsewhere(error) ? timer(250) : throwError(() => error),
      }),
      map((response) => {
        this.start(response);
        return response.accessToken;
      }),
      catchError((error: unknown) => {
        this.session.set(null);
        return throwError(() => error);
      }),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshInFlight;
  }

  restore(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        timeout(5000),
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH}/logout`, null).pipe(
      catchError(() => of(undefined)),
      tap(() => this.session.set(null)),
      map(() => undefined),
    );
  }

  clear(): void {
    this.session.set(null);
  }

  private start(response: TokenResponse): UserProfile {
    this.session.set({ token: response.accessToken, user: response.user });
    return response.user;
  }
}

function isRotatedElsewhere(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.error?.code === 'REFRESH_TOKEN_ROTATED';
}
