import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  deleteScanSource,
  listScanSources,
  type ScanSourceConfig,
  upsertScanSource,
  type UpsertScanSourceRequest,
} from '../api/scanSources';
import { triggerScan } from '../api/scans';
import { ErrorBanner } from './ErrorBanner';
import { SourceBadge } from './SourceBadge';
import { ISSUE_SOURCES, type IssueSource } from '../api/types';

interface Props {
  projectId: number;
}

export function ScanSourcesSection({ projectId }: Props) {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);

  const { data: sources, isLoading, error } = useQuery({
    queryKey: ['scan-sources', projectId],
    queryFn: () => listScanSources(projectId),
  });

  const scan = useMutation({
    mutationFn: (source: IssueSource) => triggerScan(projectId, source),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issues', projectId] });
    },
  });

  const remove = useMutation({
    mutationFn: (configId: number) => deleteScanSource(projectId, configId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-sources', projectId] });
    },
  });

  return (
    <div className="card space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Scan sources</h2>
          <p className="mt-1 text-sm text-slate-600">
            Connect Veracode and SonarQube so the platform can pull findings for this project.
          </p>
        </div>
        <button onClick={() => setShowForm((v) => !v)} className="btn-secondary">
          {showForm ? 'Cancel' : 'Add / update'}
        </button>
      </div>

      <ErrorBanner error={error || scan.error || remove.error} />

      {scan.data && (
        <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
          Scan complete · created {scan.data.created} · updated {scan.data.updated} · resolved{' '}
          {scan.data.resolvedExternally} of {scan.data.total} returned by upstream
        </div>
      )}

      {isLoading && <p className="text-sm text-slate-500">Loading…</p>}

      {!isLoading && sources && sources.length === 0 && !showForm && (
        <p className="text-sm text-slate-500">No scan sources configured yet.</p>
      )}

      {sources && sources.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {sources.map((s) => (
            <li key={s.id} className="flex items-center justify-between py-3">
              <div>
                <div className="flex items-center gap-2">
                  <SourceBadge source={s.source} />
                  <code className="font-mono text-xs text-slate-700">{s.externalProjectId}</code>
                  {!s.enabled && (
                    <span className="badge bg-slate-100 text-slate-700">disabled</span>
                  )}
                </div>
                <div className="mt-0.5 font-mono text-xs text-slate-500">{s.apiBaseUrl}</div>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => scan.mutate(s.source)}
                  className="btn-primary"
                  disabled={scan.isPending || !s.enabled}
                >
                  {scan.isPending && scan.variables === s.source ? 'Scanning…' : 'Scan now'}
                </button>
                <button
                  onClick={() => {
                    if (window.confirm(`Delete scan source for ${s.source}?`)) {
                      remove.mutate(s.id);
                    }
                  }}
                  className="btn-danger"
                  disabled={remove.isPending}
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {showForm && (
        <ScanSourceForm
          projectId={projectId}
          existingSources={(sources ?? []).map((s) => s.source)}
          onDone={() => {
            setShowForm(false);
            queryClient.invalidateQueries({ queryKey: ['scan-sources', projectId] });
          }}
        />
      )}
    </div>
  );
}

interface FormProps {
  projectId: number;
  existingSources: IssueSource[];
  onDone: () => void;
}

function ScanSourceForm({ projectId, existingSources, onDone }: FormProps) {
  const [form, setForm] = useState<UpsertScanSourceRequest>({
    source: existingSources[0] ?? 'VERACODE_SCA',
    externalProjectId: '',
    apiBaseUrl: 'https://api.veracode.com',
    secretValue: '',
    enabled: true,
  });

  const mutation = useMutation({
    mutationFn: () => upsertScanSource(projectId, form),
    onSuccess: onDone,
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        mutation.mutate();
      }}
      className="space-y-3 border-t border-slate-200 pt-4"
    >
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div>
          <label className="label">Source</label>
          <select
            className="input"
            value={form.source}
            onChange={(e) => {
              const source = e.target.value as IssueSource;
              setForm({
                ...form,
                source,
                apiBaseUrl:
                  source === 'SONARQUBE'
                    ? 'https://sonar.local'
                    : source === 'VERACODE_SCA' || source === 'VERACODE_SAST'
                      ? 'https://api.veracode.com'
                      : form.apiBaseUrl,
              });
            }}
          >
            {ISSUE_SOURCES.filter((s) => s !== 'JUNIT_COVERAGE').map((s) => (
              <option key={s} value={s}>
                {s.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <p className="mt-1 text-xs text-slate-500">
            JUnit coverage is derived from SonarQube — no separate config needed.
          </p>
        </div>
        <div>
          <label className="label">External project id</label>
          <input
            className="input"
            value={form.externalProjectId}
            required
            onChange={(e) => setForm({ ...form, externalProjectId: e.target.value })}
            placeholder={
              form.source === 'SONARQUBE' ? 'project key' : 'workspace / application GUID'
            }
          />
        </div>
        <div>
          <label className="label">API base URL</label>
          <input
            className="input"
            required
            value={form.apiBaseUrl}
            onChange={(e) => setForm({ ...form, apiBaseUrl: e.target.value })}
          />
        </div>
        <div>
          <label className="label">Secret</label>
          <input
            className="input"
            type="password"
            required
            value={form.secretValue}
            onChange={(e) => setForm({ ...form, secretValue: e.target.value })}
            placeholder={
              form.source === 'SONARQUBE' ? 'Sonar user token' : '<apiId>:<apiKey> (hex:hex)'
            }
          />
          <p className="mt-1 text-xs text-slate-500">
            Stored in the SecretStore; only an opaque reference is persisted in the database.
          </p>
        </div>
        {form.source === 'SONARQUBE' && (
          <div>
            <label className="label">Coverage threshold %</label>
            <input
              type="number"
              min={0}
              max={100}
              className="input"
              value={form.coverageThresholdPct ?? ''}
              onChange={(e) =>
                setForm({
                  ...form,
                  coverageThresholdPct: e.target.value ? Number(e.target.value) : undefined,
                })
              }
              placeholder="80"
            />
          </div>
        )}
        <div>
          <label className="label">Cron (optional)</label>
          <input
            className="input"
            value={form.cronExpression ?? ''}
            onChange={(e) =>
              setForm({ ...form, cronExpression: e.target.value || undefined })
            }
            placeholder="0 0 2 * * *"
          />
        </div>
      </div>

      <ErrorBanner error={mutation.error} />

      <div className="flex justify-end gap-2 pt-2">
        <button type="button" onClick={onDone} className="btn-secondary">
          Cancel
        </button>
        <button type="submit" className="btn-primary" disabled={mutation.isPending}>
          {mutation.isPending ? 'Saving…' : 'Save scan source'}
        </button>
      </div>
    </form>
  );
}

export type { ScanSourceConfig };
