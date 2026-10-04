import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { safeReturnUrl } from '../../core/auth/auth.guards';
import { AuthService } from '../../core/auth/auth.service';
import { toApiError } from '../../core/http/api-error';
import { ErrorSummary } from '../../shared/forms/error-summary';
import { SummaryItem, errorKey, showErrors, summaryFor } from '../../shared/forms/form-errors';

@Component({
  selector: 'cp-login-page',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, ErrorSummary],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="form-page" *transloco="let t">
      <h1>{{ t('pages.login.heading') }}</h1>
      <p class="lede">{{ t('pages.login.intro') }}</p>

      <cp-error-summary [items]="summary()" />

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label class="field__label" for="login-email">{{ t('pages.login.email') }}</label>
          @if (show('email')) {
            <span class="field__error" id="login-email-error">{{ t(message('email')!) }}</span>
          }
          <input
            class="input"
            id="login-email"
            type="email"
            formControlName="email"
            autocomplete="username"
            spellcheck="false"
            [attr.aria-invalid]="show('email')"
            [attr.aria-describedby]="show('email') ? 'login-email-error' : null"
          />
        </div>

        <div class="field">
          <label class="field__label" for="login-password">{{ t('pages.login.password') }}</label>
          @if (show('password')) {
            <span class="field__error" id="login-password-error">{{
              t(message('password')!)
            }}</span>
          }
          <input
            class="input"
            id="login-password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            [attr.aria-invalid]="show('password')"
            [attr.aria-describedby]="show('password') ? 'login-password-error' : null"
          />
        </div>

        <button class="button button--primary" type="submit" [disabled]="pending()">
          {{ pending() ? t('pages.login.submitting') : t('pages.login.submit') }}
        </button>
      </form>

      <p class="form-footer">
        {{ t('pages.login.noAccount') }}
        <a routerLink="/register" [queryParams]="returnUrl() ? { returnUrl: returnUrl() } : {}">{{
          t('pages.login.register')
        }}</a>
      </p>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly pending = signal(false);
  protected readonly summary = signal<SummaryItem[]>([]);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected show(name: 'email' | 'password'): boolean {
    return showErrors(this.form.controls[name]);
  }

  protected message(name: 'email' | 'password'): string | null {
    return errorKey(this.form.controls[name]);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.summary.set(
        summaryFor(this.form, {
          email: { id: 'login-email', label: 'pages.login.email' },
          password: { id: 'login-password', label: 'pages.login.password' },
        }),
      );
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.pending.set(true);
    this.auth.login(email, password).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (error: unknown) => {
        this.pending.set(false);
        this.summary.set([{ key: `errors.${toApiError(error).code}` }]);
      },
    });
  }
}
