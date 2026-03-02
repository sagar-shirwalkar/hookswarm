import { createActionGroup, props, emptyProps } from '@ngrx/store';

export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    'Login': props<{ email: string; password: string }>(),
    'Login Success': props<{ token: string; user: any }>(),
    'Login Failure': props<{ error: string }>(),
    'Logout': emptyProps(),
    'Load User': emptyProps(),
  }
});