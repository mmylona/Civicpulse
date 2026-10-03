import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideTestTranslations } from '../../../testing/translations';
import { ApiStatus } from './api-status';

describe('ApiStatus', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApiStatus, provideTestTranslations()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render() {
    const fixture = TestBed.createComponent(ApiStatus);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  it('announces the connected version in a live region', async () => {
    const fixture = await render();
    http
      .expectOne('/api/v1/system/info')
      .flush({ name: 'CivicPulse', version: '0.1.0', apiVersion: 'v1', serverTime: '' });
    await fixture.whenStable();

    const region: HTMLElement = fixture.nativeElement.querySelector('[role="status"]');
    expect(region.getAttribute('aria-live')).toBe('polite');
    expect(region.textContent).toContain('City service connected, version 0.1.0');
  });

  it('explains an outage and lets people retry', async () => {
    const fixture = await render();
    http.expectOne('/api/v1/system/info').error(new ProgressEvent('error'));
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain("The city service can't be reached right now.");

    const retry = element.querySelector('button');
    expect(retry?.textContent?.trim()).toBe('Try again');
    retry!.click();
    http
      .expectOne('/api/v1/system/info')
      .flush({ name: 'CivicPulse', version: '0.1.0', apiVersion: 'v1', serverTime: '' });
    await fixture.whenStable();

    expect(element.textContent).toContain('City service connected');
    expect(element.querySelector('button')).toBeNull();
  });
});
