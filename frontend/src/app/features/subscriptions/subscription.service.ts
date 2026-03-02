import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Subscription, CreateSubscriptionRequest, UpdateSubscriptionRequest } from './models/subscription.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/subscriptions`;

  getAll(): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(this.baseUrl);
  }

  getById(id: string): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.baseUrl}/${id}`);
  }

  create(data: CreateSubscriptionRequest): Observable<Subscription> {
    return this.http.post<Subscription>(this.baseUrl, data);
  }

  update(id: string, data: UpdateSubscriptionRequest): Observable<Subscription> {
    return this.http.put<Subscription>(`${this.baseUrl}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}