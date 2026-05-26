import { Navigate, useLocation } from 'react-router-dom';
import { type ReactNode, useEffect } from 'react';
import { useAuth } from '../auth/AuthProvider';
import { configureAuthTokenProvider } from '../api/client';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, getAccessToken } = useAuth();
  const location = useLocation();

  useEffect(() => {
    configureAuthTokenProvider(getAccessToken);
  }, [getAccessToken]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}
