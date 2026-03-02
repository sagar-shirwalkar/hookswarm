import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { CreateSubscriptionRequest, UpdateSubscriptionRequest, Subscription } from '../models/subscription.model';
export const SubscriptionsActions = createActionGroup({
  source: 'Subscriptions',
  events: {
    'Load Subscriptions': emptyProps(),
    'Load Subscriptions Success': props<{ subscriptions: Subscription[] }>(),
    'Load Subscriptions Failure': props<{ error: string }>(),

    'Load Subscription': props<{ id: string }>(),
    'Load Subscription Success': props<{ subscription: Subscription }>(),
    'Load Subscription Failure': props<{ error: string }>(),

    'Create Subscription': props<{ data: CreateSubscriptionRequest }>(),
    'Create Subscription Success': props<{ subscription: Subscription }>(),
    'Create Subscription Failure': props<{ error: string }>(),

    'Update Subscription': props<{ id: string; data: UpdateSubscriptionRequest }>(),
    'Update Subscription Success': props<{ subscription: Subscription }>(),
    'Update Subscription Failure': props<{ error: string }>(),

    'Delete Subscription': props<{ id: string }>(),
    'Delete Subscription Success': props<{ id: string }>(),
    'Delete Subscription Failure': props<{ error: string }>(),

    'Clear Selected Subscription': emptyProps(),
  }
});