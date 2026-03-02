export type SubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'DELETED';

export interface Subscription {
  id: string;
  url: string;
  secret?: string; // optional for responses
  eventTypes: string[];
  status: SubscriptionStatus;
  maxRetries: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSubscriptionRequest {
  url: string;
  secret: string;
  eventTypes?: string[];
  maxRetries?: number;
}

export interface UpdateSubscriptionRequest {
  url?: string;
  secret?: string;
  eventTypes?: string[];
  status?: SubscriptionStatus;
  maxRetries?: number;
}