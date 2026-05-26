import { api } from './client';

export async function getKillSwitch() {
  const res = await api.get<{ killed: boolean }>('/system/auto-mode-kill-switch');
  return res.data.killed;
}

export async function setKillSwitch(killed: boolean) {
  const res = await api.post<{ killed: boolean }>('/system/auto-mode-kill-switch', {
    killed,
  });
  return res.data.killed;
}
