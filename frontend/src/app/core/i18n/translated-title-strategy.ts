import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { take } from 'rxjs';

/**
 * Sets the document title from a translation key in the route's `title`.
 * A distinct title per page is what screen readers announce after navigation.
 */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const key = this.buildTitle(snapshot);
    if (!key) {
      this.transloco
        .selectTranslate('app.name')
        .pipe(take(1))
        .subscribe((appName) => this.title.setTitle(appName));
      return;
    }
    this.transloco
      .selectTranslate([key, 'app.name'])
      .pipe(take(1))
      .subscribe(([page, appName]: string[]) => this.title.setTitle(`${page} | ${appName}`));
  }
}
