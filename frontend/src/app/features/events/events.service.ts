import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateEventRequest, EventResponse } from './models/event.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class EventsService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/v1/events`;

  createEvent(request: CreateEventRequest): Observable<EventResponse> {
    return this.http.post<EventResponse>(this.baseUrl, request);
  }
}