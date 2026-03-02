import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { CreateEventRequest, EventResponse } from '../models/event.models';

export const EventsActions = createActionGroup({
  source: 'Events',
  events: {
    'Create Event': props<{ request: CreateEventRequest }>(),
    'Create Event Success': props<{ event: EventResponse }>(),
    'Create Event Failure': props<{ error: string }>(),
    'Reset Last Created': emptyProps()
  }
});