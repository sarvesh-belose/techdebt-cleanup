import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listNotifications, markAllRead, markRead, unreadCount } from '../api/notifications';

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const queryClient = useQueryClient();

  const { data: unread = 0 } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: unreadCount,
    refetchInterval: 30_000,
  });

  const { data: recent } = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => listNotifications(0, 8),
    enabled: open,
  });

  const markOne = useMutation({
    mutationFn: (id: number) => markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAll = useMutation({
    mutationFn: () => markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative inline-flex h-9 w-9 items-center justify-center rounded-full hover:bg-slate-100"
        aria-label="Notifications"
      >
        <span aria-hidden>🔔</span>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-96 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-2">
            <div className="text-sm font-medium">Notifications</div>
            <button
              onClick={() => markAll.mutate()}
              disabled={unread === 0 || markAll.isPending}
              className="text-xs text-brand-700 hover:underline disabled:cursor-not-allowed disabled:text-slate-400"
            >
              Mark all read
            </button>
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {!recent && <div className="px-4 py-4 text-sm text-slate-500">Loading…</div>}
            {recent && recent.content.length === 0 && (
              <div className="px-4 py-6 text-center text-sm text-slate-500">
                Nothing yet — you're all caught up.
              </div>
            )}
            {recent?.content.map((n) => (
              <button
                key={n.id}
                onClick={() => {
                  if (!n.readAt) markOne.mutate(n.id);
                  if (n.deeplink) {
                    if (n.deeplink.startsWith('http')) {
                      window.open(n.deeplink, '_blank', 'noreferrer');
                    } else {
                      window.location.assign(n.deeplink);
                    }
                  }
                  setOpen(false);
                }}
                className={`block w-full border-b border-slate-100 px-4 py-3 text-left transition hover:bg-slate-50 ${
                  n.readAt ? 'opacity-70' : ''
                }`}
              >
                <div className="flex items-start gap-2">
                  {!n.readAt && (
                    <span className="mt-1 h-2 w-2 flex-shrink-0 rounded-full bg-brand-600" />
                  )}
                  <div className="flex-1">
                    <div className="text-sm font-medium">{n.title}</div>
                    {n.body && <div className="mt-0.5 line-clamp-2 text-xs text-slate-600">{n.body}</div>}
                    <div className="mt-1 text-[11px] uppercase tracking-wide text-slate-400">
                      {n.eventType} · {formatRelative(n.createdAt)}
                    </div>
                  </div>
                </div>
              </button>
            ))}
          </div>
          <div className="border-t border-slate-200 bg-slate-50 px-4 py-2 text-right">
            <Link
              to="/notifications"
              onClick={() => setOpen(false)}
              className="text-xs text-brand-700 hover:underline"
            >
              View all
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function formatRelative(iso: string): string {
  try {
    const diff = Date.now() - new Date(iso).getTime();
    if (diff < 60_000) return 'just now';
    if (diff < 3_600_000) return Math.floor(diff / 60_000) + 'm ago';
    if (diff < 86_400_000) return Math.floor(diff / 3_600_000) + 'h ago';
    return Math.floor(diff / 86_400_000) + 'd ago';
  } catch {
    return iso;
  }
}
