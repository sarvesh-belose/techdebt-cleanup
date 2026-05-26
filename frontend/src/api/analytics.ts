import { api } from './client';

export interface OrgRollupRow {
  projectId: number;
  projectName: string;
  source: string;
  severity: string;
  status: string;
  count: number;
}

export interface FixSuccessMetrics {
  since: string;
  totalRuns: number;
  mergedRuns: number;
  failedRuns: number;
  safetyBlockedRuns: number;
  prOpenedRuns: number;
  successRate: number;
  safetyBlockRate: number;
  averageRefixAttempts: number | null;
  issuesIngestedBySource: Record<string, number>;
  openIssuesBySource: Record<string, number>;
  mttrSecondsBySource: Record<string, number>;
  notes: string[];
}

export async function orgRollup() {
  const res = await api.get<OrgRollupRow[]>('/analytics/org-rollup');
  return res.data;
}

export async function fixSuccessMetrics(sinceDays = 90) {
  const res = await api.get<FixSuccessMetrics>('/analytics/metrics', {
    params: { sinceDays },
  });
  return res.data;
}
