import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  type CredentialKind,
  type CredentialResponse,
  type TestResult,
  deleteCredential,
  listCredentials,
  testCredential,
  upsertCredential,
} from '../api/credentials';
import { ErrorBanner } from './ErrorBanner';

const KIND_LABEL: Record<CredentialKind, string> = {
  VERACODE: 'Veracode',
  SONARQUBE: 'SonarQube',
};

const KIND_HINT: Record<CredentialKind, { url: string; secret: string }> = {
  VERACODE: {
    url: 'https://api.veracode.com',
    secret: 'apiId:apiKey (both hex, colon-separated)',
  },
  SONARQUBE: {
    url: 'https://sonar.acme.com',
    secret: 'Sonar user token',
  },
};

export function PlatformCredentialsSection() {
  const { data: credentials, isLoading, error } = useQuery({
    queryKey: ['credentials'],
    queryFn: listCredentials,
  });

  const byKind = useMemo(() => {
    const m: Partial<Record<CredentialKind, CredentialResponse>> = {};
    (credentials ?? []).forEach((c) => (m[c.kind] = c));
    return m;
  }, [credentials]);

  return (
    <div className="card space-y-4">
      <div>
        <h2 className="text-lg font-semibold">Platform credentials</h2>
        <p className="mt-1 text-sm text-slate-600">
          Paste your Veracode and SonarQube credentials once. The platform validates them
          live and stores them in the SecretStore (only an opaque reference lives in the
          DB). Rotate by saving new values at any time.
        </p>
      </div>

      <ErrorBanner error={error} />

      {isLoading ? (
        <div className="text-sm text-slate-500">Loading…</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <CredentialCard kind="VERACODE" credential={byKind.VERACODE} />
          <CredentialCard kind="SONARQUBE" credential={byKind.SONARQUBE} />
        </div>
      )}
    </div>
  );
}

function CredentialCard({
  kind,
  credential,
}: {
  kind: CredentialKind;
  credential: CredentialResponse | undefined;
}) {
  const queryClient = useQueryClient();
  const [baseUrl, setBaseUrl] = useState(credential?.baseUrl ?? KIND_HINT[kind].url);
  const [secret, setSecret] = useState('');
  const [showSecret, setShowSecret] = useState(false);
  const [testStatus, setTestStatus] = useState<TestResult | null>(null);
  const [saveError, setSaveError] = useState<{ message: string; status: string } | null>(null);

  const testMut = useMutation({
    mutationFn: () => testCredential(kind, baseUrl, secret),
    onSuccess: (r) => {
      setTestStatus(r);
      setSaveError(null);
    },
  });

  const saveMut = useMutation({
    mutationFn: (saveAnyway: boolean) => upsertCredential(kind, baseUrl, secret, saveAnyway),
    onSuccess: (r) => {
      setTestStatus(r.validation);
      setSaveError(null);
      setSecret('');
      queryClient.invalidateQueries({ queryKey: ['credentials'] });
    },
    onError: (err: unknown) => {
      if (axios.isAxiosError(err) && err.response?.status === 400) {
        const data = err.response.data as Record<string, unknown>;
        setSaveError({
          message: String(data.message ?? 'Validation failed'),
          status: String(data.status ?? 'BAD_REQUEST'),
        });
        setTestStatus({
          ok: false,
          status: String(data.status ?? 'BAD_REQUEST'),
          message: String(data.message ?? 'Validation failed'),
        });
      } else {
        setSaveError({ message: String(err), status: 'NETWORK' });
      }
    },
  });

  const removeMut = useMutation({
    mutationFn: () => deleteCredential(kind),
    onSuccess: () => {
      setTestStatus(null);
      setSaveError(null);
      setSecret('');
      setBaseUrl(KIND_HINT[kind].url);
      queryClient.invalidateQueries({ queryKey: ['credentials'] });
    },
  });

  const canTest = baseUrl.trim().length > 0 && secret.trim().length > 0;

  return (
    <div className="rounded-lg border border-slate-200 p-4">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-semibold">{KIND_LABEL[kind]}</h3>
          {credential ? (
            <ValidationBadge
              ok={credential.lastValidatedOk}
              at={credential.lastValidatedAt}
              message={credential.lastValidatedMessage}
            />
          ) : (
            <p className="mt-1 text-xs text-slate-500">Not configured</p>
          )}
        </div>
        {credential && (
          <button
            onClick={() => {
              if (window.confirm(`Remove the saved ${KIND_LABEL[kind]} credential?`)) {
                removeMut.mutate();
              }
            }}
            className="text-xs text-red-700 hover:underline"
            disabled={removeMut.isPending}
          >
            Remove
          </button>
        )}
      </div>

      <div className="mt-3 space-y-3">
        <div>
          <label className="label">Base URL</label>
          <input
            className="input"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder={KIND_HINT[kind].url}
          />
        </div>
        <div>
          <label className="label">
            {kind === 'VERACODE' ? 'Credentials' : 'Token'}{' '}
            <span className="text-xs font-normal text-slate-500">
              ({KIND_HINT[kind].secret})
            </span>
          </label>
          <div className="relative">
            <input
              className="input pr-20"
              type={showSecret ? 'text' : 'password'}
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              placeholder={credential ? '(unchanged — paste new value to rotate)' : KIND_HINT[kind].secret}
              autoComplete="new-password"
            />
            <button
              type="button"
              onClick={() => setShowSecret((v) => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
            >
              {showSecret ? 'Hide' : 'Show'}
            </button>
          </div>
        </div>

        {testStatus && (
          <ResultBanner result={testStatus} />
        )}

        {saveError && (
          <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
            <div className="font-medium">Save rejected</div>
            <p className="mt-1">{saveError.message}</p>
            <button
              onClick={() => saveMut.mutate(true)}
              className="mt-2 text-xs text-amber-900 underline hover:text-amber-700"
              disabled={!canTest || saveMut.isPending}
            >
              Save anyway (audit-logged)
            </button>
          </div>
        )}

        <div className="flex gap-2 pt-1">
          <button
            onClick={() => testMut.mutate()}
            className="btn-secondary"
            disabled={!canTest || testMut.isPending}
          >
            {testMut.isPending ? 'Testing…' : 'Test'}
          </button>
          <button
            onClick={() => saveMut.mutate(false)}
            className="btn-primary"
            disabled={!canTest || saveMut.isPending}
          >
            {saveMut.isPending ? 'Saving…' : credential ? 'Update' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  );
}

function ValidationBadge({
  ok,
  at,
  message,
}: {
  ok: boolean | null;
  at: string | null;
  message: string | null;
}) {
  if (ok === null) {
    return <p className="mt-1 text-xs text-slate-500">Never validated</p>;
  }
  const cls = ok
    ? 'bg-emerald-100 text-emerald-800'
    : 'bg-amber-100 text-amber-900';
  return (
    <div className="mt-1">
      <span className={`badge ${cls}`}>{ok ? 'OK' : 'VALIDATION WARNING'}</span>
      <span className="ml-2 text-xs text-slate-500">
        last checked {at ? new Date(at).toLocaleString() : '—'}
      </span>
      {message && <p className="mt-1 text-xs text-slate-600">{message}</p>}
    </div>
  );
}

function ResultBanner({ result }: { result: TestResult }) {
  if (result.ok) {
    return (
      <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
        <div className="font-medium">✓ Validated</div>
        <p className="mt-1">{result.message}</p>
      </div>
    );
  }
  return (
    <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-900">
      <div className="font-medium">✗ {result.status}</div>
      <p className="mt-1">{result.message}</p>
    </div>
  );
}
