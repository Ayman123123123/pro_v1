const ACCESS_KEY = 'red_admin_access';
const REFRESH_KEY = 'red_admin_refresh';
const CSRF_COOKIE = 'red_admin_csrf';

function csrfToken(): string | undefined {
  return document.cookie.split('; ').find((item) => item.startsWith(`${CSRF_COOKIE}=`))?.split('=').slice(1).join('=');
}

const USER_KEY = 'red_admin_user';

export const authStore = {
  access: () => sessionStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  user(): { id?: string; username?: string; displayName?: string; redId?: string; role?: string } | null {
    try { return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null'); } catch { return null; }
  },
  set(access: string, refresh?: string, user?: unknown) {
    sessionStorage.setItem(ACCESS_KEY, access);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
    else localStorage.removeItem(REFRESH_KEY);
    if (user) sessionStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() {
    sessionStorage.removeItem(ACCESS_KEY);
    sessionStorage.removeItem(USER_KEY);
    localStorage.removeItem(REFRESH_KEY);
    window.dispatchEvent(new Event('younes:auth-expired'));
  },
  // Cookie-only (CSRF) is not a session. Production web login stores the access
  // JWT here; the HttpOnly refresh cookie is used only during rotate().
  isAuthenticated: () => !!sessionStorage.getItem(ACCESS_KEY)
};

export function asArray<T = any>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    if (Array.isArray(obj.content)) return obj.content as T[];
    if (Array.isArray(obj.notifications)) return obj.notifications as T[];
    if (Array.isArray(obj.items)) return obj.items as T[];
  }
  return [];
}

export function asPage<T = any>(data: unknown): PageResponse<T> {
  const content = asArray<T>(data);
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const obj = data as Record<string, unknown>;
    return {
      content,
      page: Number(obj.page ?? 0),
      size: Number(obj.size ?? content.length),
      totalElements: Number(obj.totalElements ?? content.length),
      totalPages: Number(obj.totalPages ?? 1),
    };
  }
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 };
}

async function readJson(res: Response) {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = (data && typeof data === 'object') ? (data as any) : {};
    throw new Error(err.error || err.message || `HTTP ${res.status}`);
  }
  return data;
}

/**
 * وعد التجديد الجاري — حارس التزامن.
 *
 * ⚠️ هذا ليس تحسين أداء بل إصلاح عطل يطرد المسؤول من اللوحة.
 *
 * الخادم (`RefreshTokenService`) يدوّر رمز التجديد ويكتشف إعادة
 * استعماله: أي محاولة باستخدام رمز مُدوَّر سابقًا تُفسَّر سرقةً
 * فتُبطَل **كل جلسات الحساب** ويُرمى `REFRESH_TOKEN_REUSE_DETECTED`.
 *
 * واللوحة تُطلق طلبات متوازية كثيرة (صفحة DINSTAR وحدها تطلق أربعة
 * عبر `Promise.all`). فإن انتهت صلاحية رمز الوصول أثناءها، عاد كل
 * طلب بـ401 واستدعى `rotate()` بالرمز **نفسه**: أولها ينجح ويُدوّر
 * الرمز، والبقية تصل برمز صار مُدوَّرًا ⇒ الخادم يظنّها سرقة ويطرد
 * المسؤول فورًا. العَرَض: «خروج عشوائي عند فتح صفحة».
 *
 * الحل: تجديد واحد فقط قيد التنفيذ في أي لحظة، والبقية تنتظر نتيجته.
 */
let rotating: Promise<boolean> | null = null;

async function performRotate(): Promise<boolean> {
  const refreshToken = authStore.refresh();
  const csrf = csrfToken();
  // Browser admin sessions keep the refresh secret in an HttpOnly cookie
  // (not readable here). Always attempt the cookie POST; CSRF is sent when
  // the readable cookie is present. Local refresh is the native/dev fallback.
  if (!refreshToken && !csrf && !authStore.access()) {
    authStore.clear();
    return false;
  }
  try {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-RED-CSRF': csrf } : {}) },
      body: JSON.stringify({ refreshToken: refreshToken || '' })
    });
    if (!response.ok) { authStore.clear(); return false; }
    const data = await response.json();
    if (!data.accessToken) { authStore.clear(); return false; }
    authStore.set(data.accessToken, data.refreshToken || undefined, authStore.user() || undefined);
    return true;
  } catch {
    // Keep the refresh token during a temporary network outage; it may still be valid.
    return false;
  }
}

function rotate(): Promise<boolean> {
  // الطلبات المتزامنة تتشارك وعدًا واحدًا بدل أن يُدوّر كلٌّ منها الرمز.
  if (!rotating) {
    rotating = performRotate().finally(() => { rotating = null; });
  }
  return rotating;
}

export async function apiFetch(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', headers.get('Content-Type') || 'application/json');
  const access = authStore.access();
  if (access) headers.set('Authorization', `Bearer ${access}`);
  const response = await fetch(path, { ...init, headers, credentials: init.credentials || 'same-origin' });
  if (response.status === 401 && retry) {
    const refreshed = await rotate();
    if (refreshed) return apiFetch(path, init, false);
    authStore.clear();
  }
  return response;
}

export function authErrorMessage(data: any, status: number): string {
  const code = String(data?.error || data?.message || '');
  if (status >= 500) {
    return code || `عطل مؤقت في الخادم (HTTP ${status})`;
  }
  if (status === 401 || /AUTHENTICATION_REQUIRED|UNAUTHORIZED|UNAUTHENTICATED|JWT|TOKEN/i.test(code)) {
    return 'انتهت الجلسة — أعد تسجيل الدخول';
  }
  return code || `HTTP ${status}`;
}

// ━━━━━━━━━━━━━━━━ 🔐 Auth ━━━━━━━━━━━━━━━━
export type BackendProbe = {
  state: 'CHECKING' | 'LIVE' | 'READY' | 'DOWN';
  status?: string;
  hint: string;
};

function parseJsonObject(text: string): Record<string, unknown> | null {
  try {
    const value = JSON.parse(text);
    return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

function isAdminPanelPlaceholder(text: string): boolean {
  return /^healthy\s*$/i.test(text.trim());
}

/**
 * Login badge must not treat a dead JVM as “wrong password”, and must not
 * treat the admin-panel container’s plaintext `/health` as the Kotlin API.
 */
export async function probeBackend(timeoutMs = 2500): Promise<BackendProbe> {
  const ctrl = new AbortController();
  const kill = window.setTimeout(() => ctrl.abort(), timeoutMs);
  const fetchText = async (path: string) => {
    const response = await fetch(path, { signal: ctrl.signal, cache: 'no-store' });
    const text = await response.text();
    return { response, text };
  };
  try {
    try {
      const live = await fetchText('/health/live');
      if (isAdminPanelPlaceholder(live.text)) {
        return {
          state: 'DOWN',
          hint: 'طلب الصحة وصل للوحة لا للخادم. افتح http://127.0.0.1:8088/ عبر Nginx.',
        };
      }
      const liveJson = parseJsonObject(live.text);
      if (live.response.ok && liveJson && String(liveJson.probe || '').toLowerCase() === 'live') {
        try {
          const ready = await fetchText('/health');
          const readyJson = parseJsonObject(ready.text);
          const status = String(readyJson?.status || '').toUpperCase();
          if (status === 'UP' || status === 'HEALTHY' || status === 'DEGRADED') {
            return { state: 'READY', status, hint: 'الخادم جاهز لتسجيل الدخول' };
          }
        } catch {
          /* JVM is up; databases may still be starting */
        }
        return { state: 'LIVE', status: 'STARTING', hint: 'الخادم يعمل وجاري تجهيز قواعد البيانات' };
      }
    } catch {
      /* fall through to /health */
    }

    const ready = await fetchText('/health');
    if (isAdminPanelPlaceholder(ready.text)) {
      return {
        state: 'DOWN',
        hint: 'طلب /health وصل للوحة لا لـ Kotlin. استخدم المنفذ 8088 لا 3000.',
      };
    }
    const readyJson = parseJsonObject(ready.text);
    const status = String(readyJson?.status || '').toUpperCase();
    if (readyJson && (status === 'UP' || status === 'HEALTHY' || status === 'DEGRADED')) {
      return { state: 'READY', status, hint: 'الخادم جاهز لتسجيل الدخول' };
    }
    if (ready.response.ok && readyJson) {
      return { state: 'LIVE', status: status || 'STARTING', hint: 'الخادم يعمل وجاري التجهيز' };
    }
    return {
      state: 'DOWN',
      hint: 'تعذر الوصول إلى /health. شغّل Docker ثم: .\\scripts\\compose-recover.ps1',
    };
  } catch {
    return {
      state: 'DOWN',
      hint: 'الخادم غير متصل. افتح http://127.0.0.1:8088/ بعد تشغيل Docker Desktop.',
    };
  } finally {
    window.clearTimeout(kill);
  }
}

export function loginFailureMessage(status: number | undefined, body: Record<string, unknown> | null, networkFailed: boolean): string {
  if (networkFailed) {
    return 'الخادم غير متصل. ليست مشكلة كلمة مرور. شغّل Docker وافتح http://127.0.0.1:8088/';
  }
  if (status === 502 || status === 503 || status === 504) {
    return 'Nginx لا يجد الباك اند بعد. انتظر حتى يصبح /health أخضر ثم أعد المحاولة.';
  }
  const code = String(body?.error || body?.message || '');
  if (status === 401 || /INVALID_CREDENTIALS/i.test(code)) {
    return 'بيانات الدخول مرفوضة. استخدم RED_ADMIN_USERNAME و RED_ADMIN_PASSWORD من ملف RED_Ultimate/.env';
  }
  if (status === 403 || status === 423) {
    return 'الحساب موجود لكنه غير معتمد أو محظور. ادخل بحساب المسؤول من .env';
  }
  return code || (status ? `تعذر الدخول (HTTP ${status})` : 'تعذر تسجيل الدخول');
}

export async function adminLogin(username: string, password: string) {
  let response: Response;
  try {
    response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-RED-Admin-Web': '1' },
      credentials: 'same-origin',
      body: JSON.stringify({ username, password }),
    });
  } catch {
    throw new Error(loginFailureMessage(undefined, null, true));
  }
  const raw = await response.text();
  const data = parseJsonObject(raw) || {};
  if (!response.ok) {
    throw new Error(loginFailureMessage(response.status, data, false));
  }
  const user = data.user as { role?: string } | undefined;
  if (user?.role !== 'ADMIN') {
    throw new Error('هذا الحساب ليس مسؤولاً. ادخل بـ RED_ADMIN_USERNAME من ملف .env');
  }
  authStore.set(String(data.accessToken || ''), typeof data.refreshToken === 'string' ? data.refreshToken : undefined, data.user);
  return data;
}

export async function adminLogout() {
  const refreshToken = authStore.refresh();
  if (refreshToken) {
    const csrf = csrfToken();
    await apiFetch('/api/auth/logout', {
      method: 'POST',
      headers: csrf ? { 'X-RED-CSRF': csrf } : undefined,
      body: JSON.stringify({ refreshToken: refreshToken || '' })
    }).catch(() => {}); // Best-effort; clear tokens regardless
  }
  authStore.clear();
}

// ━━━━━━━━━━━━━━━━ 🔔 Notifications ━━━━━━━━━━━━━━━━
export async function getNotifications(page = 0, size = 50, type?: string) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (type) params.set('type', type);
  const res = await apiFetch(`/api/notifications?${params}`);
  const data = await res.json().catch(() => ({}));
  const notifications = asArray(data);
  return {
    notifications,
    unreadCount: Number(data?.unreadCount ?? 0),
    page: Number(data?.page ?? page),
  };
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
export interface UserRecord {
  id: string;
  redId: string;
  username: string;
  displayName: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'BANNED';
  role: 'USER' | 'ADMIN';
  pstnEnabled: boolean;
  pstnDailyLimit?: number;
  createdAt: string;
  approvedAt?: string;
  lastSeen?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function getUsers(params: {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
  role?: string;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
} = {}): Promise<PageResponse<UserRecord>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.status) searchParams.set('status', params.status);
  if (params.search) searchParams.set('search', params.search);
  if (params.role) searchParams.set('role', params.role);
  if (params.sortBy) searchParams.set('sortBy', params.sortBy);
  if (params.sortDir) searchParams.set('sortDir', params.sortDir);
  const res = await apiFetch(`/api/admin/users?${searchParams}`);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(authErrorMessage(data, res.status));
  return asPage<UserRecord>(data);
}

async function writeJson(res: Response) {
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(authErrorMessage(data, res.status));
  return data;
}

export async function getUserDetail(userId: string) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}`));
}

export async function approveUser(userId: string) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/approve`, { method: 'POST' }));
}

export async function rejectUser(userId: string, reason?: string) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  }));
}

export async function banUser(userId: string, reason: string, durationDays?: number) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/ban`, {
    method: 'POST',
    body: JSON.stringify({ reason, durationDays })
  }));
}

export async function unbanUser(userId: string) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/unban`, { method: 'POST' }));
}

export async function promoteUser(userId: string, role: 'USER' | 'ADMIN') {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/role`, {
    method: 'PUT',
    body: JSON.stringify({ role })
  }));
}

export async function deleteUser(userId: string, hard = false) {
  return writeJson(await apiFetch(`/api/admin/users/${userId}${hard ? '?hard=true' : ''}`, {
    method: 'DELETE'
  }));
}

// ━━━━━━━━━━━━━━━━ 📊 Dashboard & Analytics ━━━━━━━━━━━━━━━━
export async function getOperationsOverview() {
  const res = await apiFetch('/api/admin/operations/overview');
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(authErrorMessage(data, res.status));
  if (!data || typeof data !== 'object' || !data.users) {
    throw new Error('رد الجرد غير مكتمل من الخادم');
  }
  return data;
}

export async function getDashboardSummary() {
  const res = await apiFetch('/api/admin/dashboard/summary');
  const data = await res.json().catch(() => null);
  if (!res.ok || !data || typeof data !== 'object' || Array.isArray(data) || !('analytics' in data)) {
    return null;
  }
  return data;
}

export interface DashboardSummary {
  analytics: {
    totalUsers: number;
    approvedUsers: number;
    pendingUsers: number;
    bannedUsers: number;
    newUsers24h: number;
    approvalRate: number;
  };
  pendingReports: number;
  recentCriticalAlerts: number;
  degradedComponents: number;
  activeBackups: number;
  generatedAt: string;
}

export interface SystemHealth {
  id: string;
  component: string;
  status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  cpuUsage?: number;
  memoryUsage?: number;
  diskUsage?: number;
  activeConnections?: number;
  requestsPerSecond?: number;
  averageResponseMs?: number;
  errorRate?: number;
  details?: string;
  lastCheckAt: string;
}

export interface RealtimeMetrics {
  users: any;
  health: Record<string, SystemHealth>;
  timestamp: string;
}

export async function getSystemAnalytics(startDate: string, endDate: string) {
  const res = await apiFetch(`/api/admin/analytics?start=${startDate}&end=${endDate}`);
  const data = await res.json().catch(() => []);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  return [];
}

export async function getSystemHealth() {
  const res = await apiFetch('/api/admin/health');
  const data = await res.json().catch(() => []);
  return Array.isArray(data) ? data : [];
}

export async function getRealtimeMetrics() {
  const res = await apiFetch('/api/admin/metrics/realtime');
  const data = await res.json().catch(() => null);
  return data && typeof data === 'object' && !Array.isArray(data) ? data : null;
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
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return asPage(data);
}

export async function resolveReport(reportId: string, resolution: string, notes?: string) {
  return writeJson(await apiFetch(`/api/admin/reports/${reportId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ resolution, notes })
  }));
}

export async function dismissReport(reportId: string, notes?: string) {
  return writeJson(await apiFetch(`/api/admin/reports/${reportId}/dismiss`, {
    method: 'POST',
    body: JSON.stringify({ notes })
  }));
}

export async function assignReport(reportId: string, adminId: string) {
  return writeJson(await apiFetch(`/api/admin/reports/${reportId}/assign`, {
    method: 'POST',
    body: JSON.stringify({ adminId })
  }));
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
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return asPage(data);
}

export async function getSecurityAlerts(params: { page?: number; size?: number; severity?: string } = {}) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined) searchParams.set(k, String(v));
  });
  const res = await apiFetch(`/api/admin/security/alerts?${searchParams}`);
  const data = await res.json().catch(() => ({}));
  return asPage(data);
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
  const data = await res.json().catch(() => []);
  return asArray(data);
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
  return writeJson(await apiFetch('/api/admin/announcements', {
    method: 'POST',
    body: JSON.stringify(data)
  }));
}

export async function publishAnnouncement(id: string) {
  return writeJson(await apiFetch(`/api/admin/announcements/${id}/publish`, { method: 'POST' }));
}

export async function deleteAnnouncement(id: string) {
  return writeJson(await apiFetch(`/api/admin/announcements/${id}`, { method: 'DELETE' }));
}

// ━━━━━━━━━━━━━━━━ 💾 Backups ━━━━━━━━━━━━━━━━
export async function getBackups() {
  const res = await apiFetch('/api/admin/backups');
  const data = await res.json().catch(() => ({}));
  return asPage(data);
}

export async function createBackup(type: 'FULL' | 'INCREMENTAL' | 'CONFIG_ONLY' | 'USER_DATA', notes?: string) {
  return writeJson(await apiFetch('/api/admin/backups', {
    method: 'POST',
    body: JSON.stringify({ type, notes })
  }));
}

export async function restoreBackup(backupId: string, confirmCode: string) {
  return writeJson(await apiFetch(`/api/admin/backups/${backupId}/restore`, {
    method: 'POST',
    body: JSON.stringify({ confirmCode })
  }));
}

export async function deleteBackup(backupId: string) {
  return writeJson(await apiFetch(`/api/admin/backups/${backupId}`, { method: 'DELETE' }));
}

// ━━━━━━━━━━━━━━━━ 🚩 Feature Flags ━━━━━━━━━━━━━━━━
export async function getFeatureFlags() {
  const res = await apiFetch('/api/admin/feature-flags');
  const data = await res.json().catch(() => []);
  return asArray(data);
}

export async function updateFeatureFlag(name: string, data: {
  enabled?: boolean;
  rolloutPercentage?: number;
  targetUserIds?: string[];
  config?: Record<string, any>;
  description?: string;
}) {
  return writeJson(await apiFetch(`/api/admin/feature-flags/${name}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  }));
}

// ━━━━━━━━━━━━━━━━ 📡 Streaming (Server-Sent Events) ━━━━━━━━━━━━━━━━
/**
 * Streaming events عبر fetch + ReadableStream بدل EventSource.
 *
 * EventSource لا يدعم رؤوسًا مخصصة، ووضع توكن الوصول في الاستعلام
 * يسرّبه إلى السجلات والوسائط. الالتزام هنا هو apiFetch: التوكن في
 * رأس Authorization فقط، والوصل مع نفس عقد المصادقة (401 ⇒ rotate).
 */
export function subscribeToEvents(onEvent: (event: any) => void, onState?: (s: 'OPEN' | 'CLOSED' | 'ERROR') => void): { close: () => void } {
  const controller = new AbortController();
  let closed = false;
  let lineBuffer = '';

  const close = () => {
    closed = true;
    controller.abort();
    onState?.('CLOSED');
  };

  (async () => {
    const headers = new Headers();
    headers.set('Accept', 'text/event-stream');
    headers.set('Cache-Control', 'no-cache');
    const access = authStore.access();
    if (access) headers.set('Authorization', `Bearer ${access}`);

    try {
      const res = await fetch('/api/admin/events/stream', {
        headers,
        credentials: 'same-origin',
        signal: controller.signal,
      });
      if (!res.ok || !res.body) {
        if (res.status === 401) {
          const refreshed = await rotate();
          if (refreshed) {
            const headers2 = new Headers();
            headers2.set('Accept', 'text/event-stream');
            const access2 = authStore.access();
            if (access2) headers2.set('Authorization', `Bearer ${access2}`);
            const res2 = await fetch('/api/admin/events/stream', { headers: headers2, credentials: 'same-origin', signal: controller.signal });
            if (!res2.ok || !res2.body) throw new Error(`HTTP ${res2.status}`);
            stream(res2);
            return;
          }
        }
        throw new Error(`HTTP ${res.status}`);
      }
      stream(res);
    } catch (err) {
      if (!closed && !controller.signal.aborted) onState?.('ERROR');
    }
  })();

  function stream(res: Response) {
    onState?.('OPEN');
    const reader = res.body!.getReader();
    const decoder = new TextDecoder();
    (async () => {
      try {
        for (;;) {
          const { done, value } = await reader.read();
          if (done || closed) break;
          lineBuffer += decoder.decode(value, { stream: true });
          const lines = lineBuffer.split('\n');
          lineBuffer = lines.pop() || '';
          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith(':')) continue; // تعليقات keep-alive
            const payload = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed;
            try {
              onEvent(JSON.parse(payload));
            } catch {
              onEvent(payload);
            }
          }
        }
      } catch {
        /* aborted */
      } finally {
        if (!closed) {
          closed = true;
          onState?.('CLOSED');
        }
      }
    })();
  }

  return { close };
}

// ━━━━━━━━━━━━━━━━ 📊 Content Management ━━━━━━━━━━━━━━━━
export interface Poll {
  id: string;
  creatorId: string;
  question: string;
  description?: string;
  pollType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RANKED';
  isAnonymous: boolean;
  allowAddOptions: boolean;
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED';
  startsAt: string;
  endsAt?: string;
  targetType: 'GLOBAL' | 'GROUP' | 'USER';
  totalVotes: number;
  uniqueVoters: number;
  createdAt: string;
}

export interface Event {
  id: string;
  creatorId: string;
  title: string;
  description?: string;
  locationName?: string;
  locationAddress?: string;
  startsAt: string;
  endsAt?: string;
  eventType: 'MEETING' | 'CONFERENCE' | 'WEBINAR' | 'SOCIAL' | 'CELEBRATION' | 'OTHER';
  visibility: 'PUBLIC' | 'PRIVATE' | 'INVITATION_ONLY';
  maxAttendees?: number;
  currentAttendees: number;
  status: 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED';
  rsvpEnabled: boolean;
  createdAt: string;
}

export interface Hashtag {
  id: string;
  tagName: string;
  description?: string;
  category?: string;
  usageCount: number;
  postsCount: number;
  storiesCount: number;
  uniqueUsers: number;
  trendingScore: number;
  isTrending: boolean;
  isBlocked: boolean;
  blockedReason?: string;
}

export interface StickerPack {
  id: string;
  name: string;
  description?: string;
  isOfficial: boolean;
  isFree: boolean;
  priceCents: number;
  currency: string;
  isPublished: boolean;
  stickerCount: number;
  totalDownloads: number;
  createdAt: string;
}

export async function getPolls(params: { page?: number; size?: number; status?: string } = {}) {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.status) searchParams.set('status', params.status);
  const res = await apiFetch(`/api/admin/content/polls?${searchParams}`);
  const data = await res.json().catch(() => ({}));
  return asPage(data);
}

export async function getActivePolls() {
  const res = await apiFetch('/api/admin/content/polls/active');
  return res.json();
}

export async function getPollDetail(pollId: string) {
  const res = await apiFetch(`/api/admin/content/polls/${pollId}`);
  return res.json();
}

export async function createPoll(data: {
  question: string;
  options: string[];
  pollType?: string;
  isAnonymous?: boolean;
  allowAddOptions?: boolean;
  endsAt?: string;
}) {
  return writeJson(await apiFetch('/api/admin/content/polls', {
    method: 'POST',
    body: JSON.stringify(data)
  }));
}

export async function closePoll(pollId: string) {
  return writeJson(await apiFetch(`/api/admin/content/polls/${pollId}/close`, { method: 'POST' }));
}

export async function deletePoll(pollId: string) {
  return writeJson(await apiFetch(`/api/admin/content/polls/${pollId}`, { method: 'DELETE' }));
}

export async function getEvents(params: { page?: number; size?: number; status?: string } = {}) {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set('page', String(params.page));
  if (params.size !== undefined) searchParams.set('size', String(params.size));
  if (params.status) searchParams.set('status', params.status);
  const res = await apiFetch(`/api/admin/content/events?${searchParams}`);
  const data = await res.json().catch(() => ({}));
  return asPage(data);
}

export async function getUpcomingEvents() {
  const res = await apiFetch('/api/admin/content/events/upcoming');
  return res.json();
}

export async function getLiveEvents() {
  const res = await apiFetch('/api/admin/content/events/live');
  return res.json();
}

export async function createEvent(data: {
  title: string;
  description?: string;
  locationName?: string;
  startsAt: string;
  endsAt?: string;
  eventType?: string;
  visibility?: string;
  maxAttendees?: number;
  rsvpEnabled?: boolean;
}) {
  return writeJson(await apiFetch('/api/admin/content/events', {
    method: 'POST',
    body: JSON.stringify(data)
  }));
}

export async function cancelEvent(eventId: string, reason: string) {
  return writeJson(await apiFetch(`/api/admin/content/events/${eventId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  }));
}

export async function deleteEvent(eventId: string) {
  return writeJson(await apiFetch(`/api/admin/content/events/${eventId}`, { method: 'DELETE' }));
}

export async function getTrendingHashtags(limit = 50) {
  const res = await apiFetch(`/api/admin/content/hashtags/trending?limit=${limit}`);
  return asArray(await res.json().catch(() => []));
}

export async function getPopularHashtags(limit = 50) {
  const res = await apiFetch(`/api/admin/content/hashtags/popular?limit=${limit}`);
  return asArray(await res.json().catch(() => []));
}

export async function searchHashtags(query: string, page = 0, size = 20) {
  const res = await apiFetch(`/api/admin/content/hashtags/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
  return res.json();
}

export async function blockHashtag(hashtagId: string, reason: string) {
  return writeJson(await apiFetch(`/api/admin/content/hashtags/${hashtagId}/block`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  }));
}

export async function unblockHashtag(hashtagId: string) {
  return writeJson(await apiFetch(`/api/admin/content/hashtags/${hashtagId}/unblock`, { method: 'POST' }));
}

export async function getStickerPacks(official = false) {
  const res = await apiFetch(`/api/admin/content/sticker-packs?official=${official}`);
  return asArray(await res.json().catch(() => []));
}

export async function createStickerPack(data: {
  name: string;
  description?: string;
  coverMediaKey: string;
  isOfficial?: boolean;
  isFree?: boolean;
  priceCents?: number;
}) {
  return writeJson(await apiFetch('/api/admin/content/sticker-packs', {
    method: 'POST',
    body: JSON.stringify(data)
  }));
}

export async function publishStickerPack(packId: string) {
  return writeJson(await apiFetch(`/api/admin/content/sticker-packs/${packId}/publish`, { method: 'POST' }));
}

export async function deleteStickerPack(packId: string) {
  return writeJson(await apiFetch(`/api/admin/content/sticker-packs/${packId}`, { method: 'DELETE' }));
}

// ━━━━━━━━━━━━━━━━ 🏛️ Authority & User Intelligence (دمج القديم بالجديد — بيانات حقيقية) ━━━━━━━━━━━━━━━━
export async function getPendingApprovals(): Promise<any[]> {
  const res = await apiFetch('/api/admin/users/pending');
  const data = await res.json().catch(() => []);
  if (!res.ok) throw new Error(authErrorMessage(data, res.status));
  return asArray(data);
}

export async function approveRejectUser(userId: string, action: 'APPROVED' | 'REJECTED', reason?: string) {
  return writeJson(await apiFetch('/api/admin/users/action', {
    method: 'POST',
    body: JSON.stringify({ userId, action, reason: reason || null }),
  }));
}

export interface UserOverview {
  user: any;
  online: boolean;
  messagesSent: number;
  messagesReceived: number;
  messages24h: number;
  callsMade: number;
  callsReceived: number;
  redCalls: number;
  pstnCalls: number;
  passwordResetRequired: boolean;
  remoteWipeStatus: string;
  managedDeviceWipeAllowed: boolean;
  securityEvents: any[];
}

export async function getUserOverview(userId: string): Promise<UserOverview> {
  return writeJson(await apiFetch(`/api/admin/users/${userId}/overview`));
}

export async function createTemporaryPassword(userId: string, temporaryPassword: string) {
  const res = await apiFetch(`/api/admin/users/${userId}/temporary-password`, {
    method: 'POST',
    body: JSON.stringify({ temporaryPassword }),
  });
  return res.json();
}

export async function requestRemoteWipe(userId: string) {
  const res = await apiFetch(`/api/admin/users/${userId}/remote-app-wipe`, { method: 'POST' });
  return res.json();
}

export async function requestSecurityWipe(userId: string) {
  return writeJson(await apiFetch(`/api/admin/security/wipe?userId=${encodeURIComponent(userId)}`, { method: 'POST' }));
}

export async function activateKillSwitch(reason: string) {
  return writeJson(await apiFetch(`/api/admin/security/kill-switch?reason=${encodeURIComponent(reason)}`, { method: 'POST' }));
}

export async function updatePstnAccess(userId: string, enabled: boolean, dailyLimit: number) {
  return writeJson(await apiFetch('/api/admin/users/pstn', {
    method: 'PUT',
    body: JSON.stringify({ userId, enabled, dailyLimit }),
  }));
}

export async function bindSim(userId: string, gatewayId: string, portIndex: number, number?: string) {
  return writeJson(await apiFetch('/api/admin/dinstar/bindings', {
    method: 'POST',
    body: JSON.stringify({ userId, gatewayId, portIndex, number }),
  }));
}

export async function unbindSim(userId: string) {
  return writeJson(await apiFetch(`/api/admin/dinstar/bindings/${userId}`, {
    method: 'DELETE',
  }));
}

export async function getPstnEligibleUsers() {
  // Returns APPROVED users who are pstnEnabled but not necessarily bound
  const res = await apiFetch('/api/master/v1/pstn/users?size=1000');
  const data = await res.json().catch(() => ({ content: [] }));
  return asArray(data.content);
}

export interface SimInventoryUpdate {
  simLabel?: string;
  operatorLabel?: string;
  lastFourDigits?: string;
  verificationState: 'UNVERIFIED' | 'VERIFIED' | 'FAILED' | 'LEARNED';
  verificationMethod?: 'MANUAL' | 'USSD' | 'SMS_KEYWORD' | 'CALL_LOOP';
  notes?: string;
}

export async function updateSimInventory(gatewayId: string, portIndex: number, data: SimInventoryUpdate) {
  return writeJson(await apiFetch(`/api/admin/dinstar/inventory/${gatewayId}/ports/${portIndex}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }));
}

export async function bulkBindSims(bindings: { userId: string; gatewayId: string; portIndex: number; number?: string }[]) {
  return writeJson(await apiFetch('/api/admin/dinstar/bindings/bulk', {
    method: 'POST',
    body: JSON.stringify({ bindings }),
  }));
}

export async function getPstnUsers(): Promise<any> {
  const res = await apiFetch('/api/admin/users');
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 👥 Groups (Admin) — /api/admin/social/groups ━━━━━━━━━━━━━━━━
export interface AdminGroup {
  id: string;
  name: string;
  description?: string;
  ownerRedId: string;
  avatarUrl?: string;
  memberCount: number;
  createdAt: string;
  updatedAt?: string;
}
export interface AdminGroupMember {
  userId: string;
  redId: string;
  username: string;
  role: string;
  joinedAt: string;
}
export interface AdminGroupDetails {
  id: string;
  name: string;
  description?: string;
  ownerRedId: string;
  avatarUrl?: string;
  createdAt: string;
  updatedAt?: string;
  members: AdminGroupMember[];
}
export async function getGroupsOverview(): Promise<{ totalGroups: number; totalMembers: number; avgMembersPerGroup: number; createdToday: number }> {
  return writeJson(await apiFetch('/api/admin/social/groups/overview'));
}
export async function getAdminGroups(params: { q?: string; page?: number; size?: number } = {}): Promise<PageResponse<AdminGroup>> {
  const sp = new URLSearchParams();
  if (params.q) sp.set('q', params.q);
  if (params.page !== undefined) sp.set('page', String(params.page));
  if (params.size !== undefined) sp.set('size', String(params.size));
  const data = await writeJson(await apiFetch(`/api/admin/social/groups?${sp}`));
  return asPage<AdminGroup>(data);
}
export async function getAdminGroupDetails(groupId: string): Promise<AdminGroupDetails> {
  return writeJson(await apiFetch(`/api/admin/social/groups/${groupId}`));
}
export async function deleteAdminGroup(groupId: string) {
  return writeJson(await apiFetch(`/api/admin/social/groups/${groupId}`, { method: 'DELETE' }));
}
export async function removeGroupMember(groupId: string, userId: string) {
  return writeJson(await apiFetch(`/api/admin/social/groups/${groupId}/members/${userId}`, { method: 'DELETE' }));
}
// Aliases used by some pages
export const getAdminGroup = getAdminGroupDetails;
export const getGroupsOverviewAlias = getGroupsOverview;

// ━━━━━━━━━━━━━━━━ 📝 Posts (Admin) — /api/admin/social/posts ━━━━━━━━━━━━━━━━
export interface AdminPost {
  id: string;
  authorRedId: string;
  authorUsername: string;
  authorDisplayName: string;
  text: string;
  visibility: string;
  kind: string;
  mediaCount: number;
  hashtags: string[];
  reactionCounts: Record<string, number>;
  replyCount: number;
  repostCount: number;
  createdAt: string;
  deleted?: boolean;
}
export async function getPostsOverview(): Promise<{ totalPosts: number; createdToday: number; deletedPosts: number; polls: number }> {
  return writeJson(await apiFetch('/api/admin/social/posts/overview'));
}
export async function getAdminPosts(params: { q?: string; includeDeleted?: boolean; page?: number; size?: number } = {}): Promise<PageResponse<AdminPost>> {
  const sp = new URLSearchParams();
  if (params.q) sp.set('q', params.q);
  if (params.includeDeleted !== undefined) sp.set('includeDeleted', String(params.includeDeleted));
  if (params.page !== undefined) sp.set('page', String(params.page));
  if (params.size !== undefined) sp.set('size', String(params.size));
  const data = await writeJson(await apiFetch(`/api/admin/social/posts?${sp}`));
  return asPage<AdminPost>(data);
}
export async function deleteAdminPost(postId: string) {
  return writeJson(await apiFetch(`/api/admin/social/posts/${postId}`, { method: 'DELETE' }));
}
export async function restoreAdminPost(postId: string) {
  return writeJson(await apiFetch(`/api/admin/social/posts/${postId}/restore`, { method: 'POST' }));
}
