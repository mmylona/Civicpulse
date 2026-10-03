import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'cp-not-found-page',
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t; prefix: 'pages.notFound'">
      <h1>{{ t('heading') }}</h1>
      <p>{{ t('body') }}</p>
      <a routerLink="/map">{{ t('action') }}</a>
    </ng-container>
  `,
})
export class NotFoundPage {}
