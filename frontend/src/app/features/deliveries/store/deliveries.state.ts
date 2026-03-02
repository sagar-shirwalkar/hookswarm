import { DeliveryTask } from '../models/delivery-task.model';
import { DeliveryAttempt } from '../models/delivery-attempt.model';

export interface DeliveriesState {
  tasks: DeliveryTask[];
  selectedTask: DeliveryTask | null;
  attempts: DeliveryAttempt[];
  totalElements: number;
  loading: boolean;
  error: string | null;
}

export const initialDeliveriesState: DeliveriesState = {
  tasks: [],
  selectedTask: null,
  attempts: [],
  totalElements: 0,
  loading: false,
  error: null
};