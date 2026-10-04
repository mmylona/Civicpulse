import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'cp-forbidden-page',
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t">
      <h1>{{ t('pages.forbidden.heading') }}</h1>
      <p>{{ t('pages.forbidden.body') }}</p>
      <a routerLink="/map">{{ t('pages.notFound.action') }}</a>
    </ng-container>
  `,
})
export class ForbiddenPage {}
