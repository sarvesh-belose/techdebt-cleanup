import { api } from './client';
import type { Issue } from './issues';

export async function markWontFix(projectId: number, issueId: number, reason: string) {
  const res = await api.post<Issue>(`/projects/${projectId}/issues/${issueId}/wont-fix`, {
    reason,
  });
  return res.data;
}

export async function reopenIssue(projectId: number, issueId: number) {
  const res = await api.post<Issue>(`/projects/${projectId}/issues/${issueId}/reopen`);
  return res.data;
}
