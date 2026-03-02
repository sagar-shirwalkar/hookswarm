import { createReducer, on } from '@ngrx/store';
import { SubscriptionsActions } from './subscriptions.actions';
import { initialSubscriptionsState } from './subscriptions.state';

export const subscriptionsReducer = createReducer(
  initialSubscriptionsState,
  // Load all
  on(SubscriptionsActions.loadSubscriptions, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(SubscriptionsActions.loadSubscriptionsSuccess, (state, { subscriptions }) => ({
    ...state,
    subscriptions,
    loading: false
  })),
  on(SubscriptionsActions.loadSubscriptionsFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Load single
  on(SubscriptionsActions.loadSubscription, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(SubscriptionsActions.loadSubscriptionSuccess, (state, { subscription }) => ({
    ...state,
    selectedSubscription: subscription,
    loading: false
  })),
  on(SubscriptionsActions.loadSubscriptionFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Create
  on(SubscriptionsActions.createSubscription, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(SubscriptionsActions.createSubscriptionSuccess, (state, { subscription }) => ({
    ...state,
    subscriptions: [...state.subscriptions, subscription],
    loading: false
  })),
  on(SubscriptionsActions.createSubscriptionFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Update
  on(SubscriptionsActions.updateSubscription, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(SubscriptionsActions.updateSubscriptionSuccess, (state, { subscription }) => ({
    ...state,
    subscriptions: state.subscriptions.map(s => s.id === subscription.id ? subscription : s),
    selectedSubscription: subscription,
    loading: false
  })),
  on(SubscriptionsActions.updateSubscriptionFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Delete
  on(SubscriptionsActions.deleteSubscription, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(SubscriptionsActions.deleteSubscriptionSuccess, (state, { id }) => ({
    ...state,
    subscriptions: state.subscriptions.filter(s => s.id !== id),
    selectedSubscription: state.selectedSubscription?.id === id ? null : state.selectedSubscription,
    loading: false
  })),
  on(SubscriptionsActions.deleteSubscriptionFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  on(SubscriptionsActions.clearSelectedSubscription, state => ({
    ...state,
    selectedSubscription: null
  }))
);