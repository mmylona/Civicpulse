import { TokenResponse, UserProfile } from '../app/core/auth/auth.models';

export const citizen: UserProfile = {
  id: '0192f0a0-0000-7000-8000-000000000001',
  email: 'ada@example.org',
  displayName: 'Ada Citizen',
  roles: ['CITIZEN'],
  team: null,
};

export const admin: UserProfile = {
  id: '0192f0a0-0000-7000-8000-000000000002',
  email: 'admin@example.org',
  displayName: 'Ali Admin',
  roles: ['ADMIN'],
  team: null,
};

export function tokenResponse(
  user: UserProfile = citizen,
  accessToken = 'access-1',
): TokenResponse {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, user };
}

export function apiError(status: number, code: string, errors: unknown[] = []) {
  return { status, statusText: 'Error', body: { status, code, message: '', traceId: 't', errors } };
}
