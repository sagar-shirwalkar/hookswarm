import { createFeatureSelector, createSelector } from '@ngrx/store';
import { DeliveriesState } from './deliveries.state';

export const selectDeliveriesState = createFeatureSelector<DeliveriesState>('deliveries');
export const selectAllTasks = createSelector(selectDeliveriesState, state => state.tasks || []);
export const selectSelectedTask = createSelector(selectDeliveriesState, state => state.selectedTask);
export const selectAttempts = createSelector(selectDeliveriesState, state => state.attempts || []);
export const selectTotalElements = createSelector(selectDeliveriesState, state => state.totalElements);
export const selectDeliveriesLoading = createSelector(selectDeliveriesState, state => state.loading);
export const selectDeliveriesError = createSelector(selectDeliveriesState, state => state.error);