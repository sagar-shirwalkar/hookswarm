import { Subscription } from '../models/subscription.model';

export interface SubscriptionsState {
  subscriptions: Subscription[];
  selectedSubscription: Subscription | null;
  loading: boolean;
  error: string | null;
}

export const initialSubscriptionsState: SubscriptionsState = {
  subscriptions: [],
  selectedSubscription: null,
  loading: false,
  error: null
};