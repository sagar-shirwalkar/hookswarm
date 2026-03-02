import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { DashboardStats } from '../dashboard-stats.model';

export const DashboardActions = createActionGroup({
  source: 'Dashboard',
  events: {
    'Load Stats': emptyProps(),
    'Load Stats Success': props<{ stats: DashboardStats }>(),
    'Load Stats Failure': props<{ error: string }>()
  }
});