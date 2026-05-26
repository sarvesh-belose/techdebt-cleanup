import type { FixRunState } from '../api/types';

const styles: Record<FixRunState, string> = {
  QUEUED: 'bg-slate-100 text-slate-700',
  CLONING: 'bg-sky-100 text-sky-800',
  FIXING: 'bg-indigo-100 text-indigo-800',
  FAST_BUILD: 'bg-blue-100 text-blue-800',
  CANONICAL_BUILD: 'bg-blue-100 text-blue-800',
  PR_OPENED: 'bg-purple-100 text-purple-800',
  MERGED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
  ABORTED: 'bg-slate-200 text-slate-700',
  SAFETY_BLOCKED: 'bg-amber-100 text-amber-900',
};

export function StateBadge({ state, large = false }: { state: FixRunState; large?: boolean }) {
  return (
    <span className={`badge ${styles[state]} ${large ? 'px-3 py-1 text-sm' : ''}`}>
      {state.replace(/_/g, ' ')}
    </span>
  );
}
