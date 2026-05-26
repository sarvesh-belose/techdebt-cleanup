import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import type { Issue } from '../api/issues';
import { startFixRun } from '../api/fixRuns';
import { ErrorBanner } from './ErrorBanner';
import { SeverityBadge } from './SeverityBadge';
import { SourceBadge } from './SourceBadge';

interface Props {
  projectId: number;
  issues: Issue[];
  open: boolean;
  onClose: () => void;
}

export function FixWizardModal({ projectId, issues, open, onClose }: Props) {
  const navigate = useNavigate();
  const [autoMode, setAutoMode] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      startFixRun(projectId, {
        issueIds: issues.map((i) => i.id),
        autoMode,
      }),
    onSuccess: (run) => {
      onClose();
      navigate(`/projects/${projectId}/runs/${run.id}`);
    },
  });

  if (!open) return null;
  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/40" onClick={() => !mutation.isPending && onClose()} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div className="card max-h-[90vh] w-full max-w-2xl overflow-hidden p-0">
          <div className="border-b border-slate-200 p-6">
            <h2 className="text-lg font-semibold">Fix Wizard</h2>
            <p className="mt-1 text-sm text-slate-600">
              Review the {issues.length} selected issue{issues.length === 1 ? '' : 's'}, then start
              the Fix Run. The orchestrator will clone the repo, generate a fix, run the Safety
              Guard, and (in later sprints) trigger the build.
            </p>
          </div>

          <ErrorBanner error={mutation.error} className="mx-6 mt-4" />

          <div className="max-h-80 overflow-y-auto px-6 py-4">
            <ul className="divide-y divide-slate-100">
              {issues.map((i) => (
                <li key={i.id} className="py-2">
                  <div className="flex items-center gap-2">
                    <SeverityBadge severity={i.severity} />
                    <SourceBadge source={i.source} />
                    <span className="text-sm font-medium">{i.title}</span>
                  </div>
                  {i.filePath && (
                    <div className="mt-0.5 font-mono text-xs text-slate-500">
                      {i.filePath}
                      {i.lineNumber != null ? `:${i.lineNumber}` : ''}
                    </div>
                  )}
                  {i.componentName && (
                    <div className="mt-0.5 font-mono text-xs text-slate-500">
                      {i.componentName}
                      {i.currentVersion ? ` ${i.currentVersion} → ${i.safeVersion ?? '?'}` : ''}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div className="border-t border-slate-200 px-6 py-4">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={autoMode}
                onChange={(e) => setAutoMode(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300"
              />
              Run in auto mode (skip manual review gates — Sprint 6 will fully wire this)
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-4">
            <button onClick={onClose} className="btn-secondary" disabled={mutation.isPending}>
              Cancel
            </button>
            <button
              onClick={() => mutation.mutate()}
              className="btn-primary"
              disabled={mutation.isPending || issues.length === 0}
            >
              {mutation.isPending ? 'Starting…' : `Start Fix Run (${issues.length})`}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
