import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { DeliveriesActions } from './store/deliveries.actions';
import { selectAllTasks, selectTotalElements, selectDeliveriesLoading, selectDeliveriesError } from './store/deliveries.selectors';
import { DeliveryAttemptsDialogComponent } from './delivery-attempts-dialog.component';

@Component({
  selector: 'app-delivery-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatDialogModule
  ],
  template: `
    <div class="container">
      <h1>Deliveries</h1>

      <div class="filters">
        <mat-form-field appearance="outline">
          <mat-label>Filter by Event ID</mat-label>
          <input matInput [(ngModel)]="eventId" placeholder="Enter event ID">
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Filter by Subscription ID</mat-label>
          <input matInput [(ngModel)]="subscriptionId" placeholder="Enter subscription ID">
        </mat-form-field>

        <button mat-raised-button color="primary" (click)="applyFilter()">Apply</button>
        <button mat-button (click)="clearFilters()">Clear</button>
      </div>

      @if (loading$ | async) {
        <div class="loading-spinner">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (error$ | async; as error) {
        <div class="error">{{ error }}</div>
      } @else {
        <table mat-table [dataSource]="(tasks$ | async) || []" class="full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let task">{{ task.id | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="eventId">
            <th mat-header-cell *matHeaderCellDef>Event ID</th>
            <td mat-cell *matCellDef="let task">{{ task.eventId | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="subscriptionId">
            <th mat-header-cell *matHeaderCellDef>Subscription ID</th>
            <td mat-cell *matCellDef="let task">{{ task.subscriptionId | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let task">{{ task.status }}</td>
          </ng-container>

          <ng-container matColumnDef="attempts">
            <th mat-header-cell *matHeaderCellDef>Attempts</th>
            <td mat-cell *matCellDef="let task">{{ task.attemptCount }}</td>
          </ng-container>

          <ng-container matColumnDef="nextRetry">
            <th mat-header-cell *matHeaderCellDef>Next Retry</th>
            <td mat-cell *matCellDef="let task">{{ task.nextAttemptAt | date:'short' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let task">
              <button mat-icon-button color="primary" (click)="viewAttempts(task.id)" matTooltip="View attempts">
                <mat-icon>visibility</mat-icon>
              </button>
              @if (task.status === 'FAILED' || task.status === 'DEAD') {
                <button mat-icon-button color="warn" (click)="retry(task.id)" matTooltip="Retry delivery">
                  <mat-icon>refresh</mat-icon>
                </button>
              }
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <mat-paginator
          [length]="totalElements$ | async"
          [pageSize]="pageSize"
          [pageIndex]="pageIndex"
          (page)="onPageChange($event)"
          [pageSizeOptions]="[5, 10, 20]">
        </mat-paginator>
      }
    </div>
  `,
  styles: [`
    .container { padding: 20px; }
    .filters { display: flex; gap: 16px; align-items: center; margin-bottom: 20px; flex-wrap: wrap; }
    .filters mat-form-field { flex: 1; min-width: 250px; }
    .loading-spinner { display: flex; justify-content: center; margin: 40px; }
    .error { color: red; text-align: center; margin: 20px; }
    .full-width { width: 100%; }
    .mat-column-actions { width: 100px; text-align: center; }
  `]
})
export class DeliveryListComponent implements OnInit {
  private store = inject(Store);
  private dialog = inject(MatDialog);

  eventId = '';
  subscriptionId = '';

  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'eventId', 'subscriptionId', 'status', 'attempts', 'nextRetry', 'actions'];

  tasks$ = this.store.select(selectAllTasks);
  totalElements$ = this.store.select(selectTotalElements);
  loading$ = this.store.select(selectDeliveriesLoading);
  error$ = this.store.select(selectDeliveriesError);

  ngOnInit(): void {}

  applyFilter(): void {
    if (this.eventId) {
      this.store.dispatch(DeliveriesActions.loadTasksByEvent({ eventId: this.eventId }));
    } else if (this.subscriptionId) {
      this.store.dispatch(DeliveriesActions.loadTasksBySubscription({
        subscriptionId: this.subscriptionId,
        page: this.pageIndex,
        size: this.pageSize
      }));
    }
  }

  clearFilters(): void {
    this.eventId = '';
    this.subscriptionId = '';
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.subscriptionId) {
      this.store.dispatch(DeliveriesActions.loadTasksBySubscription({
        subscriptionId: this.subscriptionId,
        page: this.pageIndex,
        size: this.pageSize
      }));
    }
  }

  viewAttempts(taskId: string): void {
    this.store.dispatch(DeliveriesActions.loadAttempts({ taskId }));
    this.dialog.open(DeliveryAttemptsDialogComponent, {
      width: '800px',
      data: { taskId }
    });
  }

  retry(taskId: string): void {
    if (confirm('Are you sure you want to retry this delivery?')) {
      this.store.dispatch(DeliveriesActions.retryTask({ id: taskId }));
    }
  }
}