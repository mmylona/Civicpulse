import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { admin, citizen, tokenResponse } from '../../../testing/auth';
import { roleGuard, safeReturnUrl, signedInGuard } from './auth.guards';
import { AuthService } from './auth.service';

@Component({ template: 'admin area' })
class AdminArea {}

@Component({ template: 'page' })
class AnyPage {}

describe('auth guards', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: 'admin/users',
            component: AdminArea,
            canActivate: [signedInGuard, roleGuard('ADMIN')],
          },
          { path: 'login', component: AnyPage },
          { path: 'forbidden', component: AnyPage },
        ]),
      ],
    });
  });

  function signInAs(user = citizen): void {
    TestBed.inject(AuthService).login(user.email, 'pw').subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login')
      .flush(tokenResponse(user));
  }

  it('redirects to login with returnUrl', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/users?page=2');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fadmin%2Fusers%3Fpage%3D2');
  });

  it('redirects to /forbidden without the role', async () => {
    signInAs(citizen);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/users');

    expect(TestBed.inject(Router).url).toBe('/forbidden');
  });

  it('allows users with the role', async () => {
    signInAs(admin);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/admin/users');

    expect(harness.routeNativeElement?.textContent).toContain('admin area');
  });

  it('safeReturnUrl only allows local paths', () => {
    expect(safeReturnUrl('/admin/users')).toBe('/admin/users');
    expect(safeReturnUrl('//evil.example')).toBe('/map');
    expect(safeReturnUrl('https://evil.example')).toBe('/map');
    expect(safeReturnUrl(undefined)).toBe('/map');
  });
});
