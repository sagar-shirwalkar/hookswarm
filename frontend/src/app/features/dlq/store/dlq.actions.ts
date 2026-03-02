import { createActionGroup, props } from '@ngrx/store';
import { DlqEntry } from '../models/dlq-entry.model';
import { PagedResponse } from '../../../shared/models/paged-response.model';

export const DlqActions = createActionGroup({
  source: 'DLQ',
  events: {
    'Load Entries': props<{ page: number; size: number }>(),
    'Load Entries Success': props<{ response: PagedResponse<DlqEntry> }>(),
    'Load Entries Failure': props<{ error: string }>(),

    'Replay Entry': props<{ id: string }>(),
    'Replay Entry Success': props<{ id: string }>(),
    'Replay Entry Failure': props<{ error: string }>(),
  }
});