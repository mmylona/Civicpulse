import { HttpErrorResponse } from '@angular/common/http';

export interface FieldViolation {
  field: string;
  code: string;
  message: string;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  traceId?: string;
  errors: FieldViolation[];
}

const NETWORK_ERROR: ApiError = { status: 0, code: 'NETWORK_ERROR', message: '', errors: [] };

export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { ...NETWORK_ERROR };
  }
  if (error.status === 0) {
    return { ...NETWORK_ERROR };
  }
  const body = error.error as Partial<ApiError> | null;
  if (body && typeof body.code === 'string') {
    return {
      status: error.status,
      code: body.code,
      message: body.message ?? '',
      traceId: body.traceId,
      errors: Array.isArray(body.errors) ? body.errors : [],
    };
  }
  return { status: error.status, code: 'UNEXPECTED_ERROR', message: '', errors: [] };
}
