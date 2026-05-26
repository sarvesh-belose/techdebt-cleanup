import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listProjects } from '../api/projects';
import { fixSuccessMetrics, orgRollup } from '../api/analytics';
import { ErrorBanner } from '../components/ErrorBanner';
import { MetricsCards } from '../components/MetricsCards';
import { OrgRollupTable } from '../components/OrgRollupTable';

export function DashboardPage() {
  const projectsQuery = useQuery({
    queryKey: ['projects', { includeArchived: false }],
    queryFn: () => listProjects({ includeArchived: false, size: 50 }),
  });

  const metricsQuery = useQuery({
    queryKey: ['analytics', 'metrics'],
    queryFn: () => fixSuccessMetrics(90),
  });

  const rollupQuery = useQuery({
    queryKey: ['analytics', 'org-rollup'],
    queryFn: () => orgRollup(),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-600">
            Tech-debt health across registered services. Metrics over the last 90 days.
          </p>
        </div>
        <Link to="/projects/new" className="btn-primary">
          Register project
        </Link>
      </div>

      <ErrorBanner error={projectsQuery.error || metricsQuery.error || rollupQuery.error} />

      {metricsQuery.data && <MetricsCards metrics={metricsQuery.data} />}

      {rollupQuery.data && <OrgRollupTable rows={rollupQuery.data} />}

      <div>
        <div className="mb-2 flex items-baseline justify-between">
          <h2 className="text-lg font-semibold">Projects</h2>
          <Link to="/projects" className="text-sm text-brand-700 hover:underline">
            View all
          </Link>
        </div>
        {projectsQuery.isLoading && <div className="card text-slate-500">Loading projects…</div>}
        {projectsQuery.data && projectsQuery.data.totalElements === 0 && (
          <div className="card flex flex-col items-center gap-3 py-12 text-center">
            <div className="text-lg font-medium">No projects yet</div>
            <p className="max-w-md text-sm text-slate-600">
              Register your first Spring Boot service to start ingesting Veracode and SonarQube
              issues.
            </p>
            <Link to="/projects/new" className="btn-primary">
              Register project
            </Link>
          </div>
        )}
        {projectsQuery.data && projectsQuery.data.totalElements > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {projectsQuery.data.content.map((p) => (
              <Link
                key={p.id}
                to={`/projects/${p.id}`}
                className="card transition hover:border-brand-400 hover:shadow"
              >
                <div className="flex items-start justify-between">
                  <div className="font-medium">{p.name}</div>
                  {p.autoModeEnabled && (
                    <span className="badge bg-emerald-100 text-emerald-800">AUTO</span>
                  )}
                </div>
                {p.description && (
                  <p className="mt-2 line-clamp-2 text-sm text-slate-600">{p.description}</p>
                )}
                <div className="mt-3 text-xs text-slate-500">
                  Branch: {p.defaultBranch} · safety {p.safetyThreshold}
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
