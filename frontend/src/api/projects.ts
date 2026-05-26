import { api } from './client';

export interface Project {
  id: number;
  name: string;
  description: string | null;
  azureRepoUrl: string;
  defaultBranch: string;
  ownerUserId: number;
  autoModeEnabled: boolean;
  archived: boolean;
  safetyThreshold: number;
  autoRateLimitPerHour: number;
  autoPolicyJson: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  azureRepoUrl: string;
  defaultBranch?: string;
}

export interface UpdateProjectRequest {
  description?: string;
  azureRepoUrl?: string;
  defaultBranch?: string;
  autoModeEnabled?: boolean;
  safetyThreshold?: number;
  autoRateLimitPerHour?: number;
  autoPolicyJson?: string | null;
}

export async function listProjects(opts: { includeArchived?: boolean; page?: number; size?: number } = {}) {
  const res = await api.get<PageResponse<Project>>('/projects', {
    params: {
      includeArchived: opts.includeArchived ?? false,
      page: opts.page ?? 0,
      size: opts.size ?? 20,
    },
  });
  return res.data;
}

export async function getProject(id: number) {
  const res = await api.get<Project>(`/projects/${id}`);
  return res.data;
}

export async function createProject(req: CreateProjectRequest) {
  const res = await api.post<Project>('/projects', req);
  return res.data;
}

export async function updateProject(id: number, req: UpdateProjectRequest) {
  const res = await api.put<Project>(`/projects/${id}`, req);
  return res.data;
}

export async function archiveProject(id: number) {
  const res = await api.post<Project>(`/projects/${id}/archive`);
  return res.data;
}

export async function unarchiveProject(id: number) {
  const res = await api.post<Project>(`/projects/${id}/unarchive`);
  return res.data;
}
