import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { listIssues, type Issue } from '../api/issues';
import { getProject } from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';
import { SeverityBadge } from '../components/SeverityBadge';
import { SourceBadge } from '../components/SourceBadge';
import { StatusBadge } from '../components/StatusBadge';
import { IssueDetailDrawer } from '../components/IssueDetailDrawer';
import { FixWizardModal } from '../components/FixWizardModal';
import {
  type IssueSource,
  type IssueStatus,
  type Severity,
  ISSUE_SOURCES,
  ISSUE_STATUSES,
  SEVERITIES,
} from '../api/types';

export function IssuesPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);

  const [source, setSource] = useState<IssueSource | ''>('');
  const [severity, setSeverity] = useState<Severity | ''>('');
  const [status, setStatus] = useState<IssueStatus | ''>('OPEN');
  const [filePath, setFilePath] = useState('');
  const [cve, setCve] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [drawerIssue, setDrawerIssue] = useState<Issue | null>(null);
  const [wizardOpen, setWizardOpen] = useState(false);

  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Number.isFinite(projectId),
  });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['issues', projectId, { source, severity, status, filePath, cve, dateFrom, dateTo }],
    queryFn: () =>
      listIssues(projectId, {
        source: source || undefined,
        severity: severity || undefined,
        status: status || undefined,
        filePath: filePath || undefined,
        cve: cve || undefined,
        dateFrom: dateFrom ? new Date(dateFrom).toISOString() : undefined,
        dateTo: dateTo ? new Date(dateTo).toISOString() : undefined,
        size: 100,
      }),
    enabled: Number.isFinite(projectId),
  });

  const issues = data?.content ?? [];
  const selectedIssues = useMemo(
    () => issues.filter((i) => selected.has(i.id)),
    [issues, selected],
  );

  const allOnPageSelected = issues.length > 0 && issues.every((i) => selected.has(i.id));

  const toggleAllOnPage = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        issues.forEach((i) => next.delete(i.id));
      } else {
        issues.forEach((i) => next.add(i.id));
      }
      return next;
    });
  };

  const toggleOne = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          {project && (
            <Link to={`/projects/${projectId}`} className="text-sm text-slate-500 hover:text-slate-700">
              ← {project.name}
            </Link>
          )}
          <h1 className="mt-1 text-2xl font-semibold">Issues</h1>
        </div>
        <div className="flex gap-2">
          <button onClick={() => refetch()} className="btn-secondary">
            Refresh
          </button>
          <button
            onClick={() => setWizardOpen(true)}
            disabled={selected.size === 0}
            className="btn-primary"
          >
            Fix Selected ({selected.size})
          </button>
        </div>
      </div>

      <div className="card grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
        <Select label="Source" value={source} onChange={setSource} options={ISSUE_SOURCES} />
        <Select label="Severity" value={severity} onChange={setSeverity} options={SEVERITIES} />
        <Select label="Status" value={status} onChange={setStatus} options={ISSUE_STATUSES} />
        <TextField label="File path contains" value={filePath} onChange={setFilePath} />
        <TextField label="CVE" value={cve} onChange={setCve} placeholder="e.g. CVE-2021-44228" />
        <DateField label="Created from" value={dateFrom} onChange={setDateFrom} />
        <DateField label="Created to" value={dateTo} onChange={setDateTo} />
      </div>

      <ErrorBanner error={error} />

      <div className="card overflow-hidden p-0">
        <table className="min-w-full divide-y divide-slate-200">
          <thead className="bg-slate-50">
            <tr>
              <Th className="w-10">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300"
                  checked={allOnPageSelected}
                  onChange={toggleAllOnPage}
                />
              </Th>
              <Th>Severity</Th>
              <Th>Source</Th>
              <Th>Title</Th>
              <Th>File / Component</Th>
              <Th>Status</Th>
              <Th>Last seen</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={7} className="px-6 py-4 text-sm text-slate-500">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && issues.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-10 text-center text-sm text-slate-500">
                  No issues match these filters. Try a "Scan now" from the project page, or relax
                  the filters.
                </td>
              </tr>
            )}
            {issues.map((i) => (
              <tr key={i.id} className="cursor-pointer hover:bg-slate-50">
                <Td onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300"
                    checked={selected.has(i.id)}
                    onChange={() => toggleOne(i.id)}
                  />
                </Td>
                <Td onClick={() => setDrawerIssue(i)}>
                  <SeverityBadge severity={i.severity} />
                </Td>
                <Td onClick={() => setDrawerIssue(i)}>
                  <SourceBadge source={i.source} />
                </Td>
                <Td onClick={() => setDrawerIssue(i)}>
                  <div className="font-medium">{i.title}</div>
                  {i.cve && <div className="text-xs text-slate-500">{i.cve}</div>}
                </Td>
                <Td onClick={() => setDrawerIssue(i)} className="font-mono text-xs">
                  {i.filePath ? (
                    <>
                      {i.filePath}
                      {i.lineNumber != null ? `:${i.lineNumber}` : ''}
                    </>
                  ) : i.componentName ? (
                    i.componentName
                  ) : (
                    <span className="text-slate-400">—</span>
                  )}
                </Td>
                <Td onClick={() => setDrawerIssue(i)}>
                  <StatusBadge status={i.status} />
                </Td>
                <Td onClick={() => setDrawerIssue(i)} className="text-xs text-slate-600">
                  {formatDate(i.lastSeen)}
                </Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && (
        <div className="text-xs text-slate-500">
          {data.totalElements} total · page {data.page + 1} of {Math.max(1, data.totalPages)}
        </div>
      )}

      <IssueDetailDrawer issue={drawerIssue} onClose={() => setDrawerIssue(null)} />

      <FixWizardModal
        projectId={projectId}
        issues={selectedIssues}
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
      />
    </div>
  );
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <th
      className={`px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500 ${
        className ?? ''
      }`}
    >
      {children}
    </th>
  );
}

function Td({
  children,
  className,
  onClick,
}: {
  children: React.ReactNode;
  className?: string;
  onClick?: (e: React.MouseEvent) => void;
}) {
  return (
    <td className={`px-4 py-3 align-top ${className ?? ''}`} onClick={onClick}>
      {children}
    </td>
  );
}

function Select<T extends string>({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: T | '';
  onChange: (v: T | '') => void;
  options: T[];
}) {
  return (
    <div>
      <label className="label">{label}</label>
      <select
        className="input"
        value={value}
        onChange={(e) => onChange((e.target.value as T) || '')}
      >
        <option value="">Any</option>
        {options.map((o) => (
          <option key={o} value={o}>
            {o.replace(/_/g, ' ')}
          </option>
        ))}
      </select>
    </div>
  );
}

function TextField({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div>
      <label className="label">{label}</label>
      <input
        className="input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </div>
  );
}

function DateField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <label className="label">{label}</label>
      <input
        type="date"
        className="input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
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
