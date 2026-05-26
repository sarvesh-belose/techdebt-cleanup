import { api } from './client';
import type { PageResponse } from './projects';
import type { FixRunState } from './types';

export interface FixRun {
  id: number;
  projectId: number;
  initiatedByUserId: number;
  autoMode: boolean;
  state: FixRunState;
  branchName: string | null;
  commitSha: string | null;
  prUrl: string | null;
  safetyScore: number | null;
  safetyBlockedReason: string | null;
  failureReason: string | null;
  issueIdsJson: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface StartFixRunRequest {
  issueIds: number[];
  autoMode?: boolean;
}

export async function startFixRun(projectId: number, req: StartFixRunRequest) {
  const res = await api.post<FixRun>(`/projects/${projectId}/fix-runs`, req);
  return res.data;
}

export async function listFixRuns(projectId: number, page = 0, size = 20) {
  const res = await api.get<PageResponse<FixRun>>(`/projects/${projectId}/fix-runs`, {
    params: { page, size, sort: 'createdAt,desc' },
  });
  return res.data;
}

export async function getFixRun(projectId: number, runId: number) {
  const res = await api.get<FixRun>(`/projects/${projectId}/fix-runs/${runId}`);
  return res.data;
}

export async function abortFixRun(projectId: number, runId: number, reason?: string) {
  const res = await api.post<FixRun>(`/projects/${projectId}/fix-runs/${runId}/abort`, null, {
    params: reason ? { reason } : undefined,
  });
  return res.data;
}

export function parseIssueIds(json: string | null): number[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed.filter((n) => typeof n === 'number') : [];
  } catch {
    return [];
  }
}
