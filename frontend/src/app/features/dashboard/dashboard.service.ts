import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardStats } from './dashboard-stats.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/metrics/dashboard`;

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(this.baseUrl);
  }
}