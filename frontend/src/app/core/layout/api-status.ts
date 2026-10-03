import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { SystemStatusService } from '../system/system-status.service';

/**
 * Footer line that tells people whether the city service is reachable.
 * It is a polite live region, so a change (e.g. going offline) is announced once.
 */
@Component({
  selector: 'cp-api-status',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="api-status" role="status" aria-live="polite" *transloco="let t">
      @switch (status().state) {
        @case ('checking') {
          <span class="dot dot--checking" aria-hidden="true"></span>
          <span>{{ t('status.checking') }}</span>
        }
        @case ('online') {
          <span class="dot dot--online" aria-hidden="true"></span>
          <span>{{ t('status.online', { version: onlineVersion() }) }}</span>
        }
        @case ('offline') {
          <span class="dot dot--offline" aria-hidden="true"></span>
          <span>{{ t('status.offline') }}</span>
          <button type="button" class="retry" (click)="retry()">{{ t('status.retry') }}</button>
        }
      }
    </div>
  `,
  styles: `
    .api-status {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--cp-space-2);
      font-size: var(--cp-step--1);
      color: var(--cp-ink-muted);
    }
    .dot {
      inline-size: 0.625rem;
      block-size: 0.625rem;
      border-radius: 50%;
      flex: none;
    }
    /* Keep the dot beside its message on narrow screens; only the button wraps. */
    .dot + span {
      flex: 1 1 14rem;
    }
    .dot--checking {
      border: 2px solid var(--cp-ink-muted);
    }
    .dot--online {
      background: var(--cp-ok);
    }
    .dot--offline {
      background: var(--cp-danger);
    }
    .retry {
      background: none;
      border: 1px solid var(--cp-teal);
      color: var(--cp-teal);
      border-radius: var(--cp-radius-control);
      padding: var(--cp-space-1) var(--cp-space-3);
      cursor: pointer;
    }
    .retry:hover {
      background: var(--cp-teal);
      color: var(--cp-surface);
    }
  `,
})
export class ApiStatus implements OnInit {
  private readonly systemStatus = inject(SystemStatusService);

  protected readonly status = this.systemStatus.status;

  protected onlineVersion(): string {
    const current = this.status();
    return current.state === 'online' ? current.version : '';
  }

  ngOnInit(): void {
    this.systemStatus.check();
  }

  protected retry(): void {
    this.systemStatus.check();
  }
}
