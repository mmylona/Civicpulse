import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { API_BASE } from '../api-base';

export interface SystemInfo {
  name: string;
  version: string;
  apiVersion: string;
  serverTime: string;
}

export type ApiStatus =
  | { readonly state: 'checking' }
  | { readonly state: 'online'; readonly version: string }
  | { readonly state: 'offline' };

/** Tracks whether the backend is reachable, so the UI can say so instead of failing silently. */
@Injectable({ providedIn: 'root' })
export class SystemStatusService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<ApiStatus>({ state: 'checking' });

  readonly status = this.current.asReadonly();

  check(): void {
    this.current.set({ state: 'checking' });
    this.http.get<SystemInfo>(`${API_BASE}/system/info`).subscribe({
      next: (info) => this.current.set({ state: 'online', version: info.version }),
      error: () => this.current.set({ state: 'offline' }),
    });
  }
}
