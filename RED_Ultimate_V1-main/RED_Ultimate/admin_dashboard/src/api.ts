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
  isAuthenticated: () => !!sessionStorage.getItem(ACCESS_KEY) || !!csrfToken()
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
  // New admin sessions keep this secret in an HttpOnly cookie; old native/dev sessions may still carry it locally.
  if (!refreshToken && !csrfToken()) { authStore.clear(); return false; }
  try {
    const csrf = csrfToken();
    const response = await fetch('/api/auth/refresh', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', ...(csrf ? { 'X-RED-CSRF': csrf } : {}) },
      // Legacy token is accepted for native/older sessions; browser admin sessions use HttpOnly cookie.
      body: JSON.stringify({ refreshToken: refreshToken || '' })
    });
    if (!response.ok) { authStore.clear(); return false; }
    const data = await response.json();
    if (!data.accessToken) { authStore.clear(); return false; }
    authStore.set(data.accessToken, data.refreshToken || undefined);
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
    if (authStore.access()) authStore.clear();
  }
  return response;
}

// ━━━━━━━━━━━━━━━━ 🔐 Auth ━━━━━━━━━━━━━━━━
export async function adminLogin(username: string, password: string) {
  const response = await fetch('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-RED-Admin-Web': '1' }, credentials: 'same-origin',
    body: JSON.stringify({ username, password })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.user?.role !== 'ADMIN') throw new Error(data.error || 'بيانات المسؤول غير صحيحة');
  authStore.set(data.accessToken, data.refreshToken || undefined, data.user);
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
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return asPage<UserRecord>(data);
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

export async function promoteUser(userId: string, role: 'USER' | 'ADMIN') {
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
export async function getOperationsOverview() {
  const res = await apiFetch('/api/admin/operations/overview');
  const data = await res.json().catch(() => ({}));
  if (res.status === 401) throw new Error('انتهت الجلسة — أعد تسجيل الدخول');
  if (!res.ok) throw new Error(data.error || data.message || `HTTP ${res.status}`);
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
  const data = await res.json().catch(() => ({}));
  return asPage(data);
}

export async function createBackup(type: 'FULL' | 'INCREMENTAL' | 'CONFIG_ONLY' | 'USER_DATA', notes?: string) {
  const res = await apiFetch('/api/admin/backups', {
    method: 'POST',
    body: JSON.stringify({ type, notes })
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
  const res = await apiFetch('/api/admin/content/polls', {
    method: 'POST',
    body: JSON.stringify(data)
  });
  return res.json();
}

export async function closePoll(pollId: string) {
  const res = await apiFetch(`/api/admin/content/polls/${pollId}/close`, { method: 'POST' });
  return res.json();
}

export async function deletePoll(pollId: string) {
  const res = await apiFetch(`/api/admin/content/polls/${pollId}`, { method: 'DELETE' });
  return res.json();
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
  const res = await apiFetch('/api/admin/content/events', {
    method: 'POST',
    body: JSON.stringify(data)
  });
  return res.json();
}

export async function cancelEvent(eventId: string, reason: string) {
  const res = await apiFetch(`/api/admin/content/events/${eventId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

export async function deleteEvent(eventId: string) {
  const res = await apiFetch(`/api/admin/content/events/${eventId}`, { method: 'DELETE' });
  return res.json();
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
  const res = await apiFetch(`/api/admin/content/hashtags/${hashtagId}/block`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
  return res.json();
}

export async function unblockHashtag(hashtagId: string) {
  const res = await apiFetch(`/api/admin/content/hashtags/${hashtagId}/unblock`, { method: 'POST' });
  return res.json();
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
  const res = await apiFetch('/api/admin/content/sticker-packs', {
    method: 'POST',
    body: JSON.stringify(data)
  });
  return res.json();
}

export async function publishStickerPack(packId: string) {
  const res = await apiFetch(`/api/admin/content/sticker-packs/${packId}/publish`, { method: 'POST' });
  return res.json();
}

export async function deleteStickerPack(packId: string) {
  const res = await apiFetch(`/api/admin/content/sticker-packs/${packId}`, { method: 'DELETE' });
  return res.json();
}

// ━━━━━━━━━━━━━━━━ 🏛️ Authority & User Intelligence (دمج القديم بالجديد — بيانات حقيقية) ━━━━━━━━━━━━━━━━
export async function getPendingApprovals(): Promise<any[]> {
  const res = await apiFetch('/api/admin/users/pending');
  const data = await res.json().catch(() => []);
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return asArray(data);
}

export async function approveRejectUser(userId: string, action: 'APPROVED' | 'REJECTED', reason?: string) {
  const res = await apiFetch('/api/admin/users/action', {
    method: 'POST',
    body: JSON.stringify({ userId, action, reason: reason || null }),
  });
  return res.json();
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
  const res = await apiFetch(`/api/admin/users/${userId}/overview`);
  return res.json();
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
  const res = await apiFetch(`/api/admin/security/wipe?userId=${encodeURIComponent(userId)}`, { method: 'POST' });
  return res.json();
}

export async function activateKillSwitch(reason: string) {
  const res = await apiFetch(`/api/admin/security/kill-switch?reason=${encodeURIComponent(reason)}`, { method: 'POST' });
  return res.json();
}

export async function updatePstnAccess(userId: string, enabled: boolean, dailyLimit: number) {
  const res = await apiFetch('/api/admin/users/pstn', {
    method: 'PUT',
    body: JSON.stringify({ userId, enabled, dailyLimit }),
  });
  return res.json();
}

export async function getPstnUsers(): Promise<any> {
  const res = await apiFetch('/api/admin/users');
  return res.json();
}
