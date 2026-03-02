import { createFeatureSelector, createSelector } from '@ngrx/store';
import { DlqState } from './dlq.state';

export const selectDlqState = createFeatureSelector<DlqState>('dlq');
export const selectDlqEntries = createSelector(selectDlqState, state => state.entries || []);
export const selectDlqTotalElements = createSelector(selectDlqState, state => state.totalElements);
export const selectDlqLoading = createSelector(selectDlqState, state => state.loading);
export const selectDlqError = createSelector(selectDlqState, state => state.error);