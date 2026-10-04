export type Role = 'CITIZEN' | 'MODERATOR' | 'CITY_OPERATOR' | 'ADMIN';

export const ROLES: readonly Role[] = ['CITIZEN', 'MODERATOR', 'CITY_OPERATOR', 'ADMIN'];

export interface Team {
  id: string;
  name: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  team: Team | null;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserProfile;
}
