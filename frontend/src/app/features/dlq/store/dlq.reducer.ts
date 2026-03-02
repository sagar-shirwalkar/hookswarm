import { createReducer, on } from '@ngrx/store';
import { DlqActions } from './dlq.actions';
import { initialDlqState } from './dlq.state';

export const dlqReducer = createReducer(
  initialDlqState,
  on(DlqActions.loadEntries, state => ({ ...state, loading: true, error: null })),
  on(DlqActions.loadEntriesSuccess, (state, { response }) => ({
    ...state,
    entries: response.content,
    totalElements: response.totalElements,
    loading: false
  })),
  on(DlqActions.loadEntriesFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),
  on(DlqActions.replayEntrySuccess, (state, { id }) => ({
    ...state,
    entries: state.entries.filter(e => e.id !== id),
    totalElements: state.totalElements - 1
  }))
);