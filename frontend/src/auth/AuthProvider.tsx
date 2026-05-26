import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { config } from '../config';
import {
  PublicClientApplication,
  type AccountInfo,
  type AuthenticationResult,
  EventType,
} from '@azure/msal-browser';

export interface AuthUser {
  upn: string;
  name: string;
  email: string;
}

interface AuthContextValue {
  isAuthenticated: boolean;
  user: AuthUser | null;
  getAccessToken: () => Promise<string | null>;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const devUser: AuthUser = {
  upn: 'dev@local',
  name: 'Local Dev',
  email: 'dev@local',
};

let msalInstance: PublicClientApplication | null = null;

function getMsal(): PublicClientApplication {
  if (msalInstance) return msalInstance;
  msalInstance = new PublicClientApplication({
    auth: {
      clientId: config.azureAd.clientId,
      authority: `https://login.microsoftonline.com/${config.azureAd.tenantId}`,
      redirectUri: config.azureAd.redirectUri,
    },
    cache: {
      cacheLocation: 'sessionStorage',
    },
  });
  return msalInstance;
}

function accountToUser(account: AccountInfo): AuthUser {
  return {
    upn: account.username ?? '',
    name: account.name ?? account.username ?? '',
    email: account.username ?? '',
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(config.devBypass ? devUser : null);
  const [initialized, setInitialized] = useState<boolean>(config.devBypass);

  useEffect(() => {
    if (config.devBypass) return;
    const msal = getMsal();
    msal
      .initialize()
      .then(() => msal.handleRedirectPromise())
      .then((result: AuthenticationResult | null) => {
        if (result?.account) {
          setUser(accountToUser(result.account));
        } else {
          const accounts = msal.getAllAccounts();
          if (accounts.length > 0) {
            setUser(accountToUser(accounts[0]));
          }
        }
      })
      .catch((err) => {
        // eslint-disable-next-line no-console
        console.error('MSAL init failed', err);
      })
      .finally(() => setInitialized(true));

    const callbackId = msal.addEventCallback((message) => {
      if (message.eventType === EventType.LOGIN_SUCCESS && message.payload) {
        const payload = message.payload as AuthenticationResult;
        if (payload.account) setUser(accountToUser(payload.account));
      } else if (message.eventType === EventType.LOGOUT_SUCCESS) {
        setUser(null);
      }
    });
    return () => {
      if (callbackId) msal.removeEventCallback(callbackId);
    };
  }, []);

  const value = useMemo<AuthContextValue>(() => {
    if (config.devBypass) {
      return {
        isAuthenticated: true,
        user: devUser,
        getAccessToken: async () => null,
        signIn: async () => {},
        signOut: async () => {},
      };
    }
    const msal = getMsal();
    return {
      isAuthenticated: !!user,
      user,
      getAccessToken: async () => {
        const accounts = msal.getAllAccounts();
        if (accounts.length === 0) return null;
        try {
          const result = await msal.acquireTokenSilent({
            account: accounts[0],
            scopes: config.azureAd.apiScope ? [config.azureAd.apiScope] : [],
          });
          return result.accessToken;
        } catch {
          return null;
        }
      },
      signIn: async () => {
        await msal.loginRedirect({
          scopes: config.azureAd.apiScope ? [config.azureAd.apiScope] : [],
        });
      },
      signOut: async () => {
        await msal.logoutRedirect();
      },
    };
  }, [user]);

  if (!initialized) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="text-slate-500">Initializing…</div>
      </div>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
