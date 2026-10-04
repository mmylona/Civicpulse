import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { apiError, tokenResponse } from '../../../testing/auth';
import { provideTestTranslations } from '../../../testing/translations';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LoginPage, provideTestTranslations()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    page = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  it('has labels', () => {
    for (const id of ['login-email', 'login-password']) {
      expect(page.querySelector(`label[for="${id}"]`)?.textContent?.trim()).toBeTruthy();
    }
  });

  it('shows error summary for empty form', async () => {
    await submit();

    const summary = page.querySelector<HTMLElement>('[role="alert"]')!;
    expect(summary.textContent).toContain('Please fix the following');
    expect([...summary.querySelectorAll('a')].map((a) => a.getAttribute('href'))).toEqual([
      '#login-email',
      '#login-password',
    ]);
    expect(document.activeElement).toBe(summary);
    expect(page.querySelector('#login-email')?.getAttribute('aria-invalid')).toBe('true');
    expect(page.querySelector('#login-email')?.getAttribute('aria-describedby')).toBe(
      'login-email-error',
    );
    http.expectNone('/api/v1/auth/login');
  });

  it('shows invalid credentials error', async () => {
    type('#login-email', 'ada@example.org');
    type('#login-password', 'wrong-password');
    await submit();

    const failure = apiError(401, 'INVALID_CREDENTIALS');
    http.expectOne('/api/v1/auth/login').flush(failure.body, failure);
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'The email or password is incorrect',
    );
    expect(page.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(false);
  });

  it('navigates to returnUrl after login', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentRef.setInput('returnUrl', '/admin/users');
    type('#login-email', 'ada@example.org');
    type('#login-password', 'correct-horse-battery');
    await submit();

    http.expectOne('/api/v1/auth/login').flush(tokenResponse());
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/admin/users');
  });
});
