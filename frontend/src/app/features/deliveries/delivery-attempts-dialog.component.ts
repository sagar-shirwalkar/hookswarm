import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { DeliveriesActions } from './store/deliveries.actions';
import { selectAttempts, selectDeliveriesLoading } from './store/deliveries.selectors';

@Component({
  selector: 'app-delivery-attempts-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatTableModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Delivery Attempts</h2>
    <mat-dialog-content>
      @if (loading$ | async) {
        <div class="loading">
          <mat-spinner diameter="30"></mat-spinner>
        </div>
      } @else {
        <table mat-table [dataSource]="(attempts$ | async) || []" class="full-width">
          <ng-container matColumnDef="attemptNumber">
            <th mat-header-cell *matHeaderCellDef>Attempt</th>
            <td mat-cell *matCellDef="let a">{{ a.attemptNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="statusCode">
            <th mat-header-cell *matHeaderCellDef>HTTP Status</th>
            <td mat-cell *matCellDef="let a">{{ a.httpStatusCode || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="latency">
            <th mat-header-cell *matHeaderCellDef>Latency (ms)</th>
            <td mat-cell *matCellDef="let a">{{ a.latencyMs }}</td>
          </ng-container>
          <ng-container matColumnDef="response">
            <th mat-header-cell *matHeaderCellDef>Response</th>
            <td mat-cell *matCellDef="let a">{{ a.responseBody || a.errorMessage || '-' | slice:0:100 }}</td>
          </ng-container>
          <ng-container matColumnDef="attemptedAt">
            <th mat-header-cell *matHeaderCellDef>Attempted At</th>
            <td mat-cell *matCellDef="let a">{{ a.attemptedAt | date:'medium' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    .loading { display: flex; justify-content: center; padding: 20px; }
    .mat-column-attemptNumber { width: 80px; }
    .mat-column-statusCode { width: 100px; }
    .mat-column-latency { width: 100px; }
    .mat-column-attemptedAt { width: 200px; }
  `]
})
export class DeliveryAttemptsDialogComponent implements OnInit {
  private store = inject(Store);
  private dialogRef = inject(MatDialogRef<DeliveryAttemptsDialogComponent>);
  data: { taskId: string } = inject(MAT_DIALOG_DATA);

  attempts$ = this.store.select(selectAttempts);
  loading$ = this.store.select(selectDeliveriesLoading);
  displayedColumns = ['attemptNumber', 'statusCode', 'latency', 'response', 'attemptedAt'];

  ngOnInit(): void {}

  close(): void {
    this.store.dispatch(DeliveriesActions.clearAttempts());
    this.dialogRef.close();
  }
}