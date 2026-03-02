export interface DeliveryAttempt {
  id: string;
  deliveryTaskId: string;
  attemptNumber: number;
  httpStatusCode: number | null;
  responseBody: string | null;
  latencyMs: number;
  errorMessage: string | null;
  attemptedAt: string; // ISO datetime
}