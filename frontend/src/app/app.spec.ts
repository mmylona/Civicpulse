import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { TitleStrategy, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { App } from './app';
import { routes } from './app.routes';
import { TranslatedTitleStrategy } from './core/i18n/translated-title-strategy';
import { provideTestTranslations } from '../testing/translations';

describe('App shell', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App, provideTestTranslations()],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
      ],
    });
  });

  afterEach(() => {
    // The footer checks the API once on start-up; the shell tests do not care about it.
    TestBed.inject(HttpTestingController).match('/api/v1/system/info');
  });

  it('offers a skip link to the main content as the first focusable element', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;

    const first = element.querySelector('a');
    expect(first?.textContent?.trim()).toBe('Skip to main content');
    expect(first?.getAttribute('href')).toBe('#main-content');
    expect(element.querySelector('main#main-content')).not.toBeNull();
  });

  it('labels the primary navigation and marks the current page', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/map');
    const shell = TestBed.createComponent(App);
    await shell.whenStable();

    const nav: HTMLElement = shell.nativeElement.querySelector('nav');
    expect(nav.getAttribute('aria-label')).toBe('Main');
    expect(nav.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('Map');
  });

  it('sends the empty path to the map with a translated page title', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');

    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('City map');
    expect(TestBed.inject(Title).getTitle()).toBe('City map | CivicPulse');
  });

  it('shows a helpful not-found page for unknown addresses', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/no/such/page');

    const page = harness.routeNativeElement!;
    expect(page.querySelector('h1')?.textContent).toBe("This page doesn't exist");
    expect(page.querySelector('a')?.getAttribute('href')).toBe('/map');
    expect(TestBed.inject(Title).getTitle()).toBe('Page not found | CivicPulse');
  });
});
