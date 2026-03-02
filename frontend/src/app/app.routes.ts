import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [authGuard] },
  { path: 'events', loadComponent: () => import('./features/events/event-form.component').then(m => m.EventFormComponent), canActivate: [authGuard] },
  { path: 'subscriptions', loadComponent: () => import('./features/subscriptions/subscription-list.component').then(m => m.SubscriptionListComponent), canActivate: [authGuard] },
  { path: 'deliveries', loadComponent: () => import('./features/deliveries/delivery-list.component').then(m => m.DeliveryListComponent), canActivate: [authGuard] },
  { path: 'dlq', loadComponent: () => import('./features/dlq/dlq-list.component').then(m => m.DlqListComponent), canActivate: [authGuard] },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];