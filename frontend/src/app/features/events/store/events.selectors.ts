import { createFeatureSelector, createSelector } from '@ngrx/store';
import { EventsState } from './events.state';

export const selectEventsState = createFeatureSelector<EventsState>('events');
export const selectEventsLoading = createSelector(selectEventsState, state => state.loading);
export const selectEventsError = createSelector(selectEventsState, state => state.error);
export const selectLastCreatedEvent = createSelector(selectEventsState, state => state.lastCreatedEvent);