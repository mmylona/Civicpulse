import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { apiError, tokenResponse } from '../../../testing/auth';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  function signIn(): void {
    auth.login('ada@example.org', 'correct-horse').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokenResponse(undefined, 'access-1'));
  }

  it('adds bearer token', () => {
    signIn();
    client.get('/api/v1/me').subscribe();

    expect(http.expectOne('/api/v1/me').request.headers.get('Authorization')).toBe(
      'Bearer access-1',
    );
  });

  it('skips auth endpoints and non-api urls', () => {
    signIn();
    client.post('/api/v1/auth/logout', null).subscribe();
    client.get('/i18n/en.json').subscribe();

    expect(http.expectOne('/api/v1/auth/logout').request.headers.has('Authorization')).toBe(false);
    expect(http.expectOne('/i18n/en.json').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes and retries on 401', async () => {
    signIn();
    const response = firstValueFrom(client.get<{ ok: boolean }>('/api/v1/me'));

    const expired = apiError(401, 'TOKEN_INVALID');
    http.expectOne('/api/v1/me').flush(expired.body, expired);
    http.expectOne('/api/v1/auth/refresh').flush(tokenResponse(undefined, 'access-2'));
    const retried = http.expectOne('/api/v1/me');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer access-2');
    retried.flush({ ok: true });

    expect(await response).toEqual({ ok: true });
  });

  it('goes to login when refresh fails', async () => {
    signIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const response = firstValueFrom(client.get('/api/v1/me'));

    const expired = apiError(401, 'TOKEN_INVALID');
    http.expectOne('/api/v1/me').flush(expired.body, expired);
    const ended = apiError(401, 'INVALID_REFRESH_TOKEN');
    http.expectOne('/api/v1/auth/refresh').flush(ended.body, ended);

    await expect(response).rejects.toMatchObject({ status: 401 });
    expect(auth.isSignedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('no refresh when signed out', async () => {
    const response = firstValueFrom(client.get('/api/v1/me'));
    const anonymous = apiError(401, 'AUTHENTICATION_REQUIRED');
    http.expectOne('/api/v1/me').flush(anonymous.body, anonymous);

    await expect(response).rejects.toMatchObject({ status: 401 });
    http.expectNone('/api/v1/auth/refresh');
  });
});
