import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  archiveProject,
  listProjects,
  type Project,
  unarchiveProject,
} from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';

export function ProjectListPage() {
  const [includeArchived, setIncludeArchived] = useState(false);
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['projects', { includeArchived }],
    queryFn: () => listProjects({ includeArchived, size: 100 }),
  });

  const archive = useMutation({
    mutationFn: (id: number) => archiveProject(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  });
  const unarchive = useMutation({
    mutationFn: (id: number) => unarchiveProject(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects'] }),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Projects</h1>
          <p className="mt-1 text-sm text-slate-600">
            All Spring Boot services registered with the platform.
          </p>
        </div>
        <Link to="/projects/new" className="btn-primary">
          Register project
        </Link>
      </div>

      <div className="flex items-center gap-2">
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(e) => setIncludeArchived(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          Include archived
        </label>
      </div>

      <ErrorBanner error={error || archive.error || unarchive.error} />

      <div className="card overflow-hidden p-0">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              <Th>Name</Th>
              <Th>Branch</Th>
              <Th>Auto</Th>
              <Th>Status</Th>
              <Th className="text-right">Actions</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-6 py-4 text-sm text-slate-500">
                  Loading…
                </td>
              </tr>
            )}
            {data?.content.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-4 text-sm text-slate-500">
                  No projects to show.
                </td>
              </tr>
            )}
            {data?.content.map((p: Project) => (
              <tr key={p.id} className="hover:bg-slate-50">
                <Td>
                  <Link to={`/projects/${p.id}`} className="font-medium text-brand-700 hover:underline">
                    {p.name}
                  </Link>
                  {p.description && (
                    <div className="text-xs text-slate-500">{p.description}</div>
                  )}
                </Td>
                <Td className="text-sm text-slate-600">{p.defaultBranch}</Td>
                <Td>
                  {p.autoModeEnabled ? (
                    <span className="badge bg-emerald-100 text-emerald-800">ON</span>
                  ) : (
                    <span className="badge bg-slate-100 text-slate-700">off</span>
                  )}
                </Td>
                <Td>
                  {p.archived ? (
                    <span className="badge bg-amber-100 text-amber-800">archived</span>
                  ) : (
                    <span className="badge bg-emerald-100 text-emerald-800">active</span>
                  )}
                </Td>
                <Td className="text-right">
                  {p.archived ? (
                    <button
                      onClick={() => unarchive.mutate(p.id)}
                      disabled={unarchive.isPending}
                      className="btn-secondary"
                    >
                      Unarchive
                    </button>
                  ) : (
                    <button
                      onClick={() => archive.mutate(p.id)}
                      disabled={archive.isPending}
                      className="btn-danger"
                    >
                      Archive
                    </button>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <th
      className={`px-6 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500 ${className ?? ''}`}
    >
      {children}
    </th>
  );
}

function Td({ children, className }: { children: React.ReactNode; className?: string }) {
  return <td className={`px-6 py-3 align-top ${className ?? ''}`}>{children}</td>;
}
