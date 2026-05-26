import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { createProject, type CreateProjectRequest } from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';

export function ProjectCreatePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<CreateProjectRequest>({
    name: '',
    description: '',
    azureRepoUrl: '',
    defaultBranch: 'main',
  });

  const mutation = useMutation({
    mutationFn: (req: CreateProjectRequest) => createProject(req),
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      navigate(`/projects/${p.id}`);
    },
  });

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    mutation.mutate({
      name: form.name.trim(),
      description: form.description?.trim() || undefined,
      azureRepoUrl: form.azureRepoUrl.trim(),
      defaultBranch: form.defaultBranch?.trim() || 'main',
    });
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Register a project</h1>
        <p className="mt-1 text-sm text-slate-600">
          Add a Spring Boot service so the platform can ingest its tech-debt findings.
        </p>
      </div>

      <ErrorBanner error={mutation.error} />

      <form onSubmit={onSubmit} className="card space-y-4">
        <div>
          <label className="label" htmlFor="name">
            Project name
          </label>
          <input
            id="name"
            className="input"
            required
            maxLength={200}
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="payments-svc"
          />
        </div>

        <div>
          <label className="label" htmlFor="azureRepoUrl">
            Azure Repos URL
          </label>
          <input
            id="azureRepoUrl"
            className="input"
            required
            value={form.azureRepoUrl}
            onChange={(e) => setForm({ ...form, azureRepoUrl: e.target.value })}
            placeholder="https://dev.azure.com/acme/banking/_git/payments-svc"
          />
          <p className="mt-1 text-xs text-slate-500">
            Must be a Git URL of the form{' '}
            <code className="font-mono">https://.../_git/&lt;repo&gt;</code>.
          </p>
        </div>

        <div>
          <label className="label" htmlFor="defaultBranch">
            Default branch
          </label>
          <input
            id="defaultBranch"
            className="input"
            value={form.defaultBranch}
            onChange={(e) => setForm({ ...form, defaultBranch: e.target.value })}
            placeholder="main"
          />
        </div>

        <div>
          <label className="label" htmlFor="description">
            Description (optional)
          </label>
          <textarea
            id="description"
            className="input"
            rows={3}
            maxLength={2000}
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="btn-secondary"
          >
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Registering…' : 'Register project'}
          </button>
        </div>
      </form>
    </div>
  );
}
