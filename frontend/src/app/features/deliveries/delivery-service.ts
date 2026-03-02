import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DeliveryTask } from './models/delivery-task.model';
import { DeliveryAttempt } from './models/delivery-attempt.model';
import { PagedResponse } from '../../shared/models/paged-response.model';

@Injectable({ providedIn: 'root' })
export class DeliveryService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/deliveries`;

  getTasksByEventId(eventId: string): Observable<DeliveryTask[]> {
    return this.http.get<DeliveryTask[]>(this.baseUrl, {
      params: { eventId }
    });
  }

  getTasksBySubscriptionId(subscriptionId: string, page: number, size: number): Observable<PagedResponse<DeliveryTask>> {
    const params = new HttpParams()
      .set('subscriptionId', subscriptionId)
      .set('page', page)
      .set('size', size);
    return this.http.get<PagedResponse<DeliveryTask>>(this.baseUrl, { params });
  }

  getTask(id: string): Observable<DeliveryTask> {
    return this.http.get<DeliveryTask>(`${this.baseUrl}/${id}`);
  }

  getAttempts(taskId: string): Observable<DeliveryAttempt[]> {
    return this.http.get<DeliveryAttempt[]>(`${this.baseUrl}/${taskId}/attempts`);
  }

  retryTask(id: string): Observable<DeliveryTask> {
    return this.http.post<DeliveryTask>(`${this.baseUrl}/${id}/retry`, {});
  }
}