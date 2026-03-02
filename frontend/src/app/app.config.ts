import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { authReducer } from './features/auth/store/auth.reducer';
import { AuthEffects } from './features/auth/store/auth.effects';
import { dashboardReducer } from './features/dashboard/store/dashboard.reducer';
import { DashboardEffects } from './features/dashboard/store/dashboard.effects';
import { eventsReducer } from './features/events/store/events.reducer';
import { EventsEffects } from './features/events/store/events.effects';
import { environment } from '../environments/environment';
import { subscriptionsReducer } from './features/subscriptions/store/subscriptions.reducer';
import { SubscriptionsEffects } from './features/subscriptions/store/subscriptions.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideStore({
      auth: authReducer,
      dashboard: dashboardReducer,
      events: eventsReducer
    }),
    provideEffects([AuthEffects, DashboardEffects, EventsEffects]),
    provideStoreDevtools({
      maxAge: 25,
      logOnly: environment.production,
      autoPause: true,
    }),
    provideStore({
      auth: authReducer,
      dashboard: dashboardReducer,
      subscriptions: subscriptionsReducer
    }),
    provideEffects([AuthEffects, DashboardEffects, SubscriptionsEffects]),
  ]
};