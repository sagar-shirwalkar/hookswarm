import { EventResponse } from '../models/event.models';

export interface EventsState {
  loading: boolean;
  error: string | null;
  lastCreatedEvent: EventResponse | null;
}

export const initialEventsState: EventsState = {
  loading: false,
  error: null,
  lastCreatedEvent: null
};