import { api } from './client';

export type CredentialKind = 'VERACODE' | 'SONARQUBE';

export interface CredentialResponse {
  kind: CredentialKind;
  baseUrl: string;
  lastValidatedAt: string | null;
  lastValidatedOk: boolean | null;
  lastValidatedMessage: string | null;
  updatedAt: string;
  updatedByUserId: number | null;
}

export interface TestResult {
  ok: boolean;
  status: string;
  message: string;
}

export interface UpsertResult {
  credential: CredentialResponse;
  validation: TestResult;
}

export async function listCredentials() {
  const res = await api.get<CredentialResponse[]>('/credentials');
  return res.data;
}

export async function testCredential(kind: CredentialKind, baseUrl: string, secret: string) {
  const res = await api.post<TestResult>(`/credentials/${kind}/test`, { baseUrl, secret });
  return res.data;
}

export async function upsertCredential(
  kind: CredentialKind,
  baseUrl: string,
  secret: string,
  saveAnyway = false,
) {
  // 400 carries the validation detail in body when validation fails without saveAnyway.
  // axios throws on non-2xx; the caller can inspect err.response?.data for the reason.
  const res = await api.put<UpsertResult>(`/credentials/${kind}`, {
    baseUrl,
    secret,
    saveAnyway,
  });
  return res.data;
}

export async function deleteCredential(kind: CredentialKind) {
  await api.delete(`/credentials/${kind}`);
}
