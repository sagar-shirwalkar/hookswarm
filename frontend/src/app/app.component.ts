import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Store } from '@ngrx/store';
import { AuthActions } from './features/auth/store/auth.actions';
import { selectIsAuthenticated, selectUser } from './features/auth/store/auth.selectors';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule
  ],
  template: `
    @if (isAuthenticated$ | async) {
      <mat-toolbar color="primary" class="toolbar">
        <button mat-icon-button (click)="drawer.toggle()">
          <mat-icon>menu</mat-icon>
        </button>
        <span>HookSwarm</span>
        <span class="spacer"></span>
        @if (user$ | async; as user) {
          <span>{{ user.email }}</span>
        }
        <button mat-button (click)="logout()">Logout</button>
      </mat-toolbar>
      <mat-sidenav-container>
        <mat-sidenav #drawer mode="side" opened class="sidenav">
          <mat-nav-list>
            <a mat-list-item routerLink="/dashboard" routerLinkActive="active">
              <mat-icon matListItemIcon>dashboard</mat-icon>
              <span>Dashboard</span>
            </a>
            <a mat-list-item routerLink="/events" routerLinkActive="active">
              <mat-icon matListItemIcon>add_circle</mat-icon>
              <span>Ingest Event</span>
            </a>
            <a mat-list-item routerLink="/subscriptions" routerLinkActive="active">
              <mat-icon matListItemIcon>subscriptions</mat-icon>
              <span>Subscriptions</span>
            </a>
            <a mat-list-item routerLink="/deliveries" routerLinkActive="active">
              <mat-icon matListItemIcon>forward_to_inbox</mat-icon>
              <span>Deliveries</span>
            </a>
            <a mat-list-item routerLink="/dlq" routerLinkActive="active">
              <mat-icon matListItemIcon>error</mat-icon>
              <span>Dead Letter Queue</span>
            </a>
          </mat-nav-list>
        </mat-sidenav>
        <mat-sidenav-content class="content">
          <router-outlet></router-outlet>
        </mat-sidenav-content>
      </mat-sidenav-container>
    } @else {
      <router-outlet></router-outlet>
    }
  `,
  styles: [`
    .toolbar { position: fixed; top: 0; left: 0; right: 0; z-index: 2; }
    .spacer { flex: 1 1 auto; }
    .sidenav { width: 250px; margin-top: 64px; }
    .content { padding: 80px 20px 20px 20px; }
    .active { background: rgba(0,0,0,0.1); }
  `]
})
export class AppComponent {
  private store = inject(Store);
  private router = inject(Router);
  isAuthenticated$ = this.store.select(selectIsAuthenticated);
  user$ = this.store.select(selectUser);

  logout() {
    this.store.dispatch(AuthActions.logout());
  }
}