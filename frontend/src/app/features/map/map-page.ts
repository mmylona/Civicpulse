import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';

/**
 * Landing page. The interactive map (Leaflet, viewport-based loading, clustering) replaces
 * the placeholder region in Phase 5; the page structure and headings stay the same.
 */
@Component({
  selector: 'cp-map-page',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t; prefix: 'pages.map'">
      <h1>{{ t('heading') }}</h1>
      <p class="intro">{{ t('intro') }}</p>
      <section class="map-region" [attr.aria-label]="t('regionLabel')">
        <p class="empty">{{ t('empty') }}</p>
      </section>
    </ng-container>
  `,
  styles: `
    .intro {
      color: var(--cp-ink-muted);
      font-size: var(--cp-step-1);
    }
    .map-region {
      display: grid;
      place-items: center;
      min-block-size: clamp(18rem, 55vh, 36rem);
      border: 1px solid var(--cp-kerb);
      border-radius: var(--cp-radius-control);
      /* A survey grid: the map's footprint before tiles load. */
      background-color: var(--cp-surface);
      background-image:
        linear-gradient(var(--cp-kerb) 1px, transparent 1px),
        linear-gradient(90deg, var(--cp-kerb) 1px, transparent 1px);
      background-size: 4rem 4rem;
      background-position: -1px -1px;
    }
    .empty {
      margin: 0;
      padding: var(--cp-space-2) var(--cp-space-3);
      background: var(--cp-surface);
      border-radius: var(--cp-radius-control);
      color: var(--cp-ink-muted);
    }
  `,
})
export class MapPage {}
