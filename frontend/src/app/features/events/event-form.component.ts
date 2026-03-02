import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Store } from '@ngrx/store';
import { EventsActions } from './store/events.actions';
import { selectEventsLoading, selectEventsError, selectLastCreatedEvent } from './store/events.selectors';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-event-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Ingest New Event</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="eventForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Event Type</mat-label>
              <input matInput formControlName="eventType" placeholder="e.g., user.created">
              @if (eventForm.get('eventType')?.hasError('required')) {
                <mat-error>Event type is required</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Payload (JSON)</mat-label>
              <textarea matInput formControlName="payload" rows="8" placeholder="{ &#92;n  &quot;key&quot;: &quot;value&quot;&#92;n}"></textarea>
              @if (eventForm.get('payload')?.hasError('required')) {
                <mat-error>Payload is required</mat-error>
              }
              @if (eventForm.get('payload')?.hasError('invalidJson')) {
                <mat-error>Invalid JSON format</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Idempotency Key (optional)</mat-label>
              <input matInput formControlName="idempotencyKey">
            </mat-form-field>

            @if (error$ | async; as error) {
              <mat-error class="error-message">{{ error }}</mat-error>
            }

            <div class="button-container">
              <button mat-raised-button color="primary" type="submit" [disabled]="eventForm.invalid || (loading$ | async)">
                @if (loading$ | async) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Send Event
                }
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container {
      max-width: 800px;
      margin: 0 auto;
      padding: 20px;
    }
    .full-width {
      width: 100%;
      margin-bottom: 20px;
    }
    .button-container {
      display: flex;
      justify-content: flex-end;
    }
    mat-spinner {
      display: inline-block;
      margin-right: 8px;
    }
    .error-message {
      margin-bottom: 16px;
    }
  `]
})
export class EventFormComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private store = inject(Store);
  private snackBar = inject(MatSnackBar);
  private destroy$ = new Subject<void>();

  eventForm: FormGroup;
  loading$ = this.store.select(selectEventsLoading);
  error$ = this.store.select(selectEventsError);
  lastCreated$ = this.store.select(selectLastCreatedEvent);

  constructor() {
    this.eventForm = this.fb.group({
      eventType: ['', Validators.required],
      payload: ['', [Validators.required, this.jsonValidator]],
      idempotencyKey: ['']
    });
  }

  ngOnInit(): void {
    // Reset last created event on component init
    this.store.dispatch(EventsActions.resetLastCreated());

    // Show snackbar when event is created successfully
    this.lastCreated$.pipe(takeUntil(this.destroy$)).subscribe(event => {
      if (event) {
        this.snackBar.open(`Event created with ID: ${event.id}`, 'Close', { duration: 5000 });
        this.eventForm.reset({ eventType: '', payload: '', idempotencyKey: '' });
        // Re-apply validators after reset? Not needed.
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSubmit(): void {
    if (this.eventForm.valid) {
      const raw = this.eventForm.value;
      // Parse payload from string to JSON
      let payloadObj: any;
      try {
        payloadObj = JSON.parse(raw.payload);
      } catch (e) {
        // Should not happen due to validator
        return;
      }
      const request = {
        eventType: raw.eventType,
        payload: payloadObj,
        idempotencyKey: raw.idempotencyKey || undefined
      };
      this.store.dispatch(EventsActions.createEvent({ request }));
    }
  }

  // Custom validator for JSON
  private jsonValidator(control: any): { [key: string]: any } | null {
    if (!control.value) return null;
    try {
      JSON.parse(control.value);
    } catch (e) {
      return { invalidJson: true };
    }
    return null;
  }
}