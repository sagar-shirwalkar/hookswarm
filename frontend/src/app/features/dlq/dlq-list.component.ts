import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { Store } from '@ngrx/store';
import { DlqActions } from './store/dlq.actions';
import { selectDlqEntries, selectDlqTotalElements, selectDlqLoading, selectDlqError } from './store/dlq.selectors';

@Component({
  selector: 'app-dlq-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="container">
      <h1>Dead Letter Queue</h1>

      @if (loading$ | async) {
        <div class="loading-spinner">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      } @else if (error$ | async; as error) {
        <div class="error">{{ error }}</div>
      } @else {
        <table mat-table [dataSource]="(entries$ | async) || []" class="full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let entry">{{ entry.id | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="eventId">
            <th mat-header-cell *matHeaderCellDef>Event ID</th>
            <td mat-cell *matCellDef="let entry">{{ entry.eventId | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="subscriptionId">
            <th mat-header-cell *matHeaderCellDef>Subscription ID</th>
            <td mat-cell *matCellDef="let entry">{{ entry.subscriptionId | slice:0:8 }}...</td>
          </ng-container>

          <ng-container matColumnDef="attempts">
            <th mat-header-cell *matHeaderCellDef>Attempts</th>
            <td mat-cell *matCellDef="let entry">{{ entry.totalAttempts }}</td>
          </ng-container>

          <ng-container matColumnDef="lastError">
            <th mat-header-cell *matHeaderCellDef>Last Error</th>
            <td mat-cell *matCellDef="let entry">{{ entry.lastError || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="deadAt">
            <th mat-header-cell *matHeaderCellDef>Dead At</th>
            <td mat-cell *matCellDef="let entry">{{ entry.deadAt | date:'medium' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let entry">
              <button mat-icon-button color="primary" (click)="replay(entry.id)" [disabled]="replaying">
                <mat-icon>replay</mat-icon>
              </button>
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
    .loading-spinner { display: flex; justify-content: center; margin: 40px; }
    .error { color: red; text-align: center; margin: 20px; }
    .full-width { width: 100%; }
    .mat-column-actions { width: 80px; text-align: center; }
  `]
})
export class DlqListComponent implements OnInit {
  private store = inject(Store);
  private snackBar = inject(MatSnackBar);

  pageIndex = 0;
  pageSize = 10;
  displayedColumns = ['id', 'eventId', 'subscriptionId', 'attempts', 'lastError', 'deadAt', 'actions'];

  entries$ = this.store.select(selectDlqEntries);
  totalElements$ = this.store.select(selectDlqTotalElements);
  loading$ = this.store.select(selectDlqLoading);
  error$ = this.store.select(selectDlqError);

  replaying = false;

  ngOnInit(): void {
    this.loadEntries();
  }

  loadEntries(): void {
    this.store.dispatch(DlqActions.loadEntries({ page: this.pageIndex, size: this.pageSize }));
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadEntries();
  }

  replay(id: string): void {
    this.replaying = true;
    this.store.dispatch(DlqActions.replayEntry({ id }));
    this.snackBar.open('Replay initiated', 'Close', { duration: 3000 });
    // Reset flag after a short delay (the entry will be removed from the list via reducer)
    setTimeout(() => this.replaying = false, 1000);
  }
}