import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getKillSwitch, setKillSwitch } from '../api/system';
import { ErrorBanner } from '../components/ErrorBanner';
import { PlatformCredentialsSection } from '../components/PlatformCredentialsSection';

export function SettingsPage() {
  const queryClient = useQueryClient();

  const { data: killed, isLoading, error } = useQuery({
    queryKey: ['system', 'kill-switch'],
    queryFn: getKillSwitch,
  });

  const toggle = useMutation({
    mutationFn: (v: boolean) => setKillSwitch(v),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['system', 'kill-switch'] }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="mt-1 text-sm text-slate-600">Org-wide controls.</p>
      </div>

      <ErrorBanner error={error || toggle.error} />

      <PlatformCredentialsSection />

      <div className="card space-y-3">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">Auto-mode kill switch</h2>
            <p className="mt-1 text-sm text-slate-600">
              When ON, no project's auto-mode actions fire — scheduled scans skip, the orchestrator
              refuses to auto-merge, and all auto behavior is paused. Manual fix runs still work.
              Use this when something is misbehaving and you need to halt automation across the org
              instantly. Action is audit-logged.
            </p>
          </div>
          <div className="text-right">
            {isLoading && <div className="text-sm text-slate-500">Loading…</div>}
            {!isLoading && (
              <>
                <span
                  className={`badge ${
                    killed ? 'bg-red-100 text-red-800' : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {killed ? 'AUTO-MODE HALTED' : 'AUTO-MODE ACTIVE'}
                </span>
                <div className="mt-3">
                  <button
                    onClick={() => {
                      const next = !killed;
                      const verb = next ? 'HALT auto-mode' : 'RESUME auto-mode';
                      if (window.confirm(`${verb} across the entire organization?`)) {
                        toggle.mutate(next);
                      }
                    }}
                    disabled={toggle.isPending}
                    className={killed ? 'btn-secondary' : 'btn-danger'}
                  >
                    {toggle.isPending
                      ? 'Updating…'
                      : killed
                        ? 'Resume auto-mode'
                        : 'Halt auto-mode'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <div className="card">
        <h2 className="text-lg font-semibold">Coming soon</h2>
        <ul className="mt-2 list-disc pl-6 text-sm text-slate-600">
          <li>Notification channel preferences (Teams + Email already wired backend-side)</li>
          <li>Per-org notification routing rules</li>
          <li>Wire scan sources to platform credentials (no per-source secret duplication)</li>
        </ul>
      </div>
    </div>
  );
}
