import { Component, Inject, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Subscription, CreateSubscriptionRequest, UpdateSubscriptionRequest } from './models/subscription.model';

export interface SubscriptionDialogData {
  subscription?: Subscription; // if present, edit mode
}

@Component({
  selector: 'app-subscription-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatChipsModule,
    MatIconModule
  ],
  template: `
    <h2 mat-dialog-title>{{ data.subscription ? 'Edit' : 'Create' }} Subscription</h2>
    <mat-dialog-content>
      <form #form="ngForm">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>URL</mat-label>
          <input matInput [(ngModel)]="model.url" name="url" required url>
        </mat-form-field>
        @if (!data.subscription) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Secret</mat-label>
            <input matInput [(ngModel)]="model.secret" name="secret" required minlength="32">
          </mat-form-field>
        }
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Event Types</mat-label>
          <mat-chip-grid #chipGrid aria-label="Event types">
            @for (type of eventTypes; track type) {
              <mat-chip-row (removed)="removeEventType(type)">
                {{type}}
                <button matChipRemove><mat-icon>cancel</mat-icon></button>
              </mat-chip-row>
            }
          </mat-chip-grid>
          <input placeholder="Add type..." [matChipInputFor]="chipGrid"
                (matChipInputTokenEnd)="addEventType($event)"/>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Max Retries</mat-label>
          <input matInput type="number" [(ngModel)]="model.maxRetries" name="maxRetries" required min="0">
        </mat-form-field>
        @if (data.subscription) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Status</mat-label>
            <select matNativeControl [(ngModel)]="model.status" name="status">
              <option value="ACTIVE">Active</option>
              <option value="PAUSED">Paused</option>
            </select>
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; margin-bottom: 16px; }']
})
export class SubscriptionDialogComponent {
  dialogRef = inject(MatDialogRef<SubscriptionDialogComponent>);
  data: SubscriptionDialogData = inject(MAT_DIALOG_DATA);

  model: any = {};
  eventTypes: string[] = [];

  constructor() {
    if (this.data.subscription) {
      // edit mode
      this.model = {
        url: this.data.subscription.url,
        maxRetries: this.data.subscription.maxRetries,
        status: this.data.subscription.status,
        // secret not editable
      };
      this.eventTypes = [...this.data.subscription.eventTypes];
    } else {
      this.model = { maxRetries: 5, status: 'ACTIVE' };
    }
  }

  addEventType(event: any): void {
    const value = (event.value || '').trim();
    if (value && !this.eventTypes.includes(value)) {
      this.eventTypes.push(value);
    }
    event.chipInput!.clear();
  }

  removeEventType(type: string): void {
    const index = this.eventTypes.indexOf(type);
    if (index >= 0) {
      this.eventTypes.splice(index, 1);
    }
  }

  save(): void {
    const result: any = {
      url: this.model.url,
      eventTypes: this.eventTypes,
      maxRetries: this.model.maxRetries
    };
    if (!this.data.subscription) {
      result.secret = this.model.secret;
    } else {
      if (this.model.status) result.status = this.model.status;
    }
    this.dialogRef.close(result);
  }
}