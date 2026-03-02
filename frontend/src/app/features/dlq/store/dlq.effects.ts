import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { DlqActions } from './dlq.actions';
import { DlqService } from '../dlq.service';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable()
export class DlqEffects {
  private actions$ = inject(Actions);
  private service = inject(DlqService);

  loadEntries$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DlqActions.loadEntries),
      mergeMap(({ page, size }) =>
        this.service.getDeadLetters(page, size).pipe(
          map(response => DlqActions.loadEntriesSuccess({ response })),
          catchError(error => of(DlqActions.loadEntriesFailure({ error: error.message })))
        )
      )
    )
  );

  replayEntry$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DlqActions.replayEntry),
      mergeMap(({ id }) =>
        this.service.replay(id).pipe(
          map(() => DlqActions.replayEntrySuccess({ id })),
          catchError(error => of(DlqActions.replayEntryFailure({ error: error.message })))
        )
      )
    )
  );
}