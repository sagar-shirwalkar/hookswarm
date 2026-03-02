import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { DeliveryTask } from '../models/delivery-task.model';
import { DeliveryAttempt } from '../models/delivery-attempt.model';
import { PagedResponse } from '../../../shared/models';

export const DeliveriesActions = createActionGroup({
  source: 'Deliveries',
  events: {
    // Load by event ID
    'Load Tasks By Event': props<{ eventId: string }>(),
    'Load Tasks By Event Success': props<{ tasks: DeliveryTask[] }>(),
    'Load Tasks By Event Failure': props<{ error: string }>(),

    // Load by subscription ID (paginated)
    'Load Tasks By Subscription': props<{ subscriptionId: string; page: number; size: number }>(),
    'Load Tasks By Subscription Success': props<{ response: PagedResponse<DeliveryTask> }>(),
    'Load Tasks By Subscription Failure': props<{ error: string }>(),

    // Load single task
    'Load Task': props<{ id: string }>(),
    'Load Task Success': props<{ task: DeliveryTask }>(),
    'Load Task Failure': props<{ error: string }>(),

    // Load attempts for a task
    'Load Attempts': props<{ taskId: string }>(),
    'Load Attempts Success': props<{ attempts: DeliveryAttempt[] }>(),
    'Load Attempts Failure': props<{ error: string }>(),

    // Retry a task
    'Retry Task': props<{ id: string }>(),
    'Retry Task Success': props<{ task: DeliveryTask }>(),
    'Retry Task Failure': props<{ error: string }>(),

    // Clear selected task (e.g., when closing modal)
    'Clear Selected Task': emptyProps(),
    'Clear Attempts': emptyProps(),
  }
});