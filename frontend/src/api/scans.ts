import { api } from './client';
import type { IssueSource } from './types';

export interface IngestionResult {
  projectId: number;
  source: string;
  created: number;
  updated: number;
  resolvedExternally: number;
  total: number;
}

export async function triggerScan(projectId: number, source: IssueSource) {
  const res = await api.post<IngestionResult>(`/projects/${projectId}/scans`, null, {
    params: { source },
  });
  return res.data;
}
