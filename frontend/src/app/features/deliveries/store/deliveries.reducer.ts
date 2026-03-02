import { createReducer, on } from '@ngrx/store';
import { DeliveriesActions } from './deliveries.actions';
import { initialDeliveriesState } from './deliveries.state';

export const deliveriesReducer = createReducer(
  initialDeliveriesState,

  // Load by event
  on(DeliveriesActions.loadTasksByEvent, state => ({ ...state, loading: true, error: null })),
  on(DeliveriesActions.loadTasksByEventSuccess, (state, { tasks }) => ({
    ...state,
    tasks,
    loading: false
  })),
  on(DeliveriesActions.loadTasksByEventFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Load by subscription (paginated)
  on(DeliveriesActions.loadTasksBySubscription, state => ({ ...state, loading: true, error: null })),
  on(DeliveriesActions.loadTasksBySubscriptionSuccess, (state, { response }) => ({
    ...state,
    tasks: response.content,
    totalElements: response.totalElements,
    loading: false
  })),
  on(DeliveriesActions.loadTasksBySubscriptionFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Load single task
  on(DeliveriesActions.loadTask, state => ({ ...state, loading: true, error: null })),
  on(DeliveriesActions.loadTaskSuccess, (state, { task }) => ({
    ...state,
    selectedTask: task,
    loading: false
  })),
  on(DeliveriesActions.loadTaskFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Load attempts
  on(DeliveriesActions.loadAttempts, state => ({ ...state, loading: true, error: null })),
  on(DeliveriesActions.loadAttemptsSuccess, (state, { attempts }) => ({
    ...state,
    attempts,
    loading: false
  })),
  on(DeliveriesActions.loadAttemptsFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Retry task
  on(DeliveriesActions.retryTask, state => ({ ...state, loading: true, error: null })),
  on(DeliveriesActions.retryTaskSuccess, (state, { task }) => ({
    ...state,
    selectedTask: task,
    tasks: state.tasks.map(t => t.id === task.id ? task : t),
    loading: false
  })),
  on(DeliveriesActions.retryTaskFailure, (state, { error }) => ({
    ...state,
    error,
    loading: false
  })),

  // Clear
  on(DeliveriesActions.clearSelectedTask, state => ({ ...state, selectedTask: null })),
  on(DeliveriesActions.clearAttempts, state => ({ ...state, attempts: [] }))
);