import axios, { type AxiosInstance } from 'axios';

let getTokenFn: () => Promise<string | null> = async () => null;

export function configureAuthTokenProvider(fn: () => Promise<string | null>) {
  getTokenFn = fn;
}

function genCorrelationId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

export const api: AxiosInstance = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use(async (cfg) => {
  cfg.headers = cfg.headers ?? {};
  cfg.headers['X-Correlation-Id'] = genCorrelationId();
  const token = await getTokenFn();
  if (token) {
    cfg.headers.Authorization = `Bearer ${token}`;
  }
  return cfg;
});

api.interceptors.response.use(
  (resp) => resp,
  (err) => {
    if (err.response?.status === 401) {
      // eslint-disable-next-line no-console
      console.warn('Unauthorized request — auth state may have expired');
    }
    return Promise.reject(err);
  },
);
