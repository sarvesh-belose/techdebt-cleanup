import { useMemo, useState } from 'react';
import type { OrgRollupRow } from '../api/analytics';

interface Props {
  rows: OrgRollupRow[];
}

type GroupBy = 'project' | 'source' | 'severity' | 'status';

export function OrgRollupTable({ rows }: Props) {
  const [groupBy, setGroupBy] = useState<GroupBy>('project');
  const [hideClosed, setHideClosed] = useState(true);

  const visible = useMemo(() => {
    if (!hideClosed) return rows;
    return rows.filter(
      (r) => r.status === 'OPEN' || r.status === 'IN_PROGRESS',
    );
  }, [rows, hideClosed]);

  const grouped = useMemo(() => {
    const map = new Map<string, number>();
    for (const r of visible) {
      const key =
        groupBy === 'project'
          ? r.projectName
          : groupBy === 'source'
            ? r.source.replace(/_/g, ' ')
            : groupBy === 'severity'
              ? r.severity
              : r.status.replace(/_/g, ' ');
      map.set(key, (map.get(key) ?? 0) + r.count);
    }
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1]);
  }, [visible, groupBy]);

  const total = visible.reduce((sum, r) => sum + r.count, 0);

  return (
    <div className="card space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Org-wide issue rollup</h2>
        <div className="flex items-center gap-2 text-sm">
          <label className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={hideClosed}
              onChange={(e) => setHideClosed(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300"
            />
            Open only
          </label>
          <select
            className="rounded-md border border-slate-300 px-2 py-1 text-sm"
            value={groupBy}
            onChange={(e) => setGroupBy(e.target.value as GroupBy)}
          >
            <option value="project">Group by project</option>
            <option value="source">Group by source</option>
            <option value="severity">Group by severity</option>
            <option value="status">Group by status</option>
          </select>
        </div>
      </div>

      <div className="text-xs text-slate-500">
        {total} {hideClosed ? 'open' : 'total'} issues across {grouped.length} group(s)
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                {groupBy[0].toUpperCase() + groupBy.slice(1)}
              </th>
              <th className="px-4 py-2 text-right text-xs font-medium uppercase tracking-wide text-slate-500">
                Count
              </th>
              <th className="px-4 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                Share
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {grouped.length === 0 && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-sm text-slate-500">
                  No issues to roll up.
                </td>
              </tr>
            )}
            {grouped.map(([label, count]) => {
              const share = total > 0 ? count / total : 0;
              return (
                <tr key={label} className="hover:bg-slate-50">
                  <td className="px-4 py-2">{label}</td>
                  <td className="px-4 py-2 text-right font-medium">{count}</td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <div className="h-2 w-32 overflow-hidden rounded bg-slate-100">
                        <div
                          className="h-2 bg-brand-500"
                          style={{ width: `${Math.round(share * 100)}%` }}
                        />
                      </div>
                      <span className="text-xs text-slate-500">{(share * 100).toFixed(1)}%</span>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
