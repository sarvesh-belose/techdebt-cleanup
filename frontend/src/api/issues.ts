import { api } from './client';
import type { PageResponse } from './projects';
import type { IssueSource, IssueStatus, Severity } from './types';

export interface Issue {
  id: number;
  projectId: number;
  source: IssueSource;
  externalId: string;
  severity: Severity;
  status: IssueStatus;
  title: string;
  description: string | null;
  filePath: string | null;
  lineNumber: number | null;
  cwe: string | null;
  cve: string | null;
  componentName: string | null;
  currentVersion: string | null;
  safeVersion: string | null;
  transitive: boolean | null;
  wontFixReason: string | null;
  firstSeen: string;
  lastSeen: string;
}

export interface IssueFilters {
  source?: IssueSource;
  severity?: Severity;
  status?: IssueStatus;
  filePath?: string;
  cve?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function listIssues(projectId: number, filters: IssueFilters = {}) {
  const params: Record<string, unknown> = {
    page: filters.page ?? 0,
    size: filters.size ?? 50,
  };
  if (filters.source) params.source = filters.source;
  if (filters.severity) params.severity = filters.severity;
  if (filters.status) params.status = filters.status;
  if (filters.filePath) params.filePath = filters.filePath;
  if (filters.cve) params.cve = filters.cve;
  if (filters.dateFrom) params.dateFrom = filters.dateFrom;
  if (filters.dateTo) params.dateTo = filters.dateTo;
  if (filters.sort) params.sort = filters.sort;
  const res = await api.get<PageResponse<Issue>>(`/projects/${projectId}/issues`, { params });
  return res.data;
}
