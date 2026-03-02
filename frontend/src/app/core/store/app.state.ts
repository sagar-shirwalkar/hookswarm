import { AuthState } from '../../features/auth/store/auth.state';
import { DashboardState } from '../../features/dashboard/store/dashboard.state';
import { EventsState } from '../../features/events/store/events.state';
import { SubscriptionsState } from '../../features/subscriptions/store/subscriptions.state';

export interface AppState {
  auth: AuthState;
  dashboard: DashboardState;
  events: EventsState;
  subscriptions: SubscriptionsState;
}