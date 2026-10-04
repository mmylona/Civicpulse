import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  input,
  viewChild,
} from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { SummaryItem } from './form-errors';

@Component({
  selector: 'cp-error-summary',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length) {
      <div class="error-summary" role="alert" tabindex="-1" #box *transloco="let t">
        <h2 class="error-summary__title">{{ t(heading()) }}</h2>
        <ul class="error-summary__list">
          @for (item of items(); track $index) {
            <li>
              @if (item.fieldId) {
                <a [href]="'#' + item.fieldId" (click)="focusField($event, item.fieldId)">
                  @if (item.label) {
                    <span class="error-summary__field">{{ t(item.label) }}:</span>
                  }
                  {{ t(item.key) }}
                </a>
              } @else {
                {{ t(item.key) }}
              }
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: `
    .error-summary {
      margin-block-end: var(--cp-space-4);
      padding: var(--cp-space-3) var(--cp-space-4);
      border-inline-start: 5px solid var(--cp-danger);
      background: var(--cp-surface);
    }
    .error-summary:focus-visible {
      outline-offset: 2px;
    }
    .error-summary__title {
      font-size: var(--cp-step-1);
      margin-block-end: var(--cp-space-2);
    }
    .error-summary__list {
      margin: 0;
      padding-inline-start: var(--cp-space-4);
    }
    .error-summary a {
      color: var(--cp-danger);
      font-weight: 650;
    }
    .error-summary__field {
      font-weight: 750;
    }
  `,
})
export class ErrorSummary {
  readonly items = input.required<SummaryItem[]>();
  readonly heading = input('forms.summaryHeading');

  private readonly box = viewChild<ElementRef<HTMLElement>>('box');

  constructor() {
    afterRenderEffect(() => {
      if (this.items().length) {
        this.box()?.nativeElement.focus();
      }
    });
  }

  protected focusField(event: Event, fieldId: string): void {
    const field = document.getElementById(fieldId);
    if (field) {
      event.preventDefault();
      field.focus();
    }
  }
}
