# HookSwarm UI

This is the Angular frontend for HookSwarm, a high‑throughput webhook delivery engine. It provides a dashboard, event ingestion, subscription management, delivery tracking, and dead‑letter queue management.

## Prerequisites

- Node.js 20 or 22 (LTS)
- npm 10+

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The application will automatically reload if you change any of the source files. API requests are proxied to `http://localhost:8080` via the proxy configuration.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory. Use the `--prod` flag for a production build.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Project structure

- `src/app/core` – Core services, guards, interceptors, and app state.
- `src/app/features` – Feature modules (auth, dashboard, events, subscriptions, deliveries, dlq).
- `src/app/shared` – Shared components and models.
- `src/assets` – Static assets (images, etc.).
- `src/environments` – Environment configuration.

## State management

The application uses NgRx for state management. Each feature has its own store (actions, reducer, effects, selectors). The store is configured in `app.config.ts`.

## Authentication

JWT‑based authentication is implemented. The token is stored in localStorage and automatically attached to API requests via an HTTP interceptor. The auth guard protects routes.

## API

All API requests are prefixed with `/api` and are proxied to the backend during development. In production, the frontend is served from the same origin as the API, so no CORS issues.

## Styling

Angular Material is used for UI components. Custom styles are in SCSS. The default theme is `indigo-pink`.

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI Overview and Command Reference](https://angular.io/cli) page.