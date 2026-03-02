import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { DashboardService } from '../dashboard.service';
import { DashboardActions } from './dashboard.actions';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable()
export class DashboardEffects {
  private actions$ = inject(Actions);
  private dashboardService = inject(DashboardService);

  loadStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DashboardActions.loadStats),
      mergeMap(() =>
        this.dashboardService.getStats().pipe(
          map(stats => DashboardActions.loadStatsSuccess({ stats })),
          catchError(error => of(DashboardActions.loadStatsFailure({ error: error.message })))
        )
      )
    )
  );
}