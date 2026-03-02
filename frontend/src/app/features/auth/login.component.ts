import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Store } from '@ngrx/store';
import { AuthActions } from './store/auth.actions';
import { selectAuthLoading, selectAuthError } from './store/auth.selectors';

// Standalone component with Angular Material.

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  template: `
    <div class="login-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>HookSwarm Login</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form #loginForm="ngForm" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" [(ngModel)]="email" name="email" required email>
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" [(ngModel)]="password" name="password" required>
            </mat-form-field>
            @if (error$ | async; as error) {
              <mat-error>{{ error }}</mat-error>
            }
            <div class="button-container">
              <button mat-raised-button color="primary" type="submit" [disabled]="loginForm.invalid || (loading$ | async)">
                @if (loading$ | async) {
                  <mat-spinner diameter="20"></mat-spinner>
                } @else {
                  Login
                }
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container { display: flex; justify-content: center; align-items: center; height: 100vh; }
    mat-card { width: 400px; }
    .full-width { width: 100%; margin-bottom: 16px; }
    .button-container { display: flex; justify-content: flex-end; }
    mat-spinner { display: inline-block; margin-right: 8px; }
  `]
})
export class LoginComponent {
  private store = inject(Store);
  email = '';
  password = '';
  loading$ = this.store.select(selectAuthLoading);
  error$ = this.store.select(selectAuthError);

  onSubmit() {
    this.store.dispatch(AuthActions.login({ email: this.email, password: this.password }));
  }
}