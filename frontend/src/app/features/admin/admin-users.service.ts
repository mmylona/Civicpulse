import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api-base';
import { Role, Team } from '../../core/auth/auth.models';
import { Page } from '../../core/http/page';

export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  team: Team | null;
  enabled: boolean;
  locked: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface UserSearch {
  query: string;
  role: Role | '';
  page: number;
}

const ADMIN = `${API_BASE}/admin`;

@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private readonly http = inject(HttpClient);

  search({ query, role, page }: UserSearch): Observable<Page<AdminUser>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<Page<AdminUser>>(`${ADMIN}/users`, { params });
  }

  teams(): Observable<Team[]> {
    return this.http.get<Team[]>(`${ADMIN}/teams`);
  }

  changeRoles(id: string, roles: Role[]): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${ADMIN}/users/${id}/roles`, { roles });
  }

  changeStatus(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${ADMIN}/users/${id}/status`, { enabled });
  }

  changeTeam(id: string, teamId: string | null): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${ADMIN}/users/${id}/team`, { teamId });
  }
}
