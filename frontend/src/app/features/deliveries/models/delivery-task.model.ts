export interface DeliveryTask {
  id: string;
  eventId: string;
  subscriptionId: string;
  status: 'PENDING' | 'IN_FLIGHT' | 'DELIVERED' | 'FAILED' | 'DEAD';
  attemptCount: number;
  nextAttemptAt: string | null; // ISO datetime
  createdAt: string;
  updatedAt: string;
}