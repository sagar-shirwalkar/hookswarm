import { createReducer, on } from '@ngrx/store';
import { AuthActions } from './auth.actions';
import { initialAuthState } from './auth.state';

export const authReducer = createReducer(
  initialAuthState,
  on(AuthActions.login, state => ({ ...state, loading: true, error: null })),
  on(AuthActions.loginSuccess, (state, { user, token }) => ({
    ...state, user, token, loading: false
  })),
  on(AuthActions.loginFailure, (state, { error }) => ({
    ...state, error, loading: false
  })),
  on(AuthActions.logout, () => initialAuthState)
);