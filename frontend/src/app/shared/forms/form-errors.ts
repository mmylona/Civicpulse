import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ApiError } from '../../core/http/api-error';

export interface SummaryItem {
  key: string;
  fieldId?: string;
  label?: string;
}

export interface FieldRef {
  id: string;
  label: string;
}

const SERVER_CONSTRAINTS: Record<string, string> = {
  NotBlank: 'required',
  NotNull: 'required',
  NotEmpty: 'required',
  Email: 'email',
  ValidPassword: 'password',
  Size: 'tooLong',
};

const MESSAGE_ORDER = ['required', 'email', 'password', 'tooLong', 'server'];

export const passwordPolicy: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = String(control.value ?? '');
  if (value === '') {
    return null;
  }
  const characters = [...value].length;
  const bytes = new TextEncoder().encode(value).length;
  return characters >= 10 && bytes <= 72 && value.trim() !== '' ? null : { password: true };
};

export function errorKey(control: AbstractControl | null): string | null {
  const errors = control?.errors;
  if (!errors) {
    return null;
  }
  if (errors['server']) {
    return `forms.errors.${errors['server']}`;
  }
  if (errors['maxlength']) {
    return 'forms.errors.tooLong';
  }
  const first = MESSAGE_ORDER.find((name) => errors[name]);
  return first ? `forms.errors.${first}` : 'forms.errors.invalid';
}

export function showErrors(control: AbstractControl | null): boolean {
  return !!control && control.invalid && (control.touched || control.dirty);
}

export function applyServerErrors(form: FormGroup, error: ApiError): string[] {
  const unplaced: string[] = [];
  for (const violation of error.errors) {
    const control = form.get(violation.field);
    if (control) {
      control.setErrors({ server: SERVER_CONSTRAINTS[violation.code] ?? 'invalid' });
      control.markAsTouched();
    } else {
      unplaced.push(violation.code);
    }
  }
  return unplaced;
}

export function summaryFor(form: FormGroup, fields: Record<string, FieldRef>): SummaryItem[] {
  const items: SummaryItem[] = [];
  for (const [name, field] of Object.entries(fields)) {
    const key = errorKey(form.get(name));
    if (key) {
      items.push({ key, fieldId: field.id, label: field.label });
    }
  }
  return items;
}
