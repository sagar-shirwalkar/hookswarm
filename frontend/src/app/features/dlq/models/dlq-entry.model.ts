export interface DlqEntry {
  id: string;
  deliveryTaskId: string;
  eventId: string;
  subscriptionId: string;
  totalAttempts: number;
  lastError: string | null;
  deadAt: string; // ISO datetime
}