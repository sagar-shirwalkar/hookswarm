import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { EventsService } from '../events.service';
import { EventsActions } from './events.actions';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable()
export class EventsEffects {
  private actions$ = inject(Actions);
  private eventsService = inject(EventsService);

  createEvent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(EventsActions.createEvent),
      mergeMap(({ request }) =>
        this.eventsService.createEvent(request).pipe(
          map(event => EventsActions.createEventSuccess({ event })),
          catchError(error => of(EventsActions.createEventFailure({ error: error.message })))
        )
      )
    )
  );
}