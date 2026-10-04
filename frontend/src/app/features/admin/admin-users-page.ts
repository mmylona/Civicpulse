import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
} from '@angular/forms';
import { TranslocoDirective } from '@jsverse/transloco';
import { Observable, Subject, catchError, concat, last, map, of, startWith, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ROLES, Role, Team } from '../../core/auth/auth.models';
import { ApiError, toApiError } from '../../core/http/api-error';
import { Page } from '../../core/http/page';
import { ErrorSummary } from '../../shared/forms/error-summary';
import { SummaryItem } from '../../shared/forms/form-errors';
import { AdminUser, AdminUsersService, UserSearch } from './admin-users.service';

type LoadState =
  | { status: 'loading' }
  | { status: 'loaded'; page: Page<AdminUser> }
  | { status: 'failed'; error: ApiError };

@Component({
  selector: 'cp-admin-users-page',
  imports: [ReactiveFormsModule, TranslocoDirective, DatePipe, ErrorSummary],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-users-page.html',
  styleUrl: './admin-users-page.scss',
})
export class AdminUsersPage implements OnInit {
  private readonly users = inject(AdminUsersService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly searches = new Subject<UserSearch>();
  private readonly editHeading = viewChild<ElementRef<HTMLElement>>('editHeading');

  protected readonly roles = ROLES;
  protected readonly state = signal<LoadState>({ status: 'loading' });
  protected readonly page = computed(() => {
    const state = this.state();
    return state.status === 'loaded' ? state.page : null;
  });
  protected readonly teams = signal<Team[]>([]);
  protected readonly current = signal<UserSearch>({ query: '', role: '', page: 0 });
  protected readonly editing = signal<AdminUser | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveErrors = signal<SummaryItem[]>([]);
  protected readonly announcement = signal('');

  protected readonly filters = this.fb.group({
    query: [''],
    role: this.fb.control<Role | ''>(''),
  });

  protected readonly editForm = new FormGroup({
    roles: new FormGroup(
      Object.fromEntries(
        ROLES.map((role) => [role, new FormControl(false, { nonNullable: true })]),
      ) as Record<Role, FormControl<boolean>>,
    ),
    teamId: new FormControl('', { nonNullable: true }),
    enabled: new FormControl(true, { nonNullable: true }),
  });

  ngOnInit(): void {
    this.searches
      .pipe(
        switchMap((search) =>
          this.users.search(search).pipe(
            map((page): LoadState => ({ status: 'loaded', page })),
            catchError((error: unknown) =>
              of<LoadState>({ status: 'failed', error: toApiError(error) }),
            ),
            startWith<LoadState>({ status: 'loading' }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => this.state.set(state));

    this.users
      .teams()
      .pipe(
        catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((teams) => this.teams.set(teams));

    this.load();
  }

  protected applyFilters(): void {
    const { query, role } = this.filters.getRawValue();
    this.current.set({ query, role, page: 0 });
    this.load();
  }

  protected goToPage(page: number): void {
    this.current.update((search) => ({ ...search, page }));
    this.load();
  }

  protected load(): void {
    this.searches.next(this.current());
  }

  protected isSelf(user: AdminUser): boolean {
    return this.auth.user()?.id === user.id;
  }

  protected edit(user: AdminUser): void {
    this.editing.set(user);
    this.saveErrors.set([]);
    this.editForm.reset({
      roles: Object.fromEntries(ROLES.map((role) => [role, user.roles.includes(role)])) as Record<
        Role,
        boolean
      >,
      teamId: user.team?.id ?? '',
      enabled: user.enabled,
    });
    setTimeout(() => this.editHeading()?.nativeElement.focus());
  }

  protected cancelEdit(): void {
    this.editing.set(null);
  }

  protected save(): void {
    const user = this.editing();
    if (!user) {
      return;
    }
    const value = this.editForm.getRawValue();
    const roles = ROLES.filter((role) => value.roles[role]);
    if (roles.length === 0) {
      this.saveErrors.set([{ key: 'pages.adminUsers.errors.noRole', fieldId: 'role-CITIZEN' }]);
      return;
    }

    const changes: Observable<AdminUser>[] = [];
    if (!sameRoles(roles, user.roles)) {
      changes.push(this.users.changeRoles(user.id, roles));
    }
    if ((user.team?.id ?? '') !== value.teamId) {
      changes.push(this.users.changeTeam(user.id, value.teamId || null));
    }
    if (user.enabled !== value.enabled) {
      changes.push(this.users.changeStatus(user.id, value.enabled));
    }
    if (changes.length === 0) {
      this.editing.set(null);
      return;
    }

    this.saving.set(true);
    this.saveErrors.set([]);
    concat(...changes)
      .pipe(last())
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.editing.set(null);
          this.replaceRow(updated);
          this.announcement.set(updated.displayName);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.saveErrors.set([{ key: `errors.${toApiError(error).code}` }]);
          this.load();
        },
      });
  }

  private replaceRow(updated: AdminUser): void {
    this.state.update((state) =>
      state.status === 'loaded'
        ? {
            status: 'loaded',
            page: {
              ...state.page,
              content: state.page.content.map((user) => (user.id === updated.id ? updated : user)),
            },
          }
        : state,
    );
  }
}

function sameRoles(a: Role[], b: Role[]): boolean {
  return a.length === b.length && a.every((role) => b.includes(role));
}
