import { createFeatureSelector, createSelector } from '@ngrx/store';
import { SubscriptionsState } from './subscriptions.state';

export const selectSubscriptionsState = createFeatureSelector<SubscriptionsState>('subscriptions');
export const selectAllSubscriptions = createSelector(selectSubscriptionsState, state => state.subscriptions || []);
export const selectSelectedSubscription = createSelector(selectSubscriptionsState, state => state.selectedSubscription);
export const selectSubscriptionsLoading = createSelector(selectSubscriptionsState, state => state.loading);
export const selectSubscriptionsError = createSelector(selectSubscriptionsState, state => state.error);