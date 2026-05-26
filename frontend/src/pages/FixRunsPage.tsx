import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { listFixRuns, parseIssueIds } from '../api/fixRuns';
import { getProject } from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';
import { StateBadge } from '../components/StateBadge';

export function FixRunsPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);

  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Number.isFinite(projectId),
  });

  const { data, isLoading, error } = useQuery({
    queryKey: ['fix-runs', projectId],
    queryFn: () => listFixRuns(projectId, 0, 50),
    enabled: Number.isFinite(projectId),
  });

  return (
    <div className="space-y-4">
      <div>
        {project && (
          <Link to={`/projects/${projectId}`} className="text-sm text-slate-500 hover:text-slate-700">
            ← {project.name}
          </Link>
        )}
        <h1 className="mt-1 text-2xl font-semibold">Fix Runs</h1>
        <p className="mt-1 text-sm text-slate-600">
          Each run is one attempt at fixing one or more issues, with its full audit trail.
        </p>
      </div>

      <ErrorBanner error={error} />

      <div className="card overflow-hidden p-0">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              <Th>Run</Th>
              <Th>State</Th>
              <Th>Issues</Th>
              <Th>Branch</Th>
              <Th>Safety</Th>
              <Th>Created</Th>
              <Th>Completed</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={7} className="px-4 py-4 text-sm text-slate-500">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && (data?.content.length ?? 0) === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-slate-500">
                  No fix runs yet. Trigger one from the Issues page.
                </td>
              </tr>
            )}
            {data?.content.map((r) => {
              const issueIds = parseIssueIds(r.issueIdsJson);
              return (
                <tr key={r.id} className="hover:bg-slate-50">
                  <Td>
                    <Link
                      to={`/projects/${projectId}/runs/${r.id}`}
                      className="font-medium text-brand-700 hover:underline"
                    >
                      #{r.id}
                    </Link>
                    {r.autoMode && (
                      <span className="ml-2 badge bg-emerald-100 text-emerald-800">AUTO</span>
                    )}
                  </Td>
                  <Td>
                    <StateBadge state={r.state} />
                  </Td>
                  <Td className="text-sm text-slate-700">{issueIds.length}</Td>
                  <Td className="font-mono text-xs text-slate-600">
                    {r.branchName ?? <span className="text-slate-400">—</span>}
                  </Td>
                  <Td className="text-sm">
                    {r.safetyScore != null ? `${r.safetyScore}` : <span className="text-slate-400">—</span>}
                  </Td>
                  <Td className="text-xs text-slate-600">{formatDate(r.createdAt)}</Td>
                  <Td className="text-xs text-slate-600">
                    {r.completedAt ? formatDate(r.completedAt) : <span className="text-slate-400">—</span>}
                  </Td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return (
    <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
      {children}
    </th>
  );
}

function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-4 py-3 align-top ${className ?? ''}`}>{children}</td>;
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
