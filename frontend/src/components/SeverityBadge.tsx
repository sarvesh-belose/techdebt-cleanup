import type { Severity } from '../api/types';

const styles: Record<Severity, string> = {
  CRITICAL: 'bg-red-100 text-red-800',
  HIGH: 'bg-orange-100 text-orange-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  LOW: 'bg-sky-100 text-sky-800',
  INFO: 'bg-slate-100 text-slate-700',
};

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <span className={`badge ${styles[severity]}`}>{severity}</span>;
}
