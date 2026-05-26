import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { config } from '../config';

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) navigate('/', { replace: true });
  }, [isAuthenticated, navigate]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100">
      <div className="card w-full max-w-md">
        <h1 className="text-xl font-semibold">TechDebt Cleanup</h1>
        <p className="mt-2 text-sm text-slate-600">
          Sign in with your corporate Azure AD account to continue.
        </p>
        <button
          onClick={() => signIn()}
          disabled={config.devBypass}
          className="btn-primary mt-6 w-full"
        >
          {config.devBypass ? 'Dev bypass active — auto signed-in' : 'Sign in with Microsoft'}
        </button>
        {config.devBypass && (
          <div className="mt-4 rounded-md bg-amber-50 p-3 text-xs text-amber-900">
            Dev bypass is on. Set <code className="font-mono">VITE_DEV_BYPASS=false</code> and
            configure Azure AD env vars for real auth.
          </div>
        )}
      </div>
    </div>
  );
}
