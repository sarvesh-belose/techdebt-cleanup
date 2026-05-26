import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  archiveProject,
  getProject,
  unarchiveProject,
  updateProject,
  type UpdateProjectRequest,
} from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';
import { ScanSourcesSection } from '../components/ScanSourcesSection';
import { AutoModePolicyMatrix } from '../components/AutoModePolicyMatrix';

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data: project, isLoading, error } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Number.isFinite(projectId),
  });

  const [form, setForm] = useState<UpdateProjectRequest>({});
  useEffect(() => {
    if (project) {
      setForm({
        description: project.description ?? '',
        azureRepoUrl: project.azureRepoUrl,
        defaultBranch: project.defaultBranch,
        autoModeEnabled: project.autoModeEnabled,
        safetyThreshold: project.safetyThreshold,
      });
    }
  }, [project]);

  const update = useMutation({
    mutationFn: (req: UpdateProjectRequest) => updateProject(projectId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  const archive = useMutation({
    mutationFn: () => archiveProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  const unarchive = useMutation({
    mutationFn: () => unarchiveProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  if (isLoading) return <div className="card text-slate-500">Loading…</div>;
  if (error) return <ErrorBanner error={error} />;
  if (!project) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <button
            onClick={() => navigate(-1)}
            className="text-sm text-slate-500 hover:text-slate-700"
          >
            ← Back
          </button>
          <h1 className="mt-1 text-2xl font-semibold">{project.name}</h1>
          <div className="mt-1 flex items-center gap-2">
            {project.archived ? (
              <span className="badge bg-amber-100 text-amber-800">archived</span>
            ) : (
              <span className="badge bg-emerald-100 text-emerald-800">active</span>
            )}
            {project.autoModeEnabled && (
              <span className="badge bg-emerald-100 text-emerald-800">AUTO MODE</span>
            )}
            <span className="badge bg-slate-100 text-slate-700">
              safety {project.safetyThreshold}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to={`/projects/${projectId}/issues`} className="btn-secondary">
            View Issues
          </Link>
          <Link to={`/projects/${projectId}/runs`} className="btn-secondary">
            View Fix Runs
          </Link>
          {project.archived ? (
            <button onClick={() => unarchive.mutate()} className="btn-secondary">
              Unarchive
            </button>
          ) : (
            <button onClick={() => archive.mutate()} className="btn-danger">
              Archive
            </button>
          )}
        </div>
      </div>

      <ErrorBanner error={update.error || archive.error || unarchive.error} />

      <ScanSourcesSection projectId={projectId} />

      <AutoModePolicyMatrix project={project} />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          update.mutate(form);
        }}
        className="card space-y-4"
      >
        <h2 className="text-lg font-semibold">Project settings</h2>
        <div>
          <label className="label">Azure Repos URL</label>
          <input
            className="input"
            value={form.azureRepoUrl ?? ''}
            onChange={(e) => setForm({ ...form, azureRepoUrl: e.target.value })}
          />
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div>
            <label className="label">Default branch</label>
            <input
              className="input"
              value={form.defaultBranch ?? ''}
              onChange={(e) => setForm({ ...form, defaultBranch: e.target.value })}
            />
          </div>
          <div>
            <label className="label">Safety threshold (0–100)</label>
            <input
              type="number"
              min={0}
              max={100}
              className="input"
              value={form.safetyThreshold ?? 60}
              onChange={(e) =>
                setForm({ ...form, safetyThreshold: Number(e.target.value) })
              }
            />
            <p className="mt-1 text-xs text-slate-500">
              Fix Runs whose Safety Guard score reaches this value are blocked from auto-merge
              and held for review.
            </p>
          </div>
        </div>
        <div>
          <label className="label">Description</label>
          <textarea
            className="input"
            rows={3}
            value={form.description ?? ''}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>
        <div>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={form.autoModeEnabled ?? false}
              onChange={(e) => setForm({ ...form, autoModeEnabled: e.target.checked })}
              className="h-4 w-4 rounded border-slate-300"
            />
            Enable auto-mode (fixes apply without manual review)
          </label>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button type="submit" className="btn-primary" disabled={update.isPending}>
            {update.isPending ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
