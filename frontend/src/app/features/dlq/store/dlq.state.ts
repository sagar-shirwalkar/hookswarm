import { DlqEntry } from '../models/dlq-entry.model';

export interface DlqState {
  entries: DlqEntry[];
  totalElements: number;
  loading: boolean;
  error: string | null;
}

export const initialDlqState: DlqState = {
  entries: [],
  totalElements: 0,
  loading: false,
  error: null
};