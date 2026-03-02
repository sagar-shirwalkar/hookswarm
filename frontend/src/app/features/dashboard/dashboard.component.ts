import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { DashboardActions } from './store/dashboard.actions';
import { selectDashboardStats, selectDashboardLoading, selectDashboardError } from './store/dashboard.selectors';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatGridListModule, MatProgressSpinnerModule],
  template: `
    <h1>Dashboard</h1>
    @if (loading$ | async) {
      <div class="loading-spinner">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (error$ | async; as error) {
      <div class="error-message">{{ error }}</div>
    } @else if (stats$ | async; as stats) {
      <div class="dashboard-grid">
        <mat-card>
          <mat-card-title>Events Today</mat-card-title>
          <mat-card-content>{{ stats.eventsToday | number }}</mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-title>Successful Deliveries</mat-card-title>
          <mat-card-content>{{ stats.successfulDeliveries }}%</mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-title>Pending Retries</mat-card-title>
          <mat-card-content>{{ stats.pendingRetries | number }}</mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-title>Dead Letters</mat-card-title>
          <mat-card-content>{{ stats.deadLetters | number }}</mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [`
    .dashboard-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
      padding: 16px;
    }
    mat-card {
      text-align: center;
    }
    mat-card-title {
      font-size: 1.2rem;
      margin-bottom: 16px;
    }
    mat-card-content {
      font-size: 2rem;
      font-weight: bold;
    }
    .loading-spinner {
      display: flex;
      justify-content: center;
      margin-top: 50px;
    }
    .error-message {
      color: red;
      text-align: center;
      margin-top: 20px;
    }
  `]
})
export class DashboardComponent implements OnInit {
  private store = inject(Store);
  stats$ = this.store.select(selectDashboardStats);
  loading$ = this.store.select(selectDashboardLoading);
  error$ = this.store.select(selectDashboardError);

  ngOnInit(): void {
    this.store.dispatch(DashboardActions.loadStats());
  }
}