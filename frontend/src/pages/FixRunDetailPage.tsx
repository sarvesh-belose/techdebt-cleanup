import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { abortFixRun, getFixRun, parseIssueIds } from '../api/fixRuns';
import { getProject } from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';
import { StateBadge } from '../components/StateBadge';
import { SafetyAssessmentView } from '../components/SafetyAssessmentView';

const TERMINAL_STATES = new Set(['MERGED', 'FAILED', 'ABORTED']);

export function FixRunDetailPage() {
  const { id, runId } = useParams<{ id: string; runId: string }>();
  const projectId = Number(id);
  const runIdNum = Number(runId);
  const queryClient = useQueryClient();

  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Number.isFinite(projectId),
  });

  const { data: run, isLoading, error } = useQuery({
    queryKey: ['fix-run', projectId, runIdNum],
    queryFn: () => getFixRun(projectId, runIdNum),
    enabled: Number.isFinite(projectId) && Number.isFinite(runIdNum),
  });

  const abortMutation = useMutation({
    mutationFn: () => abortFixRun(projectId, runIdNum, 'manual abort from UI'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['fix-run', projectId, runIdNum] });
      queryClient.invalidateQueries({ queryKey: ['fix-runs', projectId] });
    },
  });

  if (isLoading) return <div className="card text-slate-500">Loading…</div>;
  if (error) return <ErrorBanner error={error} />;
  if (!run) return null;

  const issueIds = parseIssueIds(run.issueIdsJson);
  const canAbort = !TERMINAL_STATES.has(run.state);

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          {project && (
            <Link to={`/projects/${projectId}/runs`} className="text-sm text-slate-500 hover:text-slate-700">
              ← Fix Runs · {project.name}
            </Link>
          )}
          <h1 className="mt-1 text-2xl font-semibold">Fix Run #{run.id}</h1>
          <div className="mt-2 flex items-center gap-3">
            <StateBadge state={run.state} large />
            {run.autoMode && <span className="badge bg-emerald-100 text-emerald-800">AUTO MODE</span>}
          </div>
        </div>
        <div className="flex gap-2">
          <a
            href={`/api/projects/${projectId}/fix-runs/${runIdNum}/dossier`}
            target="_blank"
            rel="noreferrer"
            className="btn-secondary"
          >
            Download dossier
          </a>
          {canAbort && (
            <button
              onClick={() => abortMutation.mutate()}
              className="btn-danger"
              disabled={abortMutation.isPending}
            >
              {abortMutation.isPending ? 'Aborting…' : 'Abort run'}
            </button>
          )}
        </div>
      </div>

      <ErrorBanner error={abortMutation.error} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="card space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Pipeline</h2>
          <KV label="Branch">
            {run.branchName ? (
              <code className="font-mono text-xs">{run.branchName}</code>
            ) : (
              <span className="text-slate-400">—</span>
            )}
          </KV>
          <KV label="Commit">
            {run.commitSha ? (
              <code className="font-mono text-xs">{run.commitSha.slice(0, 12)}</code>
            ) : (
              <span className="text-slate-400">—</span>
            )}
          </KV>
          <KV label="PR URL">
            {run.prUrl ? (
              <a className="text-brand-700 hover:underline" href={run.prUrl} target="_blank" rel="noreferrer">
                {run.prUrl}
              </a>
            ) : (
              <span className="text-slate-400">—</span>
            )}
          </KV>
          <KV label="Issues">{issueIds.length} ({issueIds.join(', ') || '—'})</KV>
          <KV label="Created">{formatDate(run.createdAt)}</KV>
          <KV label="Updated">{formatDate(run.updatedAt)}</KV>
          <KV label="Completed">
            {run.completedAt ? formatDate(run.completedAt) : <span className="text-slate-400">—</span>}
          </KV>
        </div>

        <div className="card space-y-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Safety Guard</h2>
          {run.safetyScore == null && run.safetyBlockedReason == null ? (
            <p className="text-sm text-slate-500">Safety assessment has not run yet.</p>
          ) : (
            <SafetyAssessmentView
              signalsJson={null /* we surface the textual summary; full JSON lands on fix_attempt in a follow-up endpoint */}
              summary={run.safetyBlockedReason}
            />
          )}
          {run.state === 'SAFETY_BLOCKED' && (
            <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
              <div className="font-medium">Why was this blocked?</div>
              <p className="mt-1">
                The diff exceeded this project's safety threshold. Review the signals above
                side-by-side with the diff, then either{' '}
                <span className="font-medium">override</span> (audit-logged) or{' '}
                <span className="font-medium">abort</span>. Override UI lands in Sprint 5.
              </p>
            </div>
          )}
        </div>
      </div>

      {run.failureReason && (
        <div className="card">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Failure reason
          </h2>
          <pre className="mt-2 max-h-64 overflow-auto rounded bg-slate-50 p-3 text-xs text-slate-800">
            {run.failureReason}
          </pre>
        </div>
      )}
    </div>
  );
}

function KV({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-3 gap-3">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="col-span-2 text-sm text-slate-800">{children}</div>
    </div>
  );
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
