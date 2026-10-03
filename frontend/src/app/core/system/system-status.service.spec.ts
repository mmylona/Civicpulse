import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SystemStatusService } from './system-status.service';

describe('SystemStatusService', () => {
  let service: SystemStatusService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SystemStatusService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts in the checking state', () => {
    expect(service.status()).toEqual({ state: 'checking' });
  });

  it('reports online with the backend version when the API answers', () => {
    service.check();
    http.expectOne('/api/v1/system/info').flush({
      name: 'CivicPulse',
      version: '0.1.0',
      apiVersion: 'v1',
      serverTime: '2026-10-03T14:32:07Z',
    });

    expect(service.status()).toEqual({ state: 'online', version: '0.1.0' });
  });

  it('reports offline when the API cannot be reached', () => {
    service.check();
    http.expectOne('/api/v1/system/info').error(new ProgressEvent('error'));

    expect(service.status()).toEqual({ state: 'offline' });
  });

  it('reports offline when the API answers with a server error', () => {
    service.check();
    http.expectOne('/api/v1/system/info').flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(service.status()).toEqual({ state: 'offline' });
  });
});
