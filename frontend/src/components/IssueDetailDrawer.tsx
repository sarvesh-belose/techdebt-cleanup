import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { Issue } from '../api/issues';
import { markWontFix, reopenIssue } from '../api/issueLifecycle';
import { ErrorBanner } from './ErrorBanner';
import { SeverityBadge } from './SeverityBadge';
import { SourceBadge } from './SourceBadge';
import { StatusBadge } from './StatusBadge';

interface Props {
  issue: Issue | null;
  onClose: () => void;
}

export function IssueDetailDrawer({ issue, onClose }: Props) {
  const queryClient = useQueryClient();
  const [wontFixOpen, setWontFixOpen] = useState(false);
  const [reason, setReason] = useState('');

  const wontFix = useMutation({
    mutationFn: () => {
      if (!issue) throw new Error('no issue');
      return markWontFix(issue.projectId, issue.id, reason.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      setWontFixOpen(false);
      setReason('');
      onClose();
    },
  });

  const reopen = useMutation({
    mutationFn: () => {
      if (!issue) throw new Error('no issue');
      return reopenIssue(issue.projectId, issue.id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      onClose();
    },
  });

  if (!issue) return null;
  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/30" onClick={onClose} />
      <aside className="fixed right-0 top-0 z-50 flex h-full w-full max-w-xl flex-col border-l border-slate-200 bg-white shadow-2xl">
        <header className="flex items-start justify-between border-b border-slate-200 p-6">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <SourceBadge source={issue.source} />
              <SeverityBadge severity={issue.severity} />
              <StatusBadge status={issue.status} />
            </div>
            <h2 className="mt-2 text-lg font-semibold">{issue.title}</h2>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Close">
            ✕
          </button>
        </header>

        <ErrorBanner error={wontFix.error || reopen.error} className="mx-6 mt-4" />

        {issue.wontFixReason && issue.status === 'WONT_FIX' && (
          <div className="mx-6 mt-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <div className="font-medium">Won't fix</div>
            <p className="mt-1 whitespace-pre-wrap">{issue.wontFixReason}</p>
          </div>
        )}

        <div className="flex-1 overflow-y-auto p-6 text-sm">
          <DetailRow label="Issue ID">#{issue.id}</DetailRow>
          <DetailRow label="External ID">
            <code className="font-mono text-xs">{issue.externalId}</code>
          </DetailRow>
          {issue.filePath && (
            <DetailRow label="File">
              <code className="font-mono text-xs">
                {issue.filePath}
                {issue.lineNumber != null ? `:${issue.lineNumber}` : ''}
              </code>
            </DetailRow>
          )}
          {issue.cve && <DetailRow label="CVE">{issue.cve}</DetailRow>}
          {issue.cwe && <DetailRow label="CWE">{issue.cwe}</DetailRow>}
          {issue.componentName && (
            <DetailRow label="Component">
              <code className="font-mono text-xs">{issue.componentName}</code>
            </DetailRow>
          )}
          {issue.currentVersion && (
            <DetailRow label="Versions">
              <span>
                {issue.currentVersion} → <strong>{issue.safeVersion ?? '?'}</strong>
              </span>
            </DetailRow>
          )}
          {issue.transitive != null && (
            <DetailRow label="Transitive">{issue.transitive ? 'yes' : 'no'}</DetailRow>
          )}
          <DetailRow label="First seen">{formatDate(issue.firstSeen)}</DetailRow>
          <DetailRow label="Last seen">{formatDate(issue.lastSeen)}</DetailRow>
          {issue.description && (
            <div className="mt-6">
              <div className="label">Description</div>
              <p className="whitespace-pre-wrap text-slate-700">{issue.description}</p>
            </div>
          )}
        </div>

        <footer className="flex items-center justify-end gap-2 border-t border-slate-200 bg-slate-50 px-6 py-3">
          {issue.status === 'WONT_FIX' || issue.status === 'FIXED_EXTERNALLY' ? (
            <button
              onClick={() => reopen.mutate()}
              disabled={reopen.isPending}
              className="btn-secondary"
            >
              {reopen.isPending ? 'Reopening…' : 'Reopen'}
            </button>
          ) : issue.status === 'OPEN' || issue.status === 'IN_PROGRESS' ? (
            <button onClick={() => setWontFixOpen(true)} className="btn-secondary">
              Mark won't fix
            </button>
          ) : null}
          <button onClick={onClose} className="btn-primary">
            Close
          </button>
        </footer>
      </aside>

      {wontFixOpen && (
        <>
          <div className="fixed inset-0 z-[60] bg-black/40" onClick={() => setWontFixOpen(false)} />
          <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
            <div className="card w-full max-w-md">
              <h3 className="text-lg font-semibold">Mark issue won't fix</h3>
              <p className="mt-1 text-sm text-slate-600">
                Provide a reason — this is audit-logged and shown to anyone who reopens the
                issue later.
              </p>
              <textarea
                className="input mt-3"
                rows={4}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="e.g. False positive — input is sanitized upstream"
              />
              <div className="mt-4 flex justify-end gap-2">
                <button onClick={() => setWontFixOpen(false)} className="btn-secondary">
                  Cancel
                </button>
                <button
                  onClick={() => wontFix.mutate()}
                  disabled={wontFix.isPending || reason.trim().length === 0}
                  className="btn-danger"
                >
                  {wontFix.isPending ? 'Saving…' : "Mark won't fix"}
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </>
  );
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-3 gap-4 border-b border-slate-100 py-2 last:border-b-0">
      <div className="text-xs uppercase tracking-wide text-slate-500">{label}</div>
      <div className="col-span-2 text-slate-800">{children}</div>
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
