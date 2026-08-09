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

// ━━━━━━━━━━━━━━━━ 🔐 Auth ━━━━━━━━━━━━━━━━
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

// ━━━━━━━━━━━━━━━━ 🔔 Notifications ━━━━━━━━━━━━━━━━
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

// ━━━━━━━━━━━━━━━━ 🟢 Status & Privacy ━━━━━━━━━━━━━━━━
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

// ━━━━━━━━━━━━━━━━ 👥 Users Management ━━━━━━━━━━━━━━━━
export async function getUsers(params: {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
  role?: string;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
} = {}) {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.status) searchParams.set('status', params.status);
  if (params.search) searchParams.set('search', params.search);
  if (params.role) searchParams.set('role', params.role);
  if (params.sortBy) searchParams.set('sortBy', params.sortBy);
  if (params.sortDir) searchParams.set('sortDir', params.sortDir);
  const res = await apiFetch(`/api/admin/users?${searchParams}`);
  return res.json();
}

export async function getUserDetail(userId: string) {
  const res = await apiFetch(`/api/admin/users/${userId}`);
  return res.json();
}

export async function approveUser(userId: string) {
  const res = await apiFetch(`/api/admin/users/${userId}/approve`, { method: 'POST' });
  return res.json();
}

export async function rejectUser(userId: string, reason?: string) {
  const res = await apiFetch(`/api/admin/users/${userId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

export async function banUser(userId: string, reason: string, durationDays?: number) {
  const res = await apiFetch(`/api/admin/users/${userId}/ban`, {
    method: 'POST',
    body: JSON.stringify({ reason, durationDays })
  });
  return res.json();
}

export async function unbanUser(userId: string) {
  const res = await apiFetch(`/api/admin/users/${userId}/unban`, { method: 'POST' });
  return res.json();
}

export async function promoteUser(userId: string, role: 'USER' | 'ADMIN' | 'MODERATOR') {
  const res = await apiFetch(`/api/admin/users/${userId}/role`, {
    method: 'PUT',
    body: JSON.stringify({ role })
  });
  return res.json();
}

export async function deleteUser(userId: string, hard = false) {
  const res = await apiFetch(`/api/admin/users/${userId}${hard ? '?hard=true' : ''}`, {
    method: 'DELETE'
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📊 Dashboard & Analytics ━━━━━━━━━━━━━━━━
export async function getDashboardSummary() {
  const res = await apiFetch('/api/admin/dashboard/summary');
  return res.json();
}

export async function getSystemAnalytics(startDate: string, endDate: string) {
  const res = await apiFetch(`/api/admin/analytics?start=${startDate}&end=${endDate}`);
  return res.json();
}

export async function getSystemHealth() {
  const res = await apiFetch('/api/admin/health');
  return res.json();
}

export async function getRealtimeMetrics() {
  const res = await apiFetch('/api/admin/metrics/realtime');
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📞 Calls Management ━━━━━━━━━━━━━━━━
export async function getCallHistory(params: {
  page?: number;
  size?: number;
  type?: string;
  status?: string;
  userId?: string;
  startDate?: string;
  endDate?: string;
} = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/calls?${searchParams}`);
  return res.json();
}

export async function terminateCall(callId: string, reason: string) {
  const res = await apiFetch(`/api/admin/calls/${callId}/terminate`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📱 DINSTAR Management ━━━━━━━━━━━━━━━━
export async function getDinstarPorts() {
  const res = await apiFetch('/api/admin/dinstar/ports');
  return res.json();
}

export async function toggleDinstarPort(portId: number, enabled: boolean) {
  const res = await apiFetch(`/api/admin/dinstar/ports/${portId}/toggle`, {
    method: 'POST',
    body: JSON.stringify({ enabled })
  });
  return res.json();
}

export async function resetDinstarBalance(portId: number, amount: number) {
  const res = await apiFetch(`/api/admin/dinstar/ports/${portId}/balance`, {
    method: 'POST',
    body: JSON.stringify({ amount })
  });
  return res.json();
}

export async function getDinstarStats() {
  const res = await apiFetch('/api/admin/dinstar/stats');
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 👥 Groups Management ━━━━━━━━━━━━━━━━
export async function getGroups(params: { page?: number; size?: number; search?: string } = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/groups?${searchParams}`);
  return res.json();
}

export async function deleteGroup(groupId: string, reason: string) {
  const res = await apiFetch(`/api/admin/groups/${groupId}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📝 Reports & Moderation ━━━━━━━━━━━━━━━━
export async function getReports(params: {
  page?: number;
  size?: number;
  status?: string;
  category?: string;
  assignedToMe?: boolean;
} = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/reports?${searchParams}`);
  return res.json();
}

export async function resolveReport(reportId: string, resolution: string, notes?: string) {
  const res = await apiFetch(`/api/admin/reports/${reportId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, notes })
  });
  return res.json();
}

export async function dismissReport(reportId: string, notes?: string) {
  const res = await apiFetch(`/api/admin/reports/${reportId}/dismiss`, {
    method: 'POST',
    body: JSON.stringify({ notes })
  });
  return res.json();
}

export async function assignReport(reportId: string, adminId: string) {
  const res = await apiFetch(`/api/admin/reports/${reportId}/assign`, {
    method: 'POST',
    body: JSON.stringify({ adminId })
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📦 Media Management ━━━━━━━━━━━━━━━━
export async function getMediaObjects(params: {
  page?: number;
  size?: number;
  mimeType?: string;
  userId?: string;
  orphaned?: boolean;
} = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/media?${searchParams}`);
  return res.json();
}

export async function deleteMedia(mediaKey: string, reason: string) {
  const res = await apiFetch(`/api/admin/media/${encodeURIComponent(mediaKey)}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

export async function getStorageStats() {
  const res = await apiFetch('/api/admin/storage/stats');
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 🔒 Security & Audit ━━━━━━━━━━━━━━━━
export async function getAuditLog(params: {
  page?: number;
  size?: number;
  adminId?: string;
  action?: string;
  category?: string;
  severity?: string;
  startDate?: string;
  endDate?: string;
} = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/audit?${searchParams}`);
  return res.json();
}

export async function getSecurityAlerts(params: { page?: number; size?: number; severity?: string } = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/security/alerts?${searchParams}`);
  return res.json();
}

export async function getAdminSessions() {
  const res = await apiFetch('/api/admin/sessions');
  return res.json();
}

export async function terminateSession(sessionId: string, reason: string) {
  const res = await apiFetch(`/api/admin/sessions/${sessionId}/terminate`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📢 Announcements ━━━━━━━━━━━━━━━━
export async function getAnnouncements(params: { published?: boolean } = {}) {
  const searchParams = new URLSearchParams();
  if (params.published !== undefined) searchParams.set('published', String(params.published));
  const res = await apiFetch(`/api/admin/announcements?${searchParams}`);
  return res.json();
}

export async function createAnnouncement(data: {
  title: string;
  body: string;
  type: string;
  targetAudience: string;
  priority: number;
  isDismissible: boolean;
  showFrom?: string;
  showUntil?: string;
}) {
  const res = await apiFetch('/api/admin/announcements', {
    method: 'POST',
    body: JSON.stringify(data)
  });
  return res.json();
}

export async function publishAnnouncement(id: string) {
  const res = await apiFetch(`/api/admin/announcements/${id}/publish`, { method: 'POST' });
  return res.json();
}

export async function deleteAnnouncement(id: string) {
  const res = await apiFetch(`/api/admin/announcements/${id}`, { method: 'DELETE' });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 💾 Backups ━━━━━━━━━━━━━━━━
export async function getBackups() {
  const res = await apiFetch('/api/admin/backups');
  return res.json();
}

export async function createBackup(type: 'FULL' | 'INCREMENTAL' | 'CONFIG_ONLY' | 'USER_DATA') {
  const res = await apiFetch('/api/admin/backups', {
    method: 'POST',
    body: JSON.stringify({ type })
  });
  return res.json();
}

export async function restoreBackup(backupId: string, confirmCode: string) {
  const res = await apiFetch(`/api/admin/backups/${backupId}/restore`, {
    method: 'POST',
    body: JSON.stringify({ confirmCode })
  });
  return res.json();
}

export async function deleteBackup(backupId: string) {
  const res = await apiFetch(`/api/admin/backups/${backupId}`, { method: 'DELETE' });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 🚩 Feature Flags ━━━━━━━━━━━━━━━━
export async function getFeatureFlags() {
  const res = await apiFetch('/api/admin/feature-flags');
  return res.json();
}

export async function updateFeatureFlag(name: string, data: {
  enabled?: boolean;
  rolloutPercentage?: number;
  targetUserIds?: string[];
  config?: Record<string, any>;
}) {
  const res = await apiFetch(`/api/admin/feature-flags/${name}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 📡 Streaming (Server-Sent Events) ━━━━━━━━━━━━━━━━
export function subscribeToEvents(onEvent: (event: any) => void): EventSource {
  const access = authStore.access();
  // EventSource doesn't support custom headers, use a query param workaround
  const url = `/api/admin/events/stream${access ? `?access=${encodeURIComponent(access)}` : ''}`;
  const es = new EventSource(url);
  es.onmessage = (e) => {
    try {
      onEvent(JSON.parse(e.data));
    } catch {
      onEvent(e.data);
    }
  };
  return es;
}
