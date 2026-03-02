import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { SubscriptionService } from '../subscription.service';
import { SubscriptionsActions } from './subscriptions.actions';
import { catchError, map, mergeMap, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable()
export class SubscriptionsEffects {
  private actions$ = inject(Actions);
  private service = inject(SubscriptionService);

  loadAll$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SubscriptionsActions.loadSubscriptions),
      mergeMap(() =>
        this.service.getAll().pipe(
          map(subscriptions => SubscriptionsActions.loadSubscriptionsSuccess({ subscriptions })),
          catchError(error => of(SubscriptionsActions.loadSubscriptionsFailure({ error: error.message })))
        )
      )
    )
  );

  loadOne$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SubscriptionsActions.loadSubscription),
      mergeMap(({ id }) =>
        this.service.getById(id).pipe(
          map(subscription => SubscriptionsActions.loadSubscriptionSuccess({ subscription })),
          catchError(error => of(SubscriptionsActions.loadSubscriptionFailure({ error: error.message })))
        )
      )
    )
  );

  create$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SubscriptionsActions.createSubscription),
      mergeMap(({ data }) =>
        this.service.create(data).pipe(
          map(subscription => SubscriptionsActions.createSubscriptionSuccess({ subscription })),
          catchError(error => of(SubscriptionsActions.createSubscriptionFailure({ error: error.message })))
        )
      )
    )
  );

  update$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SubscriptionsActions.updateSubscription),
      mergeMap(({ id, data }) =>
        this.service.update(id, data).pipe(
          map(subscription => SubscriptionsActions.updateSubscriptionSuccess({ subscription })),
          catchError(error => of(SubscriptionsActions.updateSubscriptionFailure({ error: error.message })))
        )
      )
    )
  );

  delete$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SubscriptionsActions.deleteSubscription),
      mergeMap(({ id }) =>
        this.service.delete(id).pipe(
          map(() => SubscriptionsActions.deleteSubscriptionSuccess({ id })),
          catchError(error => of(SubscriptionsActions.deleteSubscriptionFailure({ error: error.message })))
        )
      )
    )
  );
}