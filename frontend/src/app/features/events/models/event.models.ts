export interface CreateEventRequest {
  eventType: string;
  payload: any; // JSON object
  idempotencyKey?: string;
}

export interface EventResponse {
  id: string;
  eventType: string;
  payload: any;
  idempotencyKey?: string;
  createdAt: string; // ISO date string
}