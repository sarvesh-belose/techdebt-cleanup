import { api } from './client';
import type { PageResponse } from './projects';
import type { Severity } from './types';

export type NotificationEventType =
  | 'SCAN_COMPLETED'
  | 'SCAN_FAILED'
  | 'FIX_RUN_CREATED'
  | 'PR_OPENED'
  | 'MERGED'
  | 'SAFETY_BLOCKED'
  | 'RUN_FAILED'
  | 'AUTO_MODE_KILL_SWITCH_TOGGLED';

export interface Notification {
  id: number;
  userId: number | null;
  projectId: number | null;
  fixRunId: number | null;
  eventType: NotificationEventType;
  severity: Severity;
  title: string;
  body: string | null;
  deeplink: string | null;
  readAt: string | null;
  createdAt: string;
}

export async function listNotifications(page = 0, size = 50) {
  const res = await api.get<PageResponse<Notification>>('/notifications', {
    params: { page, size },
  });
  return res.data;
}

export async function unreadCount() {
  const res = await api.get<{ unread: number }>('/notifications/unread-count');
  return res.data.unread;
}

export async function markRead(id: number) {
  const res = await api.post<Notification>(`/notifications/${id}/read`);
  return res.data;
}

export async function markAllRead() {
  const res = await api.post<{ marked: number }>('/notifications/read-all');
  return res.data.marked;
}
