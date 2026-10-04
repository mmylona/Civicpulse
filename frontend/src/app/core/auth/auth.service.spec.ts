import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { admin, apiError, citizen, tokenResponse } from '../../../testing/auth';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts signed out', () => {
    expect(auth.isSignedIn()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('login stores token and user', async () => {
    const signedIn = firstValueFrom(auth.login('ada@example.org', 'correct-horse'));
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({ email: 'ada@example.org', password: 'correct-horse' });
    request.flush(tokenResponse());

    expect(await signedIn).toEqual(citizen);
    expect(auth.accessToken()).toBe('access-1');
    expect(auth.hasRole('CITIZEN')).toBe(true);
    expect(auth.hasRole('ADMIN')).toBe(false);
  });

  it('concurrent refresh calls share one request', async () => {
    const first = firstValueFrom(auth.refresh());
    const second = firstValueFrom(auth.refresh());

    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse(admin, 'access-2'));

    expect(await first).toBe('access-2');
    expect(await second).toBe('access-2');
    expect(auth.user()).toEqual(admin);
  });

  it('retries once on REFRESH_TOKEN_ROTATED', async () => {
    vi.useFakeTimers();
    try {
      const refreshed = firstValueFrom(auth.refresh());
      const rotated = apiError(401, 'REFRESH_TOKEN_ROTATED');
      http.expectOne('/api/v1/auth/refresh').flush(rotated.body, rotated);
      await vi.advanceTimersByTimeAsync(250);
      http.expectOne('/api/v1/auth/refresh').flush(tokenResponse());

      expect(await refreshed).toBe('access-1');
    } finally {
      vi.useRealTimers();
    }
  });

  it('restore without cookie stays signed out', async () => {
    const restored = auth.restore();
    const expired = apiError(401, 'INVALID_REFRESH_TOKEN');
    http.expectOne('/api/v1/auth/refresh').flush(expired.body, expired);

    await restored;
    expect(auth.isSignedIn()).toBe(false);
  });

  it('logout clears session even if request fails', async () => {
    auth.login('ada@example.org', 'correct-horse').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse());

    const signedOut = firstValueFrom(auth.logout());
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await signedOut;

    expect(auth.isSignedIn()).toBe(false);
  });
});
