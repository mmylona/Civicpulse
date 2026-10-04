import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from '../../public/i18n/en.json';

export function provideTestTranslations(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { en },
    translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
    preloadLangs: true,
    ...options,
  });
}
