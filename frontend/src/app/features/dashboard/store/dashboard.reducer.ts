import { createReducer, on } from '@ngrx/store';
import { DashboardActions } from './dashboard.actions';
import { initialDashboardState } from './dashboard.state';

export const dashboardReducer = createReducer(
  initialDashboardState,
  on(DashboardActions.loadStats, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(DashboardActions.loadStatsSuccess, (state, { stats }) => ({
    ...state,
    stats,
    loading: false
  })),
  on(DashboardActions.loadStatsFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  }))
);