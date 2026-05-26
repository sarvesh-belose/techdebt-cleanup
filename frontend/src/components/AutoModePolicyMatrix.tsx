import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateProject, type Project } from '../api/projects';
import { ErrorBanner } from './ErrorBanner';
import { ISSUE_SOURCES, SEVERITIES, type IssueSource, type Severity } from '../api/types';

interface Props {
  project: Project;
}

type PolicyMatrix = Record<IssueSource, Set<Severity>>;

export function AutoModePolicyMatrix({ project }: Props) {
  const queryClient = useQueryClient();
  const [matrix, setMatrix] = useState<PolicyMatrix>(() => parsePolicy(project.autoPolicyJson));
  const [rateLimit, setRateLimit] = useState<number>(project.autoRateLimitPerHour);

  useEffect(() => {
    setMatrix(parsePolicy(project.autoPolicyJson));
    setRateLimit(project.autoRateLimitPerHour);
  }, [project.autoPolicyJson, project.autoRateLimitPerHour]);

  const mutation = useMutation({
    mutationFn: () =>
      updateProject(project.id, {
        autoPolicyJson: serializePolicy(matrix),
        autoRateLimitPerHour: rateLimit,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', project.id] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  const toggle = (source: IssueSource, severity: Severity) => {
    setMatrix((prev) => {
      const next = { ...prev, [source]: new Set(prev[source]) } as PolicyMatrix;
      if (next[source].has(severity)) next[source].delete(severity);
      else next[source].add(severity);
      return next;
    });
  };

  const totalAllowed = ISSUE_SOURCES.reduce((sum, s) => sum + (matrix[s]?.size ?? 0), 0);
  const enabledOverall = project.autoModeEnabled;

  return (
    <div className="card space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Auto-mode policy</h2>
        <p className="mt-1 text-sm text-slate-600">
          Auto-mode only fires for (source, severity) pairs checked below. Empty matrix = deny all.
          The global kill switch on the Settings page overrides everything.
        </p>
        {!enabledOverall && (
          <div className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            This project's auto-mode toggle is currently <strong>off</strong> — this matrix has no
            effect until you flip it on in Project settings below.
          </div>
        )}
      </div>

      <ErrorBanner error={mutation.error} />

      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className="border-b border-slate-200 px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                Source
              </th>
              {SEVERITIES.map((s) => (
                <th
                  key={s}
                  className="border-b border-slate-200 px-3 py-2 text-center text-xs font-medium uppercase tracking-wide text-slate-500"
                >
                  {s}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ISSUE_SOURCES.map((source) => (
              <tr key={source} className="hover:bg-slate-50">
                <td className="border-b border-slate-100 px-3 py-2 font-medium">
                  {source.replace(/_/g, ' ')}
                </td>
                {SEVERITIES.map((sev) => (
                  <td key={sev} className="border-b border-slate-100 px-3 py-2 text-center">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-slate-300"
                      checked={matrix[source]?.has(sev) ?? false}
                      onChange={() => toggle(source, sev)}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div>
          <label className="label">Auto-runs per hour (rate limit)</label>
          <input
            type="number"
            min={0}
            className="input"
            value={rateLimit}
            onChange={(e) => setRateLimit(Number(e.target.value))}
          />
          <p className="mt-1 text-xs text-slate-500">
            0 = unlimited (rely on the global kill switch instead).
          </p>
        </div>
        <div className="text-sm text-slate-600">
          <div className="label">Current matrix</div>
          <p>
            {totalAllowed === 0
              ? 'Deny all — no auto-mode actions will fire.'
              : `${totalAllowed} (source, severity) pair${totalAllowed === 1 ? '' : 's'} eligible.`}
          </p>
        </div>
      </div>

      <div className="flex justify-end">
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="btn-primary"
        >
          {mutation.isPending ? 'Saving…' : 'Save policy'}
        </button>
      </div>
    </div>
  );
}

function parsePolicy(json: string | null): PolicyMatrix {
  const empty: PolicyMatrix = {
    VERACODE_SCA: new Set(),
    VERACODE_SAST: new Set(),
    SONARQUBE: new Set(),
    JUNIT_COVERAGE: new Set(),
  };
  if (!json) return empty;
  try {
    const parsed = JSON.parse(json) as Record<string, string[]>;
    for (const key of Object.keys(parsed)) {
      if (key in empty) {
        empty[key as IssueSource] = new Set((parsed[key] ?? []) as Severity[]);
      }
    }
  } catch {
    // ignore — empty matrix
  }
  return empty;
}

function serializePolicy(matrix: PolicyMatrix): string {
  const out: Record<string, string[]> = {};
  for (const source of Object.keys(matrix)) {
    const set = matrix[source as IssueSource];
    if (set.size > 0) out[source] = Array.from(set);
  }
  return JSON.stringify(out);
}
