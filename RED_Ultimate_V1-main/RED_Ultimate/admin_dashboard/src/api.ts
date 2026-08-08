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
  },
  isAuthenticated: () => !!sessionStorage.getItem(ACCESS_KEY)
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

export async function adminLogout() {
  const refreshToken = authStore.refresh();
  if (refreshToken) {
    await apiFetch('/api/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    }).catch(() => {}); // Best-effort; clear tokens regardless
  }
  authStore.clear();
}

// ━━━━━━━━━━━━ 🔔 Notifications API ━━━━━━━━━━━━
export async function getNotifications(page = 0, size = 50, type?: string) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (type) params.set('type', type);
  const res = await apiFetch(`/api/notifications?${params}`);
  return res.json();
}

export async function markNotificationRead(id: string) {
  await apiFetch(`/api/notifications/${id}/read`, { method: 'PUT' });
}

export async function markAllNotificationsRead() {
  await apiFetch('/api/notifications/read-all', { method: 'PUT' });
}

export async function getUnreadCount() {
  const res = await apiFetch('/api/notifications/unread-count');
  return res.json();
}

// ━━━━━━━━━━━━ 🟢 Status & Privacy API ━━━━━━━━━━━━
export async function getUserStatus(userId: string) {
  const res = await apiFetch(`/api/social/status/${userId}`);
  return res.json();
}

export async function updateMyStatus(type: string, customText?: string, visibleTo = 'EVERYONE') {
  const res = await apiFetch('/api/social/status', {
    method: 'PUT',
    body: JSON.stringify({ type, customText, visibleTo })
  });
  return res.json();
}

export async function getPrivacySettings() {
  const res = await apiFetch('/api/social/privacy');
  return res.json();
}

export async function updatePrivacySettings(settings: Record<string, string>) {
  const res = await apiFetch('/api/social/privacy', {
    method: 'PUT',
    body: JSON.stringify(settings)
  });
  return res.json();
}

export async function getOnlineContacts() {
  const res = await apiFetch('/api/social/online-contacts');
  return res.json();
}
