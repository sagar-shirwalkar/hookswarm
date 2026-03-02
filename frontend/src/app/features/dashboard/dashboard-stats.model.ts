export interface DashboardStats {
  eventsToday: number;
  successfulDeliveries: number; // or percentage
  pendingRetries: number;
  deadLetters: number;
  // Add other relevant metrics
}