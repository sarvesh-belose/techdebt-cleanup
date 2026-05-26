import { api } from './client';
import type { IssueSource } from './types';

export interface ScanSourceConfig {
  id: number;
  projectId: number;
  source: IssueSource;
  externalProjectId: string;
  apiBaseUrl: string;
  enabled: boolean;
  cronExpression: string | null;
  coverageThresholdPct: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertScanSourceRequest {
  source: IssueSource;
  externalProjectId: string;
  apiBaseUrl: string;
  secretValue: string;
  cronExpression?: string;
  coverageThresholdPct?: number;
  enabled?: boolean;
}

export async function listScanSources(projectId: number) {
  const res = await api.get<ScanSourceConfig[]>(`/projects/${projectId}/scan-sources`);
  return res.data;
}

export async function upsertScanSource(projectId: number, req: UpsertScanSourceRequest) {
  const res = await api.put<ScanSourceConfig>(`/projects/${projectId}/scan-sources`, req);
  return res.data;
}

export async function deleteScanSource(projectId: number, configId: number) {
  await api.delete(`/projects/${projectId}/scan-sources/${configId}`);
}
