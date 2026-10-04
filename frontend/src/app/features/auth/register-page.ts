import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { safeReturnUrl } from '../../core/auth/auth.guards';
import { AuthService } from '../../core/auth/auth.service';
import { toApiError } from '../../core/http/api-error';
import { ErrorSummary } from '../../shared/forms/error-summary';
import {
  FieldRef,
  SummaryItem,
  applyServerErrors,
  errorKey,
  passwordPolicy,
  showErrors,
  summaryFor,
} from '../../shared/forms/form-errors';

type Field = 'displayName' | 'email' | 'password';

const FIELDS: Record<Field, FieldRef> = {
  displayName: { id: 'register-name', label: 'pages.register.displayName' },
  email: { id: 'register-email', label: 'pages.register.email' },
  password: { id: 'register-password', label: 'pages.register.password' },
};

@Component({
  selector: 'cp-register-page',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, ErrorSummary],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="form-page" *transloco="let t">
      <h1>{{ t('pages.register.heading') }}</h1>
      <p class="lede">{{ t('pages.register.intro') }}</p>

      <cp-error-summary [items]="summary()" />

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label class="field__label" for="register-name">{{
            t('pages.register.displayName')
          }}</label>
          <span class="field__hint" id="register-name-hint">{{
            t('pages.register.displayNameHint')
          }}</span>
          @if (show('displayName')) {
            <span class="field__error" id="register-name-error">{{
              t(message('displayName')!)
            }}</span>
          }
          <input
            class="input"
            id="register-name"
            type="text"
            formControlName="displayName"
            autocomplete="name"
            [attr.aria-invalid]="show('displayName')"
            [attr.aria-describedby]="describedBy('displayName', 'register-name-hint')"
          />
        </div>

        <div class="field">
          <label class="field__label" for="register-email">{{ t('pages.register.email') }}</label>
          @if (show('email')) {
            <span class="field__error" id="register-email-error">{{ t(message('email')!) }}</span>
          }
          <input
            class="input"
            id="register-email"
            type="email"
            formControlName="email"
            autocomplete="email"
            spellcheck="false"
            [attr.aria-invalid]="show('email')"
            [attr.aria-describedby]="describedBy('email')"
          />
        </div>

        <div class="field">
          <label class="field__label" for="register-password">{{
            t('pages.register.password')
          }}</label>
          <span class="field__hint" id="register-password-hint">{{
            t('pages.register.passwordHint')
          }}</span>
          @if (show('password')) {
            <span class="field__error" id="register-password-error">{{
              t(message('password')!)
            }}</span>
          }
          <input
            class="input"
            id="register-password"
            type="password"
            formControlName="password"
            autocomplete="new-password"
            [attr.aria-invalid]="show('password')"
            [attr.aria-describedby]="describedBy('password', 'register-password-hint')"
          />
        </div>

        <button class="button button--primary" type="submit" [disabled]="pending()">
          {{ pending() ? t('pages.register.submitting') : t('pages.register.submit') }}
        </button>
      </form>

      <p class="form-footer">
        {{ t('pages.register.haveAccount') }}
        <a routerLink="/login" [queryParams]="returnUrl() ? { returnUrl: returnUrl() } : {}">{{
          t('pages.register.signIn')
        }}</a>
      </p>
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();

  protected readonly pending = signal(false);
  protected readonly summary = signal<SummaryItem[]>([]);
  protected readonly form = inject(NonNullableFormBuilder).group({
    displayName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, passwordPolicy]],
  });

  protected show(name: Field): boolean {
    return showErrors(this.form.controls[name]);
  }

  protected message(name: Field): string | null {
    return errorKey(this.form.controls[name]);
  }

  protected describedBy(name: Field, hintId?: string): string | null {
    const ids = [hintId, this.show(name) ? `${FIELDS[name].id}-error` : undefined].filter(Boolean);
    return ids.length ? ids.join(' ') : null;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.summary.set(summaryFor(this.form, FIELDS));
      return;
    }
    const { email, password, displayName } = this.form.getRawValue();
    this.pending.set(true);
    this.auth.register(email, password, displayName).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (error: unknown) => {
        this.pending.set(false);
        const apiError = toApiError(error);
        if (apiError.code === 'EMAIL_ALREADY_REGISTERED') {
          this.form.controls.email.setErrors({ server: 'emailTaken' });
        }
        applyServerErrors(this.form, apiError);
        const fieldItems = summaryFor(this.form, FIELDS);
        this.summary.set(fieldItems.length ? fieldItems : [{ key: `errors.${apiError.code}` }]);
      },
    });
  }
}
