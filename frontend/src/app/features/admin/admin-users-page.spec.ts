import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { apiError } from '../../../testing/auth';
import { provideTestTranslations } from '../../../testing/translations';
import { AdminUser } from './admin-users.service';
import { AdminUsersPage } from './admin-users-page';

const ada: AdminUser = {
  id: 'u-1',
  email: 'ada@example.org',
  displayName: 'Ada Citizen',
  roles: ['CITIZEN'],
  team: null,
  enabled: true,
  locked: false,
  lastLoginAt: null,
  createdAt: '2026-10-01T09:00:00Z',
};

const page = (content: AdminUser[], totalPages = 1) => ({
  content,
  page: { number: 0, size: 20, totalElements: content.length, totalPages },
});

describe('AdminUsersPage', () => {
  let fixture: ComponentFixture<AdminUsersPage>;
  let http: HttpTestingController;
  let element: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AdminUsersPage, provideTestTranslations()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminUsersPage);
    element = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/teams').flush([{ id: 't-1', name: 'Street Lighting' }]);
  });

  afterEach(() => http.verify());

  async function loadUsers(users: AdminUser[], totalPages = 1): Promise<void> {
    http.expectOne((r) => r.url === '/api/v1/admin/users').flush(page(users, totalPages));
    await fixture.whenStable();
  }

  it('renders users', async () => {
    await loadUsers([ada, { ...ada, id: 'u-2', displayName: 'Lee Locked', locked: true }]);

    const rows = element.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Ada Citizen');
    expect(rows[0].textContent).toContain('Citizen');
    expect(rows[0].textContent).toContain('Active');
    expect(rows[1].textContent).toContain('Locked');
    expect(element.textContent).toContain('2 users');
  });

  it('search sends query and role', async () => {
    await loadUsers([ada]);
    const query = element.querySelector<HTMLInputElement>('#user-query')!;
    query.value = ' ada ';
    query.dispatchEvent(new Event('input'));
    const role = element.querySelector<HTMLSelectElement>('#user-role')!;
    role.value = 'ADMIN';
    role.dispatchEvent(new Event('change'));
    element
      .querySelector<HTMLFormElement>('form[role="search"]')!
      .dispatchEvent(new Event('submit'));

    const request = http.expectOne((r) => r.url === '/api/v1/admin/users');
    expect(request.request.params.get('q')).toBe('ada');
    expect(request.request.params.get('role')).toBe('ADMIN');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([]));
    await fixture.whenStable();

    expect(element.textContent).toContain('No users match this search.');
  });

  it('saves changed roles', async () => {
    await loadUsers([ada]);
    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    await fixture.whenStable();

    const moderator = element.querySelector<HTMLInputElement>('#role-MODERATOR')!;
    moderator.click();
    element.querySelector<HTMLButtonElement>('.edit-panel button[type="submit"]')!.click();

    const request = http.expectOne('/api/v1/admin/users/u-1/roles');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ roles: ['CITIZEN', 'MODERATOR'] });
    request.flush({ ...ada, roles: ['CITIZEN', 'MODERATOR'] });
    await fixture.whenStable();

    expect(element.querySelector('.edit-panel')).toBeNull();
    expect(element.querySelector('tbody tr')?.textContent).toContain('Moderator');
    expect(element.textContent).toContain('Saved changes to Ada Citizen');
  });

  it('requires at least one role', async () => {
    await loadUsers([ada]);
    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    await fixture.whenStable();

    element.querySelector<HTMLInputElement>('#role-CITIZEN')!.click();
    element.querySelector<HTMLButtonElement>('.edit-panel button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(element.querySelector('.edit-panel [role="alert"]')?.textContent).toContain(
      'Choose at least one role',
    );
  });

  it('shows error when save fails', async () => {
    await loadUsers([ada]);
    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    await fixture.whenStable();

    element
      .querySelector<HTMLInputElement>('.edit-panel input[formcontrolname="enabled"]')!
      .click();
    element.querySelector<HTMLButtonElement>('.edit-panel button[type="submit"]')!.click();
    const refused = apiError(409, 'CANNOT_DISABLE_OWN_ACCOUNT');
    http.expectOne('/api/v1/admin/users/u-1/status').flush(refused.body, refused);
    await loadUsers([ada]);

    expect(element.querySelector('.edit-panel [role="alert"]')?.textContent).toContain(
      "You can't disable your own account",
    );
  });

  it('retry after load error', async () => {
    http.expectOne((r) => r.url === '/api/v1/admin/users').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Users could not be loaded.',
    );
    element.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await loadUsers([ada]);
    expect(element.querySelectorAll('tbody tr').length).toBe(1);
  });
});
