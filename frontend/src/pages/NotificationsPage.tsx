import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listNotifications, markAllRead, markRead } from '../api/notifications';
import { ErrorBanner } from '../components/ErrorBanner';

export function NotificationsPage() {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['notifications', 'page'],
    queryFn: () => listNotifications(0, 200),
    refetchInterval: 30_000,
  });

  const markOne = useMutation({
    mutationFn: (id: number) => markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
  const markAll = useMutation({
    mutationFn: () => markAllRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Notifications</h1>
          <p className="mt-1 text-sm text-slate-600">
            Everything the platform did or noticed, for you and the projects you can see.
          </p>
        </div>
        <button
          onClick={() => markAll.mutate()}
          disabled={markAll.isPending}
          className="btn-secondary"
        >
          Mark all read
        </button>
      </div>

      <ErrorBanner error={error || markOne.error || markAll.error} />

      <div className="card overflow-hidden p-0">
        {isLoading && <div className="px-6 py-4 text-sm text-slate-500">Loading…</div>}
        {data && data.content.length === 0 && (
          <div className="px-6 py-12 text-center text-sm text-slate-500">No notifications.</div>
        )}
        <ul className="divide-y divide-slate-100">
          {data?.content.map((n) => (
            <li key={n.id} className={`px-6 py-3 ${n.readAt ? '' : 'bg-brand-50/40'}`}>
              <div className="flex items-start justify-between gap-4">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    {!n.readAt && <span className="h-2 w-2 rounded-full bg-brand-600" />}
                    <div className="text-sm font-medium">{n.title}</div>
                    <span className="badge bg-slate-100 text-slate-700">{n.eventType}</span>
                  </div>
                  {n.body && <p className="mt-1 text-sm text-slate-600">{n.body}</p>}
                  <div className="mt-1 text-xs text-slate-400">
                    {new Date(n.createdAt).toLocaleString()}
                    {n.fixRunId && ` · run #${n.fixRunId}`}
                    {n.projectId && ` · project #${n.projectId}`}
                  </div>
                </div>
                <div className="flex flex-col items-end gap-1">
                  {!n.readAt && (
                    <button
                      onClick={() => markOne.mutate(n.id)}
                      className="text-xs text-brand-700 hover:underline"
                    >
                      Mark read
                    </button>
                  )}
                  {n.deeplink && (
                    <a
                      href={n.deeplink}
                      target={n.deeplink.startsWith('http') ? '_blank' : undefined}
                      rel={n.deeplink.startsWith('http') ? 'noreferrer' : undefined}
                      className="text-xs text-brand-700 hover:underline"
                    >
                      Open →
                    </a>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
