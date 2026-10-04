import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { apiError, tokenResponse } from '../../../testing/auth';
import { provideTestTranslations } from '../../../testing/translations';
import { RegisterPage } from './register-page';

describe('RegisterPage', () => {
  let fixture: ComponentFixture<RegisterPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [RegisterPage, provideTestTranslations()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegisterPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function fill(name: string, email: string, password: string): void {
    for (const [selector, value] of [
      ['#register-name', name],
      ['#register-email', email],
      ['#register-password', password],
    ]) {
      const input = page.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
  }

  async function submit(): Promise<void> {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  it('validates password before submit', async () => {
    fill('Ada', 'ada@example.org', 'too-short');
    await submit();

    expect(page.querySelector('#register-password-error')?.textContent).toContain(
      'at least 10 characters',
    );
    expect(page.querySelector('#register-password')?.getAttribute('aria-describedby')).toBe(
      'register-password-hint register-password-error',
    );
    http.expectNone('/api/v1/auth/register');
  });

  it('email already registered', async () => {
    fill('Ada', 'ada@example.org', 'correct-horse-battery');
    await submit();

    const taken = apiError(409, 'EMAIL_ALREADY_REGISTERED');
    http.expectOne('/api/v1/auth/register').flush(taken.body, taken);
    await fixture.whenStable();

    expect(page.querySelector('#register-email-error')?.textContent).toContain('already exists');
    expect(page.querySelector('[role="alert"] a')?.getAttribute('href')).toBe('#register-email');
  });

  it('maps server field errors', async () => {
    fill('Ada', 'ada@example.org', 'correct-horse-battery');
    await submit();

    const invalid = apiError(400, 'VALIDATION_FAILED', [
      { field: 'displayName', code: 'Size', message: 'size must be between 0 and 80' },
    ]);
    http.expectOne('/api/v1/auth/register').flush(invalid.body, invalid);
    await fixture.whenStable();

    expect(page.querySelector('#register-name-error')?.textContent).toContain('too long');
  });

  it('registers and navigates', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill('Ada', 'ada@example.org', 'correct-horse-battery');
    await submit();

    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({
      email: 'ada@example.org',
      password: 'correct-horse-battery',
      displayName: 'Ada',
    });
    request.flush(tokenResponse());
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/map');
  });
});
