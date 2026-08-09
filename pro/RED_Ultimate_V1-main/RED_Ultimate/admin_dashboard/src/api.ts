const ACCESS_KEY = 'red_admin_access';
const REFRESH_KEY = 'red_admin_refresh';

export const authStore = {
  access: () => sessionStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  set(access: string, refresh?: string) {
    sessionStorage.setItem(ACCESS_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    sessionStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    window.dispatchEvent(new Event('younes:auth-expired'));
  }
};

async function rotate(): Promise<boolean> {
  const refreshToken = authStore.refresh();
  if (!refreshToken) { authStore.clear(); return false; }
  try {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    if (!response.ok) { authStore.clear(); return false; }
    const data = await response.json();
    if (!data.accessToken || !data.refreshToken) { authStore.clear(); return false; }
    authStore.set(data.accessToken, data.refreshToken);
    return true;
  } catch {
    // Keep the refresh token during a temporary network outage; it may still be valid.
    return false;
  }
}

export async function apiFetch(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', headers.get('Content-Type') || 'application/json');
  const access = authStore.access();
  if (access) headers.set('Authorization', `Bearer ${access}`);
  const response = await fetch(path, { ...init, headers });
  if (response.status === 401 && retry && await rotate()) return apiFetch(path, init, false);
  return response;
}

export async function adminLogin(username: string, password: string) {
  const response = await fetch('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.user?.role !== 'ADMIN') throw new Error(data.error || 'بيانات المسؤول غير صحيحة');
  authStore.set(data.accessToken, data.refreshToken);
  return data;
}
