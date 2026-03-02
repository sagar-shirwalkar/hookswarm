import { createReducer, on } from '@ngrx/store';
import { EventsActions } from './events.actions';
import { initialEventsState } from './events.state';

export const eventsReducer = createReducer(
  initialEventsState,
  on(EventsActions.createEvent, state => ({
    ...state,
    loading: true,
    error: null
  })),
  on(EventsActions.createEventSuccess, (state, { event }) => ({
    ...state,
    loading: false,
    lastCreatedEvent: event
  })),
  on(EventsActions.createEventFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),
  on(EventsActions.resetLastCreated, state => ({
    ...state,
    lastCreatedEvent: null
  }))
);