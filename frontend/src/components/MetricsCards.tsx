import type { FixSuccessMetrics } from '../api/analytics';

interface Props {
  metrics: FixSuccessMetrics;
}

export function MetricsCards({ metrics }: Props) {
  const pct = (v: number) => `${Math.round(v * 1000) / 10}%`;
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Card label="Fix-run success rate" value={pct(metrics.successRate)} hint={`${metrics.mergedRuns + metrics.prOpenedRuns} merged or open of ${metrics.totalRuns}`} />
      <Card label="Safety block rate" value={pct(metrics.safetyBlockRate)} hint={`${metrics.safetyBlockedRuns} blocked of ${metrics.totalRuns}`} tone={metrics.safetyBlockRate > 0.2 ? 'amber' : 'default'} />
      <Card label="Avg refix attempts" value={metrics.averageRefixAttempts != null ? metrics.averageRefixAttempts.toFixed(2) : '—'} hint="attempts per run" />
      <Card label="Failed runs (last 90d)" value={String(metrics.failedRuns)} tone={metrics.failedRuns > 5 ? 'red' : 'default'} />
    </div>
  );
}

function Card({
  label,
  value,
  hint,
  tone = 'default',
}: {
  label: string;
  value: string;
  hint?: string;
  tone?: 'default' | 'amber' | 'red';
}) {
  const ring =
    tone === 'red' ? 'border-red-200' : tone === 'amber' ? 'border-amber-200' : 'border-slate-200';
  const text =
    tone === 'red' ? 'text-red-700' : tone === 'amber' ? 'text-amber-700' : 'text-slate-900';
  return (
    <div className={`rounded-lg border ${ring} bg-white p-4 shadow-sm`}>
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${text}`}>{value}</div>
      {hint && <div className="mt-0.5 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}
