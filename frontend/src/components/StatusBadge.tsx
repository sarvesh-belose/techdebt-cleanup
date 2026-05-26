import type { IssueStatus } from '../api/types';

const styles: Record<IssueStatus, string> = {
  OPEN: 'bg-red-100 text-red-800',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  FIXED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-slate-200 text-slate-700',
  WONT_FIX: 'bg-slate-200 text-slate-700',
  FIXED_EXTERNALLY: 'bg-emerald-50 text-emerald-700',
};

export function StatusBadge({ status }: { status: IssueStatus }) {
  return <span className={`badge ${styles[status]}`}>{status.replace(/_/g, ' ')}</span>;
}
