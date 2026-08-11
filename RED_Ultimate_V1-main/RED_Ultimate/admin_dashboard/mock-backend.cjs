/**
 * ══════════════════════════════════════════════════════════════════════
 * خادم تطوير وهمي للوحة الإدارة — YOUNES Master
 * ══════════════════════════════════════════════════════════════════════
 *
 * الغرض: تشغيل اللوحة كاملة محليًا بدون JDK/PostgreSQL/Mongo/Redis/MinIO،
 * مع أشكال بيانات مطابقة حرفيًا لعقد الخادم الحقيقي في
 * `backend-server/src/main/kotlin/com/red/server/admin/controller/AdminV2Controller.kt`.
 *
 * لماذا أُعيدت كتابته: النسخة السابقة كانت تخدم 5 مسارات فقط من 51،
 * وكل ما عداها يسقط في رد عام `{status:'SUCCESS', data:{}}`. الصفحات التي
 * تتوقع مصفوفة أو صفحة `{content,totalElements}` كانت تنهار فعليًا بـ:
 *   - "Cannot read properties of undefined (reading 'filter')"  (المستخدمون)
 *   - "analytics.map is not a function"                          (الرئيسية)
 *   - "rawData.some is not a function" / "items.filter ..."      (الإعلانات)
 *   - "INVALID_TICKET_RESPONSE"                                   (سجل النظام)
 *
 * قواعد الالتزام بالعقد (مأخوذة من الشيفرة الحقيقية لا من التخمين):
 *   • قوائم مُصفّحة  → { content, page, size, totalElements, totalPages }
 *       /users, /reports, /audit, /security/alerts, /backups,
 *       /content/polls, /content/events
 *   • مصفوفات صريحة → /health, /feature-flags, /announcements, /sessions,
 *       /analytics, /content/hashtags/*, /content/sticker-packs,
 *       /admin/dinstar/status, /master/v1/hardware/dinstar/slots
 *   • كائنات        → /dashboard/summary, /metrics/realtime, /ws-ticket
 *
 * ⚠️ للتطوير المحلي فقط: لا مصادقة حقيقية، ولا يُبنى داخل صورة Docker.
 *    الإنتاج يمر دائمًا عبر backend-server الحقيقي خلف Nginx.
 *
 * التشغيل:  node mock-backend.cjs   (ثم `npm run dev` في نافذة أخرى)
 */
const http = require('http');
const crypto = require('crypto');

const PORT = Number(process.env.MOCK_PORT || 8080);
const WS_PATH = '/ws/admin/logs';

// ─────────────────────────────── أدوات ───────────────────────────────
const nowIso = () => new Date().toISOString();
const uuid = () => crypto.randomUUID();
const iso = (daysAgo = 0, hoursAgo = 0) =>
  new Date(Date.now() - daysAgo * 86400000 - hoursAgo * 3600000).toISOString();

/** صفحة على نمط Spring Pageable كما يبنيها AdminV2Controller. */
function page(items, pageNum = 0, size = 50) {
  const from = pageNum * size;
  const content = items.slice(from, from + size);
  return {
    content,
    page: pageNum,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

// ─────────────────────── حالة ثابتة قابلة للتعديل ───────────────────────
// تبقى في الذاكرة طوال عمر العملية كي تعكس الإجراءات (موافقة/حظر/نشر) أثرًا حقيقيًا.

const USERS = [
  ['younes_sovereign', 'يونس السيادي', 'APPROVED', 'ADMIN', true, 0],
  ['ahmed_dev', 'أحمد المطور', 'PENDING', 'USER', false, 1],
  ['ali_red', 'علي أحمد', 'PENDING', 'USER', false, 2],
  ['sara_ops', 'سارة العمليات', 'APPROVED', 'USER', true, 5],
  ['khaled_m', 'خالد محمد', 'APPROVED', 'USER', false, 9],
  ['noor_a', 'نور عبدالله', 'BANNED', 'USER', false, 14],
  ['omar_t', 'عمر طارق', 'APPROVED', 'USER', true, 21],
  ['huda_s', 'هدى سالم', 'REJECTED', 'USER', false, 27],
  ['fahd_k', 'فهد كمال', 'APPROVED', 'USER', false, 33],
  ['layla_n', 'ليلى ناصر', 'PENDING', 'USER', false, 0],
].map(([username, displayName, status, role, pstnEnabled, daysAgo], i) => ({
  id: uuid(),
  redId: `RED-${String(1000 + i * 7)}`,
  username,
  displayName,
  status,
  role,
  pstnEnabled,
  createdAt: iso(daysAgo, i),
  approvedAt: status === 'APPROVED' ? iso(daysAgo - 0.2) : null,
  lastSeen: status === 'APPROVED' ? Date.now() - i * 900000 : null,
  devices: [
    {
      id: uuid(),
      deviceName: i % 2 === 0 ? 'Samsung A54' : 'Xiaomi Redmi 12',
      name: i % 2 === 0 ? 'Samsung A54' : 'Xiaomi Redmi 12',
      platform: 'ANDROID',
      status,
      // بصمة مفتاح هوية بصيغة libsignal — تُعرض للتحقق اليدوي قبل الموافقة
      identityFingerprint: crypto
        .createHash('sha256')
        .update(username)
        .digest('hex')
        .slice(0, 64)
        .replace(/(.{4})/g, '$1 ')
        .trim(),
      createdAt: iso(daysAgo, i),
    },
  ],
}));

const ANNOUNCEMENTS = [
  ['صيانة مجدولة للبنية التحتية', 'صيانة قاعدة البيانات الليلة 02:00–03:00 بتوقيت عدن.', 'MAINTENANCE', true, 2],
  ['إطلاق المكالمات الجماعية', 'المؤتمرات الصوتية عبر SFU متاحة الآن لكل الحسابات المعتمدة.', 'FEATURE', true, 6],
  ['تنبيه أمني', 'فعّلوا التحقق من بصمة المفتاح قبل قبول أي جهاز جديد.', 'WARNING', false, 1],
].map(([title, body, type, isPublished, daysAgo]) => ({
  id: uuid(),
  title,
  body,
  type,
  targetAudience: 'ALL',
  priority: type === 'WARNING' ? 2 : 0,
  isDismissible: true,
  isPublished,
  showFrom: iso(daysAgo),
  showUntil: null,
  createdBy: 'red_admin',
  createdAt: iso(daysAgo),
  publishedAt: isPublished ? iso(daysAgo) : null,
}));

const FEATURE_FLAGS = [
  ['GROUP_E2EE_SENDER_KEYS', 'توزيع وتدوير مفاتيح المجموعات', false, 0],
  ['PSTN_DINSTAR_ROUTING', 'توجيه المكالمات عبر بوابة DINSTAR', true, 100],
  ['STORIES_MEDIA', 'الحالات والوسائط المؤقتة', true, 100],
  ['LIVE_STREAMING', 'البث المباشر عبر SFU', false, 25],
  ['LOCAL_FTS_SEARCH', 'بحث محلي مشفر FTS5', false, 10],
].map(([flagName, description, enabled, rolloutPercentage]) => ({
  id: uuid(),
  flagName,
  description,
  enabled,
  rolloutPercentage,
  targetUserIds: null,
  targetGroups: null,
  updatedAt: iso(1),
}));

const AUDIT = [
  ['USER_APPROVED', 'USER', 'INFO', 'تمت الموافقة على الجهاز بعد تطابق البصمة'],
  ['SESSION_TERMINATED', 'SECURITY', 'WARNING', 'إنهاء جلسة إدارية غير نشطة'],
  ['FEATURE_FLAG_UPDATED', 'SYSTEM', 'INFO', 'تحديث علم الميزة LIVE_STREAMING'],
  ['KILL_SWITCH_ARMED', 'SECURITY', 'CRITICAL', 'تفعيل مفتاح الإيقاف الطارئ (تجربة)'],
  ['ANNOUNCEMENT_PUBLISHED', 'SYSTEM', 'INFO', 'نشر إعلان الصيانة'],
  ['USERS_LISTED', 'USER', 'INFO', 'عرض قائمة المستخدمين'],
  ['BACKUP_CREATED', 'SYSTEM', 'INFO', 'إنشاء نسخة احتياطية كاملة'],
  ['USER_BANNED', 'USER', 'WARNING', 'حظر حساب مخالف'],
].map(([action, category, severity, description], i) => ({
  id: uuid(),
  adminId: USERS[0].id,
  adminUsername: 'red_admin',
  action,
  category,
  targetType: category === 'USER' ? 'USER' : 'SYSTEM',
  targetId: USERS[i % USERS.length].id,
  description,
  metadata: null,
  ipAddress: '192.168.11.20',
  userAgent: 'Mozilla/5.0 (X11; Linux x86_64)',
  severity,
  createdAt: iso(0, i * 3),
}));

const REPORTS = [
  ['SPAM', 'PENDING', 'رسائل ترويجية متكررة'],
  ['ABUSE', 'PENDING', 'لغة مسيئة داخل مجموعة'],
  ['IMPERSONATION', 'RESOLVED', 'انتحال هوية حساب إداري'],
  ['OTHER', 'DISMISSED', 'بلاغ غير مكتمل'],
].map(([category, status, description], i) => ({
  id: uuid(),
  reporterId: USERS[(i + 3) % USERS.length].id,
  reporterUsername: USERS[(i + 3) % USERS.length].username,
  reportedUserId: USERS[(i + 5) % USERS.length].id,
  reportedUsername: USERS[(i + 5) % USERS.length].username,
  category,
  status,
  description,
  contentType: 'MESSAGE',
  contentId: uuid(),
  assignedTo: null,
  resolution: status === 'RESOLVED' ? 'تم اتخاذ إجراء' : null,
  notes: null,
  createdAt: iso(0, i * 5),
  resolvedAt: status === 'RESOLVED' ? iso(0, 1) : null,
}));

const BACKUPS = [
  ['FULL', 'COMPLETED', 4_294_967_296],
  ['INCREMENTAL', 'COMPLETED', 536_870_912],
  ['CONFIG_ONLY', 'VERIFIED', 12_582_912],
  ['USER_DATA', 'IN_PROGRESS', 1_073_741_824],
].map(([backupType, status, sizeBytes], i) => ({
  id: uuid(),
  backupType,
  status,
  sizeBytes,
  location: `minio://red-backups/${backupType.toLowerCase()}-${i}.tar.zst`,
  checksum: crypto.randomBytes(16).toString('hex'),
  notes: null,
  startedAt: iso(i),
  completedAt: status === 'IN_PROGRESS' ? null : iso(i),
  createdBy: 'red_admin',
}));

const SESSIONS = [
  ['red_admin', '192.168.11.20', true],
  ['ops_admin', '192.168.11.34', false],
].map(([adminUsername, ipAddress, current], i) => ({
  id: uuid(),
  adminId: USERS[0].id,
  adminUsername,
  ipAddress,
  userAgent: 'Mozilla/5.0 (X11; Linux x86_64)',
  isCurrent: current,
  createdAt: iso(0, i * 4),
  lastActivityAt: iso(0, i),
  expiresAt: iso(-1),
}));

// ─── DINSTAR UC2000-VE-8G: ثماني شرائح، مشغّلون يمنيون حقيقيون ───
const OPERATORS = ['Sabafon', 'YOU', 'Yemen Mobile', 'Y Telecom', 'Yemen Mobile', 'Sabafon', 'YOU', 'Yemen Mobile'];
const dinstarSlots = () =>
  Array.from({ length: 8 }, (_, index) => {
    const registered = index !== 5; // منفذ واحد بلا شريحة لإظهار الحالة غير المسجلة
    return {
      index,
      port: index,
      radioType: 'GSM',
      status: registered ? 'REGISTERED' : 'UNREGISTERED',
      callState: index === 2 ? 'ACTIVE' : index === 6 ? 'DIALING' : 'IDLE',
      // إشارة تتذبذب قليلًا كل استعلام كي تُظهر الواجهة أنها حية
      signal: registered ? Math.min(99, 55 + ((index * 7 + Math.floor(Date.now() / 5000)) % 40)) : 0,
      signalRaw: registered ? 18 + (index % 8) : 0,
      gprs: registered ? 'ATTACHED' : 'DETACHED',
      numberMasked: registered ? `+9677${index}****${index}${index}` : null,
      imsiMasked: registered ? `4210${index}******${index}` : null,
      iccidMasked: registered ? `8996701******${index}` : null,
      operator: registered ? OPERATORS[index] : 'UNKNOWN',
    };
  });

const HASHTAGS = ['يونس', 'صنعاء', 'عدن', 'تقنية', 'أمن_المعلومات', 'اليمن', 'برمجة', 'تشفير'].map((tag, i) => ({
  id: uuid(),
  tag,
  usageCount: 1200 - i * 130,
  trendScore: 98 - i * 9,
  isBlocked: false,
  lastUsedAt: iso(0, i),
  createdAt: iso(30 - i),
}));

const STICKER_PACKS = [
  ['حزمة يونس الرسمية', true, true, 0],
  ['تعابير يمنية', false, true, 0],
  ['حزمة رمضان', false, false, 500],
].map(([name, isOfficial, isPublished, priceCents]) => ({
  id: uuid(),
  name,
  description: 'حزمة ملصقات محلية',
  isOfficial,
  isPublished,
  isFree: priceCents === 0,
  priceCents,
  stickerCount: 24,
  coverUrl: null,
  createdAt: iso(10),
}));

const POLLS = [
  ['ما أولوية التطوير القادمة؟', 'ACTIVE', 'SINGLE_CHOICE'],
  ['هل تفضل الوضع الليلي؟', 'CLOSED', 'SINGLE_CHOICE'],
].map(([question, status, pollType], i) => ({
  id: uuid(),
  question,
  pollType,
  status,
  isAnonymous: false,
  allowAddOptions: false,
  totalVotes: 120 - i * 45,
  options: [
    { id: uuid(), text: 'المكالمات', votes: 64 },
    { id: uuid(), text: 'المجموعات', votes: 56 },
  ],
  createdBy: USERS[0].id,
  createdAt: iso(i + 1),
  closesAt: null,
}));

const EVENTS = [
  ['اجتماع فريق التشغيل', 'SCHEDULED', 'MEETING'],
  ['بث مباشر: إطلاق النسخة', 'LIVE', 'BROADCAST'],
  ['ورشة الأمان', 'COMPLETED', 'WORKSHOP'],
].map(([title, status, eventType], i) => ({
  id: uuid(),
  title,
  description: 'حدث داخلي على منصة يونس',
  eventType,
  status,
  visibility: 'PUBLIC',
  rsvpEnabled: true,
  attendeeCount: 40 - i * 12,
  startsAt: iso(-i),
  endsAt: iso(-i - 1),
  createdBy: USERS[0].id,
  createdAt: iso(i + 2),
}));

/** تحليلات آخر 7 أيام — الرئيسية ترسم منها المخططات، فيجب أن تكون مصفوفة. */
function analyticsRange(start, end) {
  const startDate = new Date(`${start}T00:00:00Z`);
  const endDate = new Date(`${end}T00:00:00Z`);
  const rows = [];
  for (let d = new Date(startDate); d <= endDate; d.setUTCDate(d.getUTCDate() + 1)) {
    const i = rows.length;
    rows.push({
      id: uuid(),
      statDate: d.toISOString().slice(0, 10),
      totalUsers: 1180 + i * 24,
      newUsers: 12 + (i % 5) * 3,
      activeUsersDau: 420 + i * 11,
      activeUsersMau: 980 + i * 15,
      pendingApprovals: 3,
      bannedUsers: 1,
      messagesSent: 9800 + i * 640,
      messagesDelivered: 9650 + i * 620,
      messagesRead: 9100 + i * 600,
      voiceMessages: 320 + i * 25,
      mediaUploads: 210 + i * 18,
      mediaBytesUploaded: (18 + i) * 1_073_741_824,
      callsTotal: 260 + i * 20,
      callsAudio: 180 + i * 12,
      callsVideo: 55 + i * 6,
      callsConference: 15 + i,
      callsLive: 4,
      callsPstn: 40 + i * 3,
      callsDurationSeconds: (3600 + i * 250) * 6,
      callsFailed: 6,
      callsMissed: 14,
      dinstarActivePorts: 7,
      dinstarTotalCalls: 40 + i * 3,
      dinstarTotalDurationSeconds: 5400 + i * 300,
      dinstarBalanceRemaining: 24500 - i * 620,
      groupsCreated: 3 + (i % 3),
      storageUsedBytes: (240 + i * 7) * 1_073_741_824,
    });
  }
  return rows;
}

const systemHealth = () => {
  const jitter = (base, spread) => Number((base + Math.random() * spread).toFixed(1));
  return [
    ['backend', 'HEALTHY', 18, 42],
    ['postgresql', 'HEALTHY', 12, 38],
    ['mongodb', 'HEALTHY', 9, 30],
    ['redis', 'HEALTHY', 4, 12],
    ['minio', 'HEALTHY', 6, 22],
    ['media-sfu', 'DEGRADED', 61, 74],
    ['pstn-asterisk', 'HEALTHY', 8, 26],
  ].map(([component, status, cpu, mem]) => ({
    id: uuid(),
    component,
    status,
    cpuUsage: jitter(cpu, 6),
    memoryUsage: jitter(mem, 6),
    diskUsage: jitter(35, 10),
    activeConnections: Math.floor(20 + Math.random() * 120),
    requestsPerSecond: jitter(24, 18),
    averageResponseMs: jitter(38, 25),
    errorRate: status === 'DEGRADED' ? 2.4 : 0.1,
    details: status === 'DEGRADED' ? 'ارتفاع زمن الاستجابة على مسار الوسائط' : null,
    lastCheckAt: nowIso(),
    createdAt: nowIso(),
  }));
};

const currentAnalytics = () => {
  const total = USERS.length;
  const approved = USERS.filter((u) => u.status === 'APPROVED').length;
  return {
    totalUsers: total,
    approvedUsers: approved,
    pendingUsers: USERS.filter((u) => u.status === 'PENDING').length,
    bannedUsers: USERS.filter((u) => u.status === 'BANNED').length,
    newUsers24h: USERS.filter((u) => Date.now() - new Date(u.createdAt).getTime() < 86400000).length,
    approvalRate: total > 0 ? (approved / total) * 100 : 0,
  };
};

// ───────────────────────────── التوجيه ─────────────────────────────
/**
 * جدول المسارات: [method, نمط, معالج].
 * `:param` يلتقط جزءًا واحدًا من المسار. تُطابق بالترتيب، والأكثر تحديدًا أولًا.
 */
const routes = [];
const on = (method, pattern, handler) => routes.push({ method, pattern, handler });

function match(pattern, pathname) {
  const p = pattern.split('/').filter(Boolean);
  const u = pathname.split('/').filter(Boolean);
  if (p.length !== u.length) return null;
  const params = {};
  for (let i = 0; i < p.length; i++) {
    if (p[i].startsWith(':')) params[p[i].slice(1)] = decodeURIComponent(u[i]);
    else if (p[i] !== u[i]) return null;
  }
  return params;
}

const findUser = (id) => USERS.find((u) => u.id === id);
const ok = (data) => ({ status: 200, data });

// ── الصحة والمصادقة ──
on('GET', '/health', () => ok({ status: 'UP', service: 'mock-backend', timestamp: nowIso() }));
on('GET', '/sfu-health', () => ok({ status: 'UP', workers: 4, rooms: 2, peers: 5 }));
on('POST', '/api/auth/login', (_p, _q, body) =>
  ok({
    accessToken: `mock.access.${Date.now()}`,
    refreshToken: `mock.refresh.${Date.now()}`,
    user: { id: USERS[0].id, username: body?.username || 'red_admin', role: 'ADMIN', displayName: 'المسؤول السيادي' },
  })
);
on('POST', '/api/auth/refresh', () =>
  ok({ accessToken: `mock.access.${Date.now()}`, refreshToken: `mock.refresh.${Date.now()}` })
);
on('POST', '/api/auth/logout', () => ok({ success: true }));

// تذكرة WebSocket — الشكل المطلوب { ticket, expiresInSeconds } وإلا فشل سجل النظام
on('POST', '/api/admin/ws-ticket', () =>
  ok({ ticket: crypto.randomBytes(32).toString('base64url'), expiresInSeconds: 30 })
);

// ── اللوحة والتحليلات ──
on('GET', '/api/admin/dashboard/summary', () =>
  ok({
    analytics: currentAnalytics(),
    pendingReports: REPORTS.filter((r) => r.status === 'PENDING').length,
    recentCriticalAlerts: AUDIT.filter((a) => a.severity === 'CRITICAL').length,
    degradedComponents: systemHealth().filter((h) => h.status !== 'HEALTHY').length,
    activeBackups: BACKUPS.filter((b) => b.status === 'IN_PROGRESS').length,
    generatedAt: nowIso(),
  })
);
on('GET', '/api/admin/analytics', (_p, q) =>
  ok(analyticsRange(q.get('start') || iso(6).slice(0, 10), q.get('end') || nowIso().slice(0, 10)))
);
on('GET', '/api/admin/health', () => ok(systemHealth()));
on('GET', '/api/admin/metrics/realtime', () =>
  ok({
    users: currentAnalytics(),
    health: Object.fromEntries(systemHealth().map((h) => [h.component, h])),
    timestamp: nowIso(),
  })
);

// ── المستخدمون ──
on('GET', '/api/admin/users/pending', () => ok(USERS.filter((u) => u.status === 'PENDING')));
on('GET', '/api/admin/users/:userId/overview', (p) => {
  const user = findUser(p.userId);
  if (!user) return { status: 404, data: { error: 'USER_NOT_FOUND' } };
  return ok({
    user,
    devices: user.devices,
    sessions: 1,
    messagesSent: 420,
    callsTotal: 36,
    lastSeen: user.lastSeen,
    pstnEnabled: user.pstnEnabled,
    pstnDailyLimit: user.pstnEnabled ? 20 : 0,
    storageUsedBytes: 1_073_741_824,
  });
});
on('GET', '/api/admin/users/:userId', (p) => {
  const user = findUser(p.userId);
  return user ? ok(user) : { status: 404, data: { error: 'USER_NOT_FOUND' } };
});
on('GET', '/api/admin/users', (_p, q) => {
  const status = q.get('status');
  const role = q.get('role');
  const search = (q.get('search') || '').toLowerCase();
  const filtered = USERS.filter(
    (u) =>
      (!status || u.status === status) &&
      (!role || u.role === role) &&
      (!search ||
        u.username.toLowerCase().includes(search) ||
        u.displayName.toLowerCase().includes(search) ||
        u.redId.toLowerCase().includes(search))
  );
  return ok(page(filtered, Number(q.get('page') || 0), Number(q.get('size') || 50)));
});

const mutateUser = (id, patch) => {
  const user = findUser(id);
  if (!user) return { status: 404, data: { error: 'USER_NOT_FOUND' } };
  Object.assign(user, patch);
  return ok({ success: true, user });
};
on('POST', '/api/admin/users/:userId/approve', (p) =>
  mutateUser(p.userId, { status: 'APPROVED', approvedAt: nowIso() })
);
on('POST', '/api/admin/users/:userId/reject', (p) => mutateUser(p.userId, { status: 'REJECTED' }));
on('POST', '/api/admin/users/:userId/ban', (p) => mutateUser(p.userId, { status: 'BANNED' }));
on('POST', '/api/admin/users/:userId/unban', (p) => mutateUser(p.userId, { status: 'APPROVED' }));
on('PUT', '/api/admin/users/:userId/role', (p, _q, body) => mutateUser(p.userId, { role: body?.role || 'USER' }));
on('POST', '/api/admin/users/:userId/temporary-password', () => ok({ success: true, expiresInMinutes: 30 }));
on('POST', '/api/admin/users/:userId/remote-app-wipe', () => ok({ success: true, queued: true }));
on('POST', '/api/admin/users/action', () => ok({ success: true }));
on('PUT', '/api/admin/users/pstn', (_p, _q, body) => {
  const user = findUser(body?.userId);
  if (user) user.pstnEnabled = Boolean(body?.enabled);
  return ok({ success: true });
});
on('DELETE', '/api/admin/users/:userId', (p) => {
  const i = USERS.findIndex((u) => u.id === p.userId);
  if (i >= 0) USERS.splice(i, 1);
  return ok({ success: i >= 0 });
});

// ── التدقيق والأمان والجلسات ──
on('GET', '/api/admin/audit', (_p, q) => {
  const category = q.get('category');
  const severity = q.get('severity');
  const filtered = AUDIT.filter((a) => (!category || a.category === category) && (!severity || a.severity === severity));
  return ok(page(filtered, Number(q.get('page') || 0), Number(q.get('size') || 50)));
});
on('GET', '/api/admin/security/alerts', (_p, q) => {
  const severity = q.get('severity');
  const alerts = AUDIT.filter((a) => a.severity !== 'INFO' && (!severity || a.severity === severity));
  return ok(page(alerts, Number(q.get('page') || 0), Number(q.get('size') || 50)));
});
on('GET', '/api/admin/sessions', () => ok(SESSIONS));
on('POST', '/api/admin/sessions/:sessionId/terminate', (p) => {
  const i = SESSIONS.findIndex((s) => s.id === p.sessionId);
  if (i >= 0) SESSIONS.splice(i, 1);
  return ok({ success: true });
});
on('POST', '/api/admin/sessions/cleanup', () => ok({ cleanedCount: 0 }));
on('POST', '/api/admin/security/kill-switch', (_p, q) => ok({ success: true, armed: true, reason: q.get('reason') }));
on('POST', '/api/admin/security/wipe', (_p, q) => ok({ success: true, userId: q.get('userId') }));

// ── أعلام الميزات ──
on('GET', '/api/admin/feature-flags', () => ok(FEATURE_FLAGS));
on('PUT', '/api/admin/feature-flags/:name', (p, _q, body) => {
  const flag = FEATURE_FLAGS.find((f) => f.flagName === p.name);
  if (!flag) return { status: 404, data: { error: 'FLAG_NOT_FOUND' } };
  if (body?.enabled !== undefined) flag.enabled = Boolean(body.enabled);
  if (body?.rolloutPercentage !== undefined) flag.rolloutPercentage = Number(body.rolloutPercentage);
  flag.updatedAt = nowIso();
  return ok(flag);
});

// ── البلاغات والإشراف ──
on('GET', '/api/admin/reports', (_p, q) => {
  const status = q.get('status');
  const category = q.get('category');
  const filtered = REPORTS.filter((r) => (!status || r.status === status) && (!category || r.category === category));
  return ok(page(filtered, Number(q.get('page') || 0), Number(q.get('size') || 50)));
});
const resolveReport = (id, status, resolution) => {
  const report = REPORTS.find((r) => r.id === id);
  if (!report) return { status: 404, data: { error: 'REPORT_NOT_FOUND' } };
  report.status = status;
  report.resolution = resolution;
  report.resolvedAt = nowIso();
  return ok(report);
};
on('POST', '/api/admin/reports/:reportId/resolve', (p, _q, body) =>
  resolveReport(p.reportId, 'RESOLVED', body?.resolution || 'تم')
);
on('POST', '/api/admin/reports/:reportId/dismiss', (p) => resolveReport(p.reportId, 'DISMISSED', null));
on('POST', '/api/admin/reports/:reportId/assign', (p, _q, body) => {
  const report = REPORTS.find((r) => r.id === p.reportId);
  if (report) report.assignedTo = body?.adminId || USERS[0].id;
  return ok(report || {});
});
on('GET', '/api/admin/moderation/reports', (_p, q) => {
  const status = q.get('status');
  return ok(REPORTS.filter((r) => !status || r.status === status || (status === 'OPEN' && r.status === 'PENDING')));
});
on('PATCH', '/api/admin/moderation/reports/:id', (p, q) =>
  resolveReport(p.id, q.get('status') || 'RESOLVED', 'تم عبر الإشراف السريع')
);

// ── الإعلانات ──
on('GET', '/api/admin/announcements', (_p, q) => {
  const published = q.get('published');
  return ok(published === null ? ANNOUNCEMENTS : ANNOUNCEMENTS.filter((a) => String(a.isPublished) === published));
});
on('POST', '/api/admin/announcements', (_p, _q, body) => {
  const ann = {
    id: uuid(),
    title: body?.title || 'بلا عنوان',
    body: body?.body || '',
    type: body?.type || 'INFO',
    targetAudience: body?.targetAudience || 'ALL',
    priority: Number(body?.priority || 0),
    isDismissible: body?.isDismissible !== false,
    isPublished: false,
    showFrom: nowIso(),
    showUntil: body?.showUntil || null,
    createdBy: 'red_admin',
    createdAt: nowIso(),
    publishedAt: null,
  };
  ANNOUNCEMENTS.unshift(ann);
  return ok(ann);
});
on('POST', '/api/admin/announcements/:id/publish', (p) => {
  const ann = ANNOUNCEMENTS.find((a) => a.id === p.id);
  if (!ann) return { status: 404, data: { error: 'NOT_FOUND' } };
  ann.isPublished = true;
  ann.publishedAt = nowIso();
  return ok(ann);
});
on('DELETE', '/api/admin/announcements/:id', (p) => {
  const i = ANNOUNCEMENTS.findIndex((a) => a.id === p.id);
  if (i >= 0) ANNOUNCEMENTS.splice(i, 1);
  return ok({ success: i >= 0 });
});

// ── النسخ الاحتياطية ──
on('GET', '/api/admin/backups', (_p, q) => ok(page(BACKUPS, Number(q.get('page') || 0), Number(q.get('size') || 20))));
on('POST', '/api/admin/backups', (_p, _q, body) => {
  const backup = {
    id: uuid(),
    backupType: body?.type || body?.backupType || 'FULL',
    status: 'IN_PROGRESS',
    sizeBytes: 0,
    location: 'minio://red-backups/pending',
    checksum: null,
    notes: body?.notes || null,
    startedAt: nowIso(),
    completedAt: null,
    createdBy: 'red_admin',
  };
  BACKUPS.unshift(backup);
  // محاكاة اكتمال العملية كي تعكس الواجهة تغيّر الحالة عند التحديث
  setTimeout(() => {
    backup.status = 'COMPLETED';
    backup.sizeBytes = 2_147_483_648;
    backup.completedAt = nowIso();
    backup.checksum = crypto.randomBytes(16).toString('hex');
  }, 8000).unref?.();
  return ok(backup);
});
on('POST', '/api/admin/backups/:backupId/restore', () => ok({ success: true, restoreStarted: true }));
on('DELETE', '/api/admin/backups/:backupId', (p) => {
  const i = BACKUPS.findIndex((b) => b.id === p.backupId);
  if (i >= 0) BACKUPS.splice(i, 1);
  return ok({ success: i >= 0 });
});

// ── المحتوى: استطلاعات/أحداث/هاشتاقات/ملصقات ──
on('GET', '/api/admin/content/polls/active', () => ok(POLLS.filter((x) => x.status === 'ACTIVE')));
on('GET', '/api/admin/content/polls/:pollId', (p) => ok(POLLS.find((x) => x.id === p.pollId) || {}));
on('GET', '/api/admin/content/polls', (_p, q) => ok(page(POLLS, Number(q.get('page') || 0), Number(q.get('size') || 50))));
on('POST', '/api/admin/content/polls', (_p, _q, body) => {
  const poll = {
    id: uuid(),
    question: body?.question || '',
    pollType: body?.pollType || 'SINGLE_CHOICE',
    status: 'ACTIVE',
    isAnonymous: Boolean(body?.isAnonymous),
    allowAddOptions: Boolean(body?.allowAddOptions),
    totalVotes: 0,
    options: (body?.options || []).map((text) => ({ id: uuid(), text, votes: 0 })),
    createdBy: USERS[0].id,
    createdAt: nowIso(),
    closesAt: null,
  };
  POLLS.unshift(poll);
  return ok(poll);
});
on('POST', '/api/admin/content/polls/:pollId/close', (p) => {
  const poll = POLLS.find((x) => x.id === p.pollId);
  if (poll) poll.status = 'CLOSED';
  return ok(poll || {});
});
on('DELETE', '/api/admin/content/polls/:pollId', (p) => {
  const i = POLLS.findIndex((x) => x.id === p.pollId);
  if (i >= 0) POLLS.splice(i, 1);
  return ok({ success: i >= 0 });
});

on('GET', '/api/admin/content/events/live', () => ok(EVENTS.filter((e) => e.status === 'LIVE')));
on('GET', '/api/admin/content/events/upcoming', () => ok(EVENTS.filter((e) => e.status === 'SCHEDULED')));
on('GET', '/api/admin/content/events', (_p, q) => ok(page(EVENTS, Number(q.get('page') || 0), Number(q.get('size') || 50))));
on('POST', '/api/admin/content/events', (_p, _q, body) => {
  const event = {
    id: uuid(),
    title: body?.title || '',
    description: body?.description || '',
    eventType: body?.eventType || 'MEETING',
    status: 'SCHEDULED',
    visibility: body?.visibility || 'PUBLIC',
    rsvpEnabled: body?.rsvpEnabled !== false,
    attendeeCount: 0,
    startsAt: body?.startsAt || nowIso(),
    endsAt: body?.endsAt || null,
    createdBy: USERS[0].id,
    createdAt: nowIso(),
  };
  EVENTS.unshift(event);
  return ok(event);
});
on('POST', '/api/admin/content/events/:eventId/cancel', (p) => {
  const event = EVENTS.find((e) => e.id === p.eventId);
  if (event) event.status = 'CANCELLED';
  return ok(event || {});
});
on('DELETE', '/api/admin/content/events/:eventId', (p) => {
  const i = EVENTS.findIndex((e) => e.id === p.eventId);
  if (i >= 0) EVENTS.splice(i, 1);
  return ok({ success: i >= 0 });
});

on('GET', '/api/admin/content/hashtags/trending', (_p, q) =>
  ok(HASHTAGS.slice(0, Number(q.get('limit') || 50)))
);
on('GET', '/api/admin/content/hashtags/popular', (_p, q) =>
  ok([...HASHTAGS].sort((a, b) => b.usageCount - a.usageCount).slice(0, Number(q.get('limit') || 50)))
);
on('GET', '/api/admin/content/hashtags/search', (_p, q) => {
  const query = (q.get('query') || '').toLowerCase();
  return ok(page(HASHTAGS.filter((h) => h.tag.toLowerCase().includes(query)), Number(q.get('page') || 0), Number(q.get('size') || 20)));
});
const setHashtagBlocked = (id, isBlocked) => {
  const tag = HASHTAGS.find((h) => h.id === id);
  if (tag) tag.isBlocked = isBlocked;
  return ok(tag || {});
};
on('POST', '/api/admin/content/hashtags/:hashtagId/block', (p) => setHashtagBlocked(p.hashtagId, true));
on('POST', '/api/admin/content/hashtags/:hashtagId/unblock', (p) => setHashtagBlocked(p.hashtagId, false));

on('GET', '/api/admin/content/sticker-packs', (_p, q) => {
  const official = q.get('official');
  return ok(official === 'true' ? STICKER_PACKS.filter((s) => s.isOfficial) : STICKER_PACKS);
});
on('POST', '/api/admin/content/sticker-packs', (_p, _q, body) => {
  const pack = {
    id: uuid(),
    name: body?.name || '',
    description: body?.description || '',
    isOfficial: Boolean(body?.isOfficial),
    isPublished: false,
    isFree: body?.isFree !== false,
    priceCents: Number(body?.priceCents || 0),
    stickerCount: 0,
    coverUrl: null,
    createdAt: nowIso(),
  };
  STICKER_PACKS.unshift(pack);
  return ok(pack);
});
on('POST', '/api/admin/content/sticker-packs/:packId/publish', (p) => {
  const pack = STICKER_PACKS.find((s) => s.id === p.packId);
  if (pack) pack.isPublished = true;
  return ok(pack || {});
});
on('DELETE', '/api/admin/content/sticker-packs/:packId', (p) => {
  const i = STICKER_PACKS.findIndex((s) => s.id === p.packId);
  if (i >= 0) STICKER_PACKS.splice(i, 1);
  return ok({ success: i >= 0 });
});

// ── DINSTAR ──
on('GET', '/api/admin/dinstar/discover', () =>
  ok({
    success: true,
    gatewayIp: process.env.DINSTAR_IP || '192.168.11.1',
    model: 'UC2000-VE-8G',
    status: 'ONLINE',
    portsDetected: 8,
    message: 'محاكاة تطوير — ليست بوابة حقيقية',
  })
);
on('GET', '/api/admin/dinstar/capabilities', () =>
  ok({ model: 'UC2000-VE-8G', ports: 8, sms: true, ussd: true, voice: true, apiVersion: '1102', digestAuth: true })
);
on('GET', '/api/admin/dinstar/status', () => ok(dinstarSlots()));
on('GET', '/api/admin/dinstar/cdr', () =>
  ok({
    cdr: Array.from({ length: 12 }, (_, i) => ({
      id: uuid(),
      port: i % 8,
      direction: i % 3 === 0 ? 'INBOUND' : 'OUTBOUND',
      callee: `+9677${(100000 + i * 37) % 1000000}`,
      startTime: iso(0, i),
      duration: 30 + i * 17,
      status: i % 5 === 0 ? 'FAILED' : 'ANSWERED',
    })),
  })
);
on('POST', '/api/admin/dinstar/ports/:port/reset', (p) => ok({ success: true, port: Number(p.port) }));
on('POST', '/api/admin/dinstar/ports/:port/ussd', (p, _q, body) =>
  ok({ success: true, port: Number(p.port), code: body?.code, response: 'رصيدك 1,250 ريال' })
);

// ── مسارات master/v1 ──
on('GET', '/api/master/v1/stats/realtime', () => {
  const a = currentAnalytics();
  return ok({
    active_users: 142,
    pending_approvals: a.pendingUsers,
    gsm_signal: 'STABLE',
    db_storage: '4.2 GB / 25 GB',
    messages_24h: 12480,
    messages_today: 12480,
    delivery_rate_percent: 99.2,
    avg_latency_ms: 38,
    system_load: '12%',
    cpu_usage: 18.5,
    memory_usage: 42.1,
    db_health: 'UP',
  });
});
on('GET', '/api/master/v1/hardware/dinstar/slots', () => ok(dinstarSlots()));
on('GET', '/api/master/v1/media/active-calls', () =>
  ok([
    { id: uuid(), type: 'VIDEO', participants: 3, startedAt: iso(0, 0.2), room: 'red-room-1', bitrateKbps: 1800 },
    { id: uuid(), type: 'AUDIO', participants: 2, startedAt: iso(0, 0.5), room: 'red-room-2', bitrateKbps: 64 },
  ])
);

// ── الإشعارات والاجتماعي ──
on('GET', '/api/notifications/unread-count', () => ok({ count: 3 }));
on('GET', '/api/notifications', (_p, q) =>
  ok(
    page(
      [
        ['APPROVAL', 'طلب موافقة جديد', 'جهاز جديد بانتظار التحقق من البصمة'],
        ['SECURITY', 'تنبيه أمني', 'محاولة دخول فاشلة متكررة'],
        ['SYSTEM', 'اكتملت النسخة الاحتياطية', 'نسخة كاملة بحجم 4 غيغابايت'],
      ].map(([type, title, body], i) => ({
        id: uuid(),
        type,
        title,
        body,
        isRead: false,
        createdAt: iso(0, i * 2),
      })),
      Number(q.get('page') || 0),
      Number(q.get('size') || 50)
    )
  )
);
on('PUT', '/api/notifications/read-all', () => ok({ success: true }));
on('PUT', '/api/notifications/:id/read', () => ok({ success: true }));
on('GET', '/api/social/online-contacts', () =>
  ok(USERS.filter((u) => u.status === 'APPROVED').slice(0, 4).map((u) => ({ userId: u.id, username: u.username, status: 'ONLINE' })))
);
on('GET', '/api/social/privacy', () =>
  ok({ lastSeen: 'CONTACTS', profilePhoto: 'EVERYONE', status: 'CONTACTS', readReceipts: 'EVERYONE' })
);
on('PUT', '/api/social/privacy', () => ok({ success: true }));
on('PUT', '/api/social/status', () => ok({ success: true }));
on('GET', '/api/social/status/:userId', (p) => ok({ userId: p.userId, type: 'ONLINE', customText: null }));

// ───────────────────────────── الخادم ─────────────────────────────
const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', req.headers.origin || '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, X-Device-Id');
  res.setHeader('Access-Control-Allow-Credentials', 'true');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    let body = null;
    if (chunks.length) {
      try {
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      } catch {
        body = null;
      }
    }

    try {
      for (const route of routes) {
        if (route.method !== req.method) continue;
        const params = match(route.pattern, parsed.pathname);
        if (!params) continue;
        const result = route.handler(params, parsed.searchParams, body);
        res.writeHead(result.status, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify(result.data));
      }

      // 404 صريح: أفضل من رد نجاح عام يخفي مسارًا ناقصًا ويكسر الواجهة لاحقًا.
      console.warn(`[mock] مسار غير معرّف: ${req.method} ${parsed.pathname}`);
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: 'MOCK_ROUTE_NOT_IMPLEMENTED', method: req.method, path: parsed.pathname }));
    } catch (e) {
      console.error('[mock] خطأ:', e);
      res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: e.message }));
    }
  });
});

// ── بث السجل الحي عبر WebSocket (RFC 6455 يدويًا — بلا اعتماديات) ──
// صفحة "سجل النظام الحي" تطلب تذكرة ثم تفتح /ws/admin/logs?ticket=...
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function wsFrame(text) {
  const payload = Buffer.from(text, 'utf8');
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.from([0x81, len]);
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x81;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

server.on('upgrade', (req, socket) => {
  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (parsed.pathname !== WS_PATH || !parsed.searchParams.get('ticket')) {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    return socket.destroy();
  }

  const accept = crypto
    .createHash('sha1')
    .update((req.headers['sec-websocket-key'] || '') + GUID)
    .digest('base64');
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
      'Upgrade: websocket\r\n' +
      'Connection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${accept}\r\n\r\n`
  );

  const samples = [
    'INFO  [auth] تم إصدار رمز وصول للمسؤول red_admin',
    'INFO  [ws] عميل إدارة متصل بقناة السجل',
    'INFO  [dinstar] استعلام حالة المنافذ — 7/8 مسجلة',
    'WARN  [media-sfu] ارتفاع زمن الاستجابة إلى 240ms',
    'INFO  [backup] تقدم النسخ الاحتياطي 42%',
    'INFO  [messages] توجيه رسالة مشفرة (metadata فقط)',
    'ERROR [pstn] فشل تسجيل المنفذ 6 — لا توجد شريحة',
    'INFO  [health] فحص دوري: postgresql=UP redis=UP minio=UP',
  ];

  socket.write(wsFrame(`${nowIso()}  INFO  [mock] بدأ بث السجل الحي (بيانات تطوير)`));
  let i = 0;
  const timer = setInterval(() => {
    if (socket.destroyed) return clearInterval(timer);
    socket.write(wsFrame(`${nowIso()}  ${samples[i++ % samples.length]}`));
  }, 2000);

  socket.on('close', () => clearInterval(timer));
  socket.on('error', () => clearInterval(timer));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Mock Backend API listening on 0.0.0.0:${PORT}`);
  console.log(`  • ${routes.length} مسار HTTP مطابق لعقد AdminV2Controller`);
  console.log(`  • بث السجل الحي على ws://0.0.0.0:${PORT}${WS_PATH}?ticket=...`);
});
