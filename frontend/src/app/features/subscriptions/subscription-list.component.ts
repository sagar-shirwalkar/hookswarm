import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Store } from '@ngrx/store';
import { SubscriptionsActions } from './store/subscriptions.actions';
import { selectAllSubscriptions, selectSubscriptionsLoading, selectSubscriptionsError } from './store/subscriptions.selectors';
import { SubscriptionDialogComponent } from './subscription-dialog.component';
import { CreateSubscriptionRequest, UpdateSubscriptionRequest, Subscription } from './models/subscription.model';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-subscription-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="header">
      <h2>Subscriptions</h2>
      <button mat-raised-button color="primary" (click)="openCreateDialog()">Add Subscription</button>
    </div>
    @if (loading$ | async) {
      <div class="loading-spinner"><mat-spinner diameter="40"></mat-spinner></div>
    } @else {
      <table mat-table [dataSource]="(subscriptions$ | async) || []" class="mat-elevation-z8">
        <ng-container matColumnDef="url">
          <th mat-header-cell *matHeaderCellDef>URL</th>
          <td mat-cell *matCellDef="let sub">{{ sub.url }}</td>
        </ng-container>
        <ng-container matColumnDef="eventTypes">
          <th mat-header-cell *matHeaderCellDef>Event Types</th>
          <td mat-cell *matCellDef="let sub">{{ sub.eventTypes.join(', ') || 'All' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let sub">{{ sub.status }}</td>
        </ng-container>
        <ng-container matColumnDef="maxRetries">
          <th mat-header-cell *matHeaderCellDef>Max Retries</th>
          <td mat-cell *matCellDef="let sub">{{ sub.maxRetries }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let sub">
            <button mat-icon-button color="primary" (click)="openEditDialog(sub)"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button color="warn" (click)="delete(sub.id)"><mat-icon>delete</mat-icon></button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
      </table>
    }
  `,
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .loading-spinner { display: flex; justify-content: center; margin-top: 50px; }
    table { width: 100%; }
    .mat-column-actions { width: 100px; text-align: right; }
  `]
})
export class SubscriptionListComponent implements OnInit {
  private store = inject(Store);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  displayedColumns = ['url', 'eventTypes', 'status', 'maxRetries', 'actions'];
  subscriptions$ = this.store.select(selectAllSubscriptions);
  loading$ = this.store.select(selectSubscriptionsLoading);
  error$ = this.store.select(selectSubscriptionsError);

  ngOnInit() {
    this.store.dispatch(SubscriptionsActions.loadSubscriptions());
    this.error$.subscribe(error => {
      if (error) this.snackBar.open(`Error: ${error}`, 'Close', { duration: 5000 });
    });
  }

  async openCreateDialog() {
    const dialogRef = this.dialog.open(SubscriptionDialogComponent, {
      width: '600px',
      data: {}
    });
    const result = await firstValueFrom(dialogRef.afterClosed());
    if (result) {
      this.store.dispatch(SubscriptionsActions.createSubscription({ data: result as CreateSubscriptionRequest }));
    }
  }

  async openEditDialog(subscription: Subscription) {
    const dialogRef = this.dialog.open(SubscriptionDialogComponent, {
      width: '600px',
      data: { subscription }
    });
    const result = await firstValueFrom(dialogRef.afterClosed());
    if (result) {
      const updateData: UpdateSubscriptionRequest = { ...result };
      this.store.dispatch(SubscriptionsActions.updateSubscription({ id: subscription.id, data: updateData }));
    }
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this subscription?')) {
      this.store.dispatch(SubscriptionsActions.deleteSubscription({ id }));
    }
  }
}