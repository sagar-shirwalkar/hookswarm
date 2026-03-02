import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { DeliveriesActions } from './deliveries.actions';
import { DeliveryService } from '../delivery.service';
import { catchError, map, mergeMap, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable()
export class DeliveriesEffects {
  private actions$ = inject(Actions);
  private service = inject(DeliveryService);

  loadByEvent$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DeliveriesActions.loadTasksByEvent),
      mergeMap(({ eventId }) =>
        this.service.getTasksByEventId(eventId).pipe(
          map(tasks => DeliveriesActions.loadTasksByEventSuccess({ tasks })),
          catchError(error => of(DeliveriesActions.loadTasksByEventFailure({ error: error.message })))
        )
      )
    )
  );

  loadBySubscription$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DeliveriesActions.loadTasksBySubscription),
      mergeMap(({ subscriptionId, page, size }) =>
        this.service.getTasksBySubscriptionId(subscriptionId, page, size).pipe(
          map(response => DeliveriesActions.loadTasksBySubscriptionSuccess({ response })),
          catchError(error => of(DeliveriesActions.loadTasksBySubscriptionFailure({ error: error.message })))
        )
      )
    )
  );

  loadTask$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DeliveriesActions.loadTask),
      mergeMap(({ id }) =>
        this.service.getTask(id).pipe(
          map(task => DeliveriesActions.loadTaskSuccess({ task })),
          catchError(error => of(DeliveriesActions.loadTaskFailure({ error: error.message })))
        )
      )
    )
  );

  loadAttempts$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DeliveriesActions.loadAttempts),
      mergeMap(({ taskId }) =>
        this.service.getAttempts(taskId).pipe(
          map(attempts => DeliveriesActions.loadAttemptsSuccess({ attempts })),
          catchError(error => of(DeliveriesActions.loadAttemptsFailure({ error: error.message })))
        )
      )
    )
  );

  retryTask$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DeliveriesActions.retryTask),
      switchMap(({ id }) =>
        this.service.retryTask(id).pipe(
          map(task => DeliveriesActions.retryTaskSuccess({ task })),
          catchError(error => of(DeliveriesActions.retryTaskFailure({ error: error.message })))
        )
      )
    )
  );
}