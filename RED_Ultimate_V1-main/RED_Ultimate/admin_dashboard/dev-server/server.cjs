/**
 * ══════════════════════════════════════════════════════════════════════
 * خادم تطوير لوحة الإدارة — مدعوم بقاعدة بيانات SQLite حقيقية
 * ══════════════════════════════════════════════════════════════════════
 *
 * ينفّذ عقد `backend-server` الحقيقي فوق تخزين دائم، فتعمل كل صفحة وكل زر
 * ببيانات فعلية تبقى بعد إعادة التشغيل — بدل بيانات ثابتة في الذاكرة.
 *
 * مصدر الحقيقة للأشكال والسلوك (منقول لا مُخمَّن):
 *   admin/controller/AdminV2Controller.kt   — صفحات Pageable ومصفوفات
 *   controllers/AdminController.kt          — /users/action و /users/pending
 *   auth/RedApprovalService.kt              — منطق الموافقة وإصدار الشهادات
 *   auth/AuthDtos.kt                        — UserAccountResponse/DeviceResponse
 *   auth/security/DeviceCertificateService.kt — توقيع SHA256withECDSA
 *
 * سلوك مطابق للخادم عند الموافقة (RedApprovalService.processAction):
 *   • APPROVED → إصدار شهادة موقّعة لكل جهاز PENDING وتحويله إلى APPROVED
 *   • REJECTED/BANNED → إبطال جلسات التحديث وإلغاء كل الأجهزة (REVOKED)
 *   • حسابات ADMIN لا تُحظر عبر هذا المسار
 *   • تسجيل تدقيق ACCOUNT_<الحالة> في كل الحالات
 *
 * ⚠️ تطوير محلي فقط: بلا مصادقة حقيقية، وغير مُضمَّن في صورة Docker.
 *    الإنتاج يمر عبر backend-server + PostgreSQL/Mongo/Redis/MinIO.
 */
const http = require('node:http');
const crypto = require('node:crypto');
const d = require('./db.cjs');

const PORT = Number(process.env.MOCK_PORT || 8080);
const WS_PATH = '/ws/admin/logs';
const { uuid, nowIso, iso, all, get, run, recordAudit } = d;

// ───────────────────────── محوّلات الصفوف → عقد الواجهة ─────────────────────────
const deviceDto = (r) => ({
  id: r.id,
  deviceName: r.device_name,
  name: r.device_name,
  platform: r.platform,
  identityFingerprint: r.identity_fingerprint,
  status: r.status,
  authorizationCertificate: r.authorization_certificate,
  certificateExpiresAt: r.certificate_expires_at,
  createdAt: r.created_at,
});

const devicesOf = (userId) =>
  all('SELECT * FROM devices WHERE user_id = ? ORDER BY created_at ASC', userId).map(deviceDto);

const userDto = (r, withDevices = true) => ({
  id: r.id,
  redId: r.red_id,
  username: r.username,
  displayName: r.display_name,
  status: r.status,
  role: r.role,
  pstnEnabled: !!r.pstn_enabled,
  pstnDailyLimit: r.pstn_daily_limit,
  rejectionReason: r.rejection_reason,
  createdAt: r.created_at,
  updatedAt: r.updated_at,
  approvedAt: r.approved_at,
  lastSeen: r.last_seen,
  ...(withDevices ? { devices: devicesOf(r.id) } : {}),
});

const auditDto = (r) => ({
  id: r.id, adminId: r.admin_id, adminUsername: r.admin_username,
  action: r.action, category: r.category, targetType: r.target_type, targetId: r.target_id,
  description: r.description, metadata: r.metadata, ipAddress: r.ip_address,
  userAgent: r.user_agent, severity: r.severity, createdAt: r.created_at,
});

const annDto = (r) => ({
  id: r.id, title: r.title, body: r.body, type: r.type, targetAudience: r.target_audience,
  priority: r.priority, isDismissible: !!r.is_dismissible, isPublished: !!r.is_published,
  showFrom: r.show_from, showUntil: r.show_until, createdBy: r.created_by,
  createdAt: r.created_at, publishedAt: r.published_at,
});

const flagDto = (r) => ({
  id: r.id, flagName: r.flag_name, description: r.description,
  enabled: !!r.enabled, rolloutPercentage: r.rollout_percentage,
  targetUserIds: null, targetGroups: null, updatedAt: r.updated_at,
});

const reportDto = (r) => ({
  id: r.id, reporterId: r.reporter_id, reporterUsername: r.reporter_username,
  reportedUserId: r.reported_user_id, reportedUsername: r.reported_username,
  category: r.category, status: r.status, description: r.description,
  contentType: r.content_type, contentId: r.content_id, assignedTo: r.assigned_to,
  resolution: r.resolution, notes: r.notes, createdAt: r.created_at, resolvedAt: r.resolved_at,
});

const backupDto = (r) => ({
  id: r.id, backupType: r.backup_type, status: r.status, sizeBytes: r.size_bytes,
  location: r.location, checksum: r.checksum, notes: r.notes,
  startedAt: r.started_at, completedAt: r.completed_at, createdBy: r.created_by,
});

const sessionDto = (r) => ({
  id: r.id, adminId: r.admin_id, adminUsername: r.admin_username, ipAddress: r.ip_address,
  userAgent: r.user_agent, isCurrent: !!r.is_current, createdAt: r.created_at,
  lastActivityAt: r.last_activity_at, expiresAt: r.expires_at,
});

const pollDto = (r) => ({
  id: r.id, question: r.question, pollType: r.poll_type, status: r.status,
  isAnonymous: !!r.is_anonymous, allowAddOptions: !!r.allow_add_options,
  options: JSON.parse(r.options), totalVotes: r.total_votes,
  createdBy: r.created_by, createdAt: r.created_at, closesAt: r.closes_at,
});

const eventDto = (r) => ({
  id: r.id, title: r.title, description: r.description, eventType: r.event_type,
  status: r.status, visibility: r.visibility, rsvpEnabled: !!r.rsvp_enabled,
  attendeeCount: r.attendee_count, startsAt: r.starts_at, endsAt: r.ends_at,
  createdBy: r.created_by, createdAt: r.created_at,
});

const tagDto = (r) => ({
  id: r.id, tag: r.tag, usageCount: r.usage_count, trendScore: r.trend_score,
  isBlocked: !!r.is_blocked, lastUsedAt: r.last_used_at, createdAt: r.created_at,
});

const packDto = (r) => ({
  id: r.id, name: r.name, description: r.description, isOfficial: !!r.is_official,
  isPublished: !!r.is_published, isFree: !!r.is_free, priceCents: r.price_cents,
  stickerCount: r.sticker_count, coverUrl: r.cover_url, createdAt: r.created_at,
});

/** صفحة Spring Pageable كما يبنيها AdminV2Controller. */
function page(items, pageNum = 0, size = 50) {
  const from = pageNum * size;
  return {
    content: items.slice(from, from + size),
    page: pageNum, size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

const adminRow = () => get("SELECT * FROM users WHERE role='ADMIN' LIMIT 1");

// ───────────────────────── مقاييس حية ─────────────────────────
const currentAnalytics = () => {
  const c = (s) => get('SELECT COUNT(*) c FROM users WHERE status = ?', s).c;
  const total = get('SELECT COUNT(*) c FROM users').c;
  const approved = c('APPROVED');
  return {
    totalUsers: total, approvedUsers: approved,
    pendingUsers: c('PENDING'), bannedUsers: c('BANNED'),
    newUsers24h: get("SELECT COUNT(*) c FROM users WHERE created_at > ?", iso(1)).c,
    approvalRate: total > 0 ? (approved / total) * 100 : 0,
  };
};

const systemHealth = () => {
  const j = (b, s) => Number((b + Math.random() * s).toFixed(1));
  return [
    ['backend', 'HEALTHY', 18, 42], ['postgresql', 'HEALTHY', 12, 38],
    ['mongodb', 'HEALTHY', 9, 30], ['redis', 'HEALTHY', 4, 12],
    ['minio', 'HEALTHY', 6, 22], ['media-sfu', 'DEGRADED', 61, 74],
    ['pstn-asterisk', 'HEALTHY', 8, 26],
  ].map(([component, status, cpu, mem]) => ({
    id: uuid(), component, status,
    cpuUsage: j(cpu, 6), memoryUsage: j(mem, 6), diskUsage: j(35, 10),
    activeConnections: Math.floor(20 + Math.random() * 120),
    requestsPerSecond: j(24, 18), averageResponseMs: j(38, 25),
    errorRate: status === 'DEGRADED' ? 2.4 : 0.1,
    details: status === 'DEGRADED' ? 'ارتفاع زمن الاستجابة على مسار الوسائط' : null,
    lastCheckAt: nowIso(), createdAt: nowIso(),
  }));
};

const OPERATORS = ['Sabafon', 'YOU', 'Yemen Mobile', 'Y Telecom', 'Yemen Mobile', 'Sabafon', 'YOU', 'Yemen Mobile'];
const dinstarSlots = () =>
  Array.from({ length: 8 }, (_, index) => {
    const registered = index !== 5;
    return {
      index, port: index, radioType: 'GSM',
      status: registered ? 'REGISTERED' : 'UNREGISTERED',
      callState: index === 2 ? 'ACTIVE' : index === 6 ? 'DIALING' : 'IDLE',
      signal: registered ? Math.min(99, 55 + ((index * 7 + Math.floor(Date.now() / 5000)) % 40)) : 0,
      signalRaw: registered ? 18 + (index % 8) : 0,
      gprs: registered ? 'ATTACHED' : 'DETACHED',
      numberMasked: registered ? `+9677${index}****${index}${index}` : null,
      imsiMasked: registered ? `4210${index}******${index}` : null,
      iccidMasked: registered ? `8996701******${index}` : null,
      operator: registered ? OPERATORS[index] : 'UNKNOWN',
    };
  });

// ───────────────────────────── التوجيه ─────────────────────────────
const routes = [];
/**
 * التسجيل الأول يفوز عند التطابق. المسارات المعرّفة في هذا الملف تُسجَّل
 * قبل مسارات التطبيق، فيستطيع `/api/auth/login` هنا أن يغلّف نسخة التطبيق
 * (يضيف فتح جلسة إدارية) دون أن تتعارض النسختان.
 */
const on = (method, pattern, handler) => routes.push({ method, pattern, handler });

// مسارات تطبيق الهاتف (red-app) على نفس القاعدة — تُسجَّل في نهاية الملف
// بعد مسارات اللوحة، لتغطية /api/contacts/* و/api/feed/* و/api/groups/* إلخ.
const appRoutes = require('./app-routes.cjs');
const ok = (data) => ({ status: 200, data });
const notFound = (error = 'NOT_FOUND') => ({ status: 404, data: { error } });

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

// ── الصحة والمصادقة ──
on('GET', '/health', () => ok({ status: 'UP', service: 'red-dev-server', db: 'sqlite', timestamp: nowIso() }));
on('GET', '/sfu-health', () => ok({ status: 'UP', workers: 4, rooms: 2, peers: 5 }));
/**
 * تسجيل الدخول الموحّد — نفس المسار الذي يستخدمه التطبيق واللوحة، تمامًا
 * كما في الخادم الحقيقي (AuthController واحد لا اثنان).
 *
 * كان هذا سببًا مباشرًا لعطل «لا يوجد RED ID في التطبيق»: النسخة السابقة
 * كانت تُرجع دائمًا حساب المسؤول وبلا حقل redId إطلاقًا، بينما
 * red-app/auth/AuthModels.kt يُعرّف UserResponse.redId كحقل إلزامي — فيفشل
 * فك الترميز ويبقى TokenStore.redId فارغًا في كل شاشات التطبيق.
 *
 * التفويض الحقيقي في appRoutes.loginAccount: يتحقق من المستخدم المطلوب،
 * ويمنع غير المعتمدين، ويعيد UserResponse كاملًا بـ redId.
 */
on('POST', '/api/auth/login', (_p, _q, body, ctx) => {
  const result = appRoutes.loginAccount(body);
  // الدخول الإداري الناجح يفتح جلسة تظهر في صفحة الجلسات ويمكن إنهاؤها
  if (result.status === 200 && result.data?.user?.role === 'ADMIN' && result.data.accessToken) {
    const u = result.data.user;
    run(`INSERT INTO admin_sessions (id,admin_id,admin_username,ip_address,user_agent,is_current,created_at,last_activity_at,expires_at)
         VALUES (?,?,?,?,?,1,?,?,?)`,
      uuid(), u.id, u.username, ctx?.ip || '127.0.0.1', ctx?.userAgent || 'dev-client',
      nowIso(), nowIso(), new Date(Date.now() + 12 * 3600000).toISOString());
    recordAudit({ adminId: u.id, action: 'ADMIN_LOGIN', category: 'SECURITY', description: 'دخول المسؤول' });
  }
  return result;
});
on('POST', '/api/admin/ws-ticket', () =>
  ok({ ticket: crypto.randomBytes(32).toString('base64url'), expiresInSeconds: 30 }));

// ── اللوحة والتحليلات ──
on('GET', '/api/admin/dashboard/summary', () => ok({
  analytics: currentAnalytics(),
  pendingReports: get("SELECT COUNT(*) c FROM reports WHERE status='PENDING'").c,
  recentCriticalAlerts: get("SELECT COUNT(*) c FROM audit_log WHERE severity='CRITICAL'").c,
  degradedComponents: systemHealth().filter((h) => h.status !== 'HEALTHY').length,
  activeBackups: get("SELECT COUNT(*) c FROM backups WHERE status='IN_PROGRESS'").c,
  generatedAt: nowIso(),
}));
on('GET', '/api/admin/analytics', (_p, q) => {
  const start = q.get('start') || iso(6).slice(0, 10);
  const end = q.get('end') || nowIso().slice(0, 10);
  return ok(
    all('SELECT payload FROM analytics_daily WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date ASC', start, end)
      .map((r) => JSON.parse(r.payload))
  );
});
on('GET', '/api/admin/health', () => ok(systemHealth()));
on('GET', '/api/admin/metrics/realtime', () => ok({
  users: currentAnalytics(),
  health: Object.fromEntries(systemHealth().map((h) => [h.component, h])),
  timestamp: nowIso(),
}));

// ── المستخدمون ──
on('GET', '/api/admin/users/pending', () =>
  ok(all("SELECT * FROM users WHERE status='PENDING' ORDER BY created_at ASC").map((r) => userDto(r))));

on('GET', '/api/admin/users/:userId/overview', (p) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  return ok({
    user: userDto(u), devices: devicesOf(u.id),
    online: u.status === 'APPROVED', sessions: u.status === 'APPROVED' ? 1 : 0,
    messagesSent: 420, messagesReceived: 388, callsTotal: 36,
    lastSeen: u.last_seen, pstnEnabled: !!u.pstn_enabled, pstnDailyLimit: u.pstn_daily_limit,
    storageUsedBytes: 1073741824,
  });
});

on('GET', '/api/admin/users/:userId', (p) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  return u ? ok(userDto(u)) : notFound('USER_NOT_FOUND');
});

on('GET', '/api/admin/users', (_p, q) => {
  const status = q.get('status'); const role = q.get('role');
  const search = (q.get('search') || '').toLowerCase();
  const dir = (q.get('sortDir') || 'desc').toLowerCase() === 'asc' ? 'ASC' : 'DESC';
  const rows = all(`SELECT * FROM users ORDER BY created_at ${dir}`).filter((u) =>
    (!status || u.status === status) && (!role || u.role === role) &&
    (!search || u.username.toLowerCase().includes(search) ||
      u.display_name.toLowerCase().includes(search) || u.red_id.toLowerCase().includes(search)));
  return ok(page(rows.map((r) => userDto(r)), Number(q.get('page') || 0), Number(q.get('size') || 50)));
});

/**
 * منطق الموافقة — نسخة مطابقة لـ RedApprovalService.processAction.
 * كان هذا مصدر العطل: الإجراء كان يرد نجاحًا دون تغيير الحالة،
 * فيبقى المستخدم في قائمة "الموافقات المعلقة" بعد الموافقة عليه.
 */
const ALLOWED_ACTIONS = new Set(['APPROVED', 'REJECTED', 'SUSPENDED', 'BANNED']);

function processAction(userId, action, reason, adminId) {
  if (!ALLOWED_ACTIONS.has(action)) return { status: 400, data: { error: `Unsupported account action: ${action}` } };
  const user = get('SELECT * FROM users WHERE id = ?', userId);
  if (!user) return notFound('USER_NOT_FOUND');
  if (user.role === 'ADMIN' && action !== 'APPROVED') {
    return { status: 400, data: { error: 'Administrator accounts cannot be blocked through this endpoint' } };
  }

  const cleanReason = (reason || '').trim() || null;
  const ts = nowIso();

  if (action === 'APPROVED') {
    run(`UPDATE users SET status=?, updated_at=?, approved_at=?, approved_by=?, rejection_reason=NULL WHERE id=?`,
      action, ts, ts, adminId, userId);
    // إصدار شهادة تفويض موقّعة لكل جهاز معلّق (كما يفعل DeviceCertificateService)
    for (const dev of all("SELECT * FROM devices WHERE user_id=? AND status='PENDING'", userId)) {
      const cert = d.issueDeviceCertificate(user, dev);
      run(`UPDATE devices SET authorization_certificate=?, certificate_expires_at=?, status='APPROVED', approved_at=? WHERE id=?`,
        cert.compact, cert.expiresAt, ts, dev.id);
    }
  } else {
    run(`UPDATE users SET status=?, updated_at=?, rejection_reason=? WHERE id=?`, action, ts, cleanReason, userId);
    // إبطال كل جلسات التحديث
    run(`UPDATE refresh_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL`, ts, userId);
    if (action === 'REJECTED' || action === 'BANNED') {
      run(`UPDATE devices SET status='REVOKED', revoked_at=? WHERE user_id=? AND status<>'REVOKED'`, ts, userId);
    }
  }

  recordAudit({
    adminId, action: `ACCOUNT_${action}`, category: 'USER', targetType: 'USER', targetId: userId,
    description: `${user.red_id}${cleanReason ? ` — ${cleanReason}` : ''}`,
    severity: action === 'APPROVED' ? 'INFO' : 'WARNING',
  });

  return ok(userDto(get('SELECT * FROM users WHERE id = ?', userId)));
}

// المسار الذي تستدعيه شاشة "الموافقات المعلقة"
on('POST', '/api/admin/users/action', (_p, _q, body) =>
  processAction(body?.userId, body?.action, body?.reason, adminRow().id));

on('POST', '/api/admin/users/:userId/approve', (p) => processAction(p.userId, 'APPROVED', null, adminRow().id));
on('POST', '/api/admin/users/:userId/reject', (p, _q, b) => processAction(p.userId, 'REJECTED', b?.reason, adminRow().id));
on('POST', '/api/admin/users/:userId/ban', (p, _q, b) => processAction(p.userId, 'BANNED', b?.reason, adminRow().id));
on('POST', '/api/admin/users/:userId/unban', (p) => processAction(p.userId, 'APPROVED', null, adminRow().id));
on('POST', '/api/admin/users/approve', (_p, q) => processAction(q.get('userId'), 'APPROVED', null, adminRow().id));
on('POST', '/api/admin/users/update-status', (_p, q) =>
  processAction(q.get('userId'), String(q.get('status') || '').toUpperCase(), null, adminRow().id));

on('PUT', '/api/admin/users/:userId/role', (p, _q, body) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  run('UPDATE users SET role=?, updated_at=? WHERE id=?', body?.role || 'USER', nowIso(), p.userId);
  recordAudit({ adminId: adminRow().id, action: 'USER_ROLE_CHANGED', category: 'USER', targetId: p.userId,
    description: `${u.red_id} → ${body?.role}`, severity: 'WARNING' });
  return ok(userDto(get('SELECT * FROM users WHERE id = ?', p.userId)));
});

on('POST', '/api/admin/users/:userId/temporary-password', (p) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  run('UPDATE refresh_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL', nowIso(), p.userId);
  recordAudit({ adminId: adminRow().id, action: 'TEMPORARY_PASSWORD_ISSUED', category: 'SECURITY',
    targetId: p.userId, description: u.red_id, severity: 'WARNING' });
  return ok({ success: true, expiresInMinutes: 30 });
});

on('POST', '/api/admin/users/:userId/remote-app-wipe', (p) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  run("UPDATE devices SET status='REVOKED', revoked_at=? WHERE user_id=?", nowIso(), p.userId);
  run('UPDATE refresh_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL', nowIso(), p.userId);
  recordAudit({ adminId: adminRow().id, action: 'REMOTE_WIPE_SENT', category: 'SECURITY',
    targetId: p.userId, description: u.red_id, severity: 'CRITICAL' });
  return ok({ success: true, queued: true });
});

on('PUT', '/api/admin/users/pstn', (_p, _q, body) => {
  const u = get('SELECT * FROM users WHERE id = ?', body?.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  run('UPDATE users SET pstn_enabled=?, pstn_daily_limit=?, updated_at=? WHERE id=?',
    body?.enabled ? 1 : 0, Number(body?.dailyLimit || 0), nowIso(), body.userId);
  recordAudit({ adminId: adminRow().id, action: 'PSTN_ACCESS_UPDATED', category: 'USER',
    targetId: body.userId, description: `${u.red_id} → ${body?.enabled ? 'مفعّل' : 'موقوف'}` });
  return ok({ success: true });
});

on('DELETE', '/api/admin/users/:userId', (p, q) => {
  const u = get('SELECT * FROM users WHERE id = ?', p.userId);
  if (!u) return notFound('USER_NOT_FOUND');
  if (q.get('hard') === 'true') run('DELETE FROM users WHERE id = ?', p.userId);
  else run("UPDATE users SET status='BANNED', updated_at=? WHERE id=?", nowIso(), p.userId);
  recordAudit({ adminId: adminRow().id, action: 'USER_DELETED', category: 'USER',
    targetId: p.userId, description: u.red_id, severity: 'CRITICAL' });
  return ok({ success: true });
});

// ── التدقيق والأمان والجلسات ──
on('GET', '/api/admin/audit', (_p, q) => {
  const category = q.get('category'); const severity = q.get('severity'); const action = q.get('action');
  const rows = all('SELECT * FROM audit_log ORDER BY created_at DESC').filter((r) =>
    (!category || r.category === category) && (!severity || r.severity === severity) && (!action || r.action === action));
  return ok(page(rows.map(auditDto), Number(q.get('page') || 0), Number(q.get('size') || 50)));
});

on('GET', '/api/admin/security/alerts', (_p, q) => {
  const severity = q.get('severity');
  const rows = all("SELECT * FROM audit_log WHERE severity <> 'INFO' ORDER BY created_at DESC")
    .filter((r) => !severity || r.severity === severity);
  return ok(page(rows.map(auditDto), Number(q.get('page') || 0), Number(q.get('size') || 50)));
});

on('GET', '/api/admin/sessions', () =>
  ok(all('SELECT * FROM admin_sessions ORDER BY created_at DESC').map(sessionDto)));

on('POST', '/api/admin/sessions/:sessionId/terminate', (p, _q, b) => {
  run('DELETE FROM admin_sessions WHERE id = ?', p.sessionId);
  recordAudit({ adminId: adminRow().id, action: 'SESSION_TERMINATED', category: 'SECURITY',
    targetId: p.sessionId, description: b?.reason || 'ADMIN_ACTION', severity: 'WARNING' });
  return ok({ success: true });
});

on('POST', '/api/admin/sessions/cleanup', () => {
  const before = get('SELECT COUNT(*) c FROM admin_sessions').c;
  run("DELETE FROM admin_sessions WHERE is_current = 0 AND expires_at < ?", nowIso());
  const cleaned = before - get('SELECT COUNT(*) c FROM admin_sessions').c;
  recordAudit({ adminId: adminRow().id, action: 'SESSIONS_CLEANED', description: `${cleaned} جلسة` });
  return ok({ cleanedCount: cleaned });
});

on('POST', '/api/admin/security/kill-switch', (_p, q) => {
  recordAudit({ adminId: adminRow().id, action: 'KILL_SWITCH_ACTIVATED', category: 'SECURITY',
    description: q.get('reason') || '—', severity: 'CRITICAL' });
  return ok({ success: true, armed: true, reason: q.get('reason') });
});

on('POST', '/api/admin/security/wipe', (_p, q) => {
  const userId = q.get('userId');
  if (userId) {
    run("UPDATE devices SET status='REVOKED', revoked_at=? WHERE user_id=?", nowIso(), userId);
    run('UPDATE refresh_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL', nowIso(), userId);
  }
  recordAudit({ adminId: adminRow().id, action: 'REMOTE_WIPE_SENT', category: 'SECURITY',
    targetId: userId, severity: 'CRITICAL' });
  return ok({ success: true, userId });
});

/** تحقق حقيقي من توقيع شهادة الجهاز — يثبت أن الاعتماد صحيح تشفيريًا. */
on('GET', '/api/admin/security/device-certificate/:deviceId', (p) => {
  const dev = get('SELECT * FROM devices WHERE id = ?', p.deviceId);
  if (!dev) return notFound('DEVICE_NOT_FOUND');
  if (!dev.authorization_certificate) return ok({ valid: false, reason: 'NO_CERTIFICATE', status: dev.status });
  return ok({ ...d.verifyDeviceCertificate(dev.authorization_certificate), status: dev.status });
});
// /api/identity/authority معرّف في app-routes.cjs (يستخدمه التطبيق واللوحة معًا)

// ── أعلام الميزات ──
on('GET', '/api/admin/feature-flags', () =>
  ok(all('SELECT * FROM feature_flags ORDER BY flag_name').map(flagDto)));
on('PUT', '/api/admin/feature-flags/:name', (p, _q, body) => {
  const f = get('SELECT * FROM feature_flags WHERE flag_name = ?', p.name);
  if (!f) return notFound('FLAG_NOT_FOUND');
  run('UPDATE feature_flags SET enabled=?, rollout_percentage=?, updated_at=? WHERE flag_name=?',
    body?.enabled !== undefined ? (body.enabled ? 1 : 0) : f.enabled,
    body?.rolloutPercentage !== undefined ? Number(body.rolloutPercentage) : f.rollout_percentage,
    nowIso(), p.name);
  recordAudit({ adminId: adminRow().id, action: 'FEATURE_FLAG_UPDATED', targetId: p.name, description: p.name });
  return ok(flagDto(get('SELECT * FROM feature_flags WHERE flag_name = ?', p.name)));
});

// ── البلاغات والإشراف ──
on('GET', '/api/admin/reports', (_p, q) => {
  const status = q.get('status'); const category = q.get('category');
  const rows = all('SELECT * FROM reports ORDER BY created_at DESC')
    .filter((r) => (!status || r.status === status) && (!category || r.category === category));
  return ok(page(rows.map(reportDto), Number(q.get('page') || 0), Number(q.get('size') || 50)));
});
const resolveReport = (id, status, resolution, notes) => {
  const r = get('SELECT * FROM reports WHERE id = ?', id);
  if (!r) return notFound('REPORT_NOT_FOUND');
  run('UPDATE reports SET status=?, resolution=?, notes=?, resolved_at=? WHERE id=?',
    status, resolution, notes || null, nowIso(), id);
  recordAudit({ adminId: adminRow().id, action: `REPORT_${status}`, category: 'MODERATION', targetId: id });
  return ok(reportDto(get('SELECT * FROM reports WHERE id = ?', id)));
};
on('POST', '/api/admin/reports/:reportId/resolve', (p, _q, b) =>
  resolveReport(p.reportId, 'RESOLVED', b?.resolution || 'تم', b?.notes));
on('POST', '/api/admin/reports/:reportId/dismiss', (p, _q, b) =>
  resolveReport(p.reportId, 'DISMISSED', null, b?.notes));
on('POST', '/api/admin/reports/:reportId/assign', (p, _q, b) => {
  run('UPDATE reports SET assigned_to=? WHERE id=?', b?.adminId || adminRow().id, p.reportId);
  return ok(reportDto(get('SELECT * FROM reports WHERE id = ?', p.reportId)));
});
/** تقديم بلاغ من مستخدم — المصدر الحقيقي لصفوف صفحة البلاغات (UserReport). */
on('POST', '/api/reports', (_p, _q, b) => {
  const reporter = get('SELECT * FROM users WHERE id = ?', b?.reporterId)
    || get("SELECT * FROM users WHERE status='APPROVED' AND role<>'ADMIN' LIMIT 1");
  const reported = get('SELECT * FROM users WHERE id = ?', b?.reportedUserId);
  const id = uuid();
  run(`INSERT INTO reports (id,reporter_id,reporter_username,reported_user_id,reported_username,category,status,description,content_type,content_id,created_at)
       VALUES (?,?,?,?,?,?,'PENDING',?,?,?,?)`,
    id, reporter?.id || null, reporter?.username || 'unknown',
    reported?.id || null, reported?.display_name || b?.reportedUsername || null,
    b?.category || 'SPAM', b?.reason || b?.description || '', b?.contentType || null,
    b?.contentId || null, nowIso());
  recordAudit({ adminId: null, action: 'REPORT_SUBMITTED', category: 'MODERATION', targetType: 'REPORT',
    targetId: id, description: b?.category || 'SPAM', severity: 'WARNING' });
  return ok(reportDto(get('SELECT * FROM reports WHERE id = ?', id)));
});

on('GET', '/api/admin/moderation/reports', (_p, q) => {
  const status = q.get('status');
  return ok(all('SELECT * FROM reports ORDER BY created_at DESC')
    .filter((r) => !status || r.status === status || (status === 'OPEN' && r.status === 'PENDING'))
    .map(reportDto));
});
on('PATCH', '/api/admin/moderation/reports/:id', (p, q) =>
  resolveReport(p.id, q.get('status') || 'RESOLVED', 'تم عبر الإشراف السريع'));

// ── الإعلانات ──
on('GET', '/api/admin/announcements', (_p, q) => {
  const published = q.get('published');
  return ok(all('SELECT * FROM announcements ORDER BY created_at DESC')
    .filter((r) => published === null || String(!!r.is_published) === published).map(annDto));
});
on('POST', '/api/admin/announcements', (_p, _q, b) => {
  const id = uuid();
  run(`INSERT INTO announcements (id,title,body,type,target_audience,priority,is_dismissible,is_published,show_from,show_until,created_by,created_at)
       VALUES (?,?,?,?,?,?,?,0,?,?,?,?)`,
    id, b?.title || 'بلا عنوان', b?.body || '', b?.type || 'INFO', b?.targetAudience || 'ALL',
    Number(b?.priority || 0), b?.isDismissible === false ? 0 : 1, nowIso(), b?.showUntil || null, 'red_admin', nowIso());
  recordAudit({ adminId: adminRow().id, action: 'ANNOUNCEMENT_CREATED', targetId: id, description: b?.title });
  return ok(annDto(get('SELECT * FROM announcements WHERE id = ?', id)));
});
on('POST', '/api/admin/announcements/:id/publish', (p) => {
  const a = get('SELECT * FROM announcements WHERE id = ?', p.id);
  if (!a) return notFound();
  run('UPDATE announcements SET is_published=1, published_at=? WHERE id=?', nowIso(), p.id);
  recordAudit({ adminId: adminRow().id, action: 'ANNOUNCEMENT_PUBLISHED', targetId: p.id, description: a.title });
  return ok(annDto(get('SELECT * FROM announcements WHERE id = ?', p.id)));
});
on('DELETE', '/api/admin/announcements/:id', (p) => {
  run('DELETE FROM announcements WHERE id = ?', p.id);
  recordAudit({ adminId: adminRow().id, action: 'ANNOUNCEMENT_DELETED', targetId: p.id, severity: 'WARNING' });
  return ok({ success: true });
});

// ── النسخ الاحتياطية ──
on('GET', '/api/admin/backups', (_p, q) =>
  ok(page(all('SELECT * FROM backups ORDER BY started_at DESC').map(backupDto),
    Number(q.get('page') || 0), Number(q.get('size') || 20))));
on('POST', '/api/admin/backups', (_p, _q, b) => {
  const id = uuid();
  const type = b?.type || b?.backupType || 'FULL';
  run(`INSERT INTO backups (id,backup_type,status,size_bytes,location,notes,started_at,created_by)
       VALUES (?,?,'IN_PROGRESS',0,?,?,?,?)`,
    id, type, 'minio://red-backups/pending', b?.notes || null, nowIso(), 'red_admin');
  recordAudit({ adminId: adminRow().id, action: 'BACKUP_CREATED', targetId: id, description: type });
  // اكتمال محاكى كي تعكس الواجهة تغيّر الحالة عند التحديث
  setTimeout(() => {
    try {
      run(`UPDATE backups SET status='COMPLETED', size_bytes=?, completed_at=?, checksum=?, location=? WHERE id=?`,
        2147483648, nowIso(), crypto.randomBytes(16).toString('hex'),
        `minio://red-backups/${type.toLowerCase()}-${id.slice(0, 8)}.tar.zst`, id);
    } catch { /* أُغلقت القاعدة */ }
  }, 8000).unref?.();
  return ok(backupDto(get('SELECT * FROM backups WHERE id = ?', id)));
});
on('POST', '/api/admin/backups/:backupId/restore', (p) => {
  recordAudit({ adminId: adminRow().id, action: 'BACKUP_RESTORED', targetId: p.backupId, severity: 'CRITICAL' });
  return ok({ success: true, restoreStarted: true });
});
on('DELETE', '/api/admin/backups/:backupId', (p) => {
  run('DELETE FROM backups WHERE id = ?', p.backupId);
  recordAudit({ adminId: adminRow().id, action: 'BACKUP_DELETED', targetId: p.backupId, severity: 'WARNING' });
  return ok({ success: true });
});

// ── المحتوى ──
on('GET', '/api/admin/content/polls/active', () =>
  ok(all("SELECT * FROM polls WHERE status='ACTIVE' ORDER BY created_at DESC").map(pollDto)));
on('GET', '/api/admin/content/polls/:pollId', (p) => {
  const r = get('SELECT * FROM polls WHERE id = ?', p.pollId);
  return r ? ok(pollDto(r)) : notFound();
});
on('GET', '/api/admin/content/polls', (_p, q) =>
  ok(page(all('SELECT * FROM polls ORDER BY created_at DESC').map(pollDto),
    Number(q.get('page') || 0), Number(q.get('size') || 50))));
on('POST', '/api/admin/content/polls', (_p, _q, b) => {
  const id = uuid();
  const options = (b?.options || []).map((t) => ({ id: uuid(), text: typeof t === 'string' ? t : t?.text, votes: 0 }));
  run(`INSERT INTO polls (id,question,poll_type,status,is_anonymous,allow_add_options,options,total_votes,created_by,created_at)
       VALUES (?,?,?,'ACTIVE',?,?,?,0,?,?)`,
    id, b?.question || '', b?.pollType || 'SINGLE_CHOICE', b?.isAnonymous ? 1 : 0,
    b?.allowAddOptions ? 1 : 0, JSON.stringify(options), adminRow().id, nowIso());
  recordAudit({ adminId: adminRow().id, action: 'POLL_CREATED', category: 'CONTENT', targetId: id });
  return ok(pollDto(get('SELECT * FROM polls WHERE id = ?', id)));
});
on('POST', '/api/admin/content/polls/:pollId/close', (p) => {
  run("UPDATE polls SET status='CLOSED' WHERE id=?", p.pollId);
  return ok(pollDto(get('SELECT * FROM polls WHERE id = ?', p.pollId)));
});
on('DELETE', '/api/admin/content/polls/:pollId', (p) => {
  run('DELETE FROM polls WHERE id = ?', p.pollId);
  return ok({ success: true });
});

on('GET', '/api/admin/content/events/live', () =>
  ok(all("SELECT * FROM events WHERE status='LIVE' ORDER BY starts_at DESC").map(eventDto)));
on('GET', '/api/admin/content/events/upcoming', () =>
  ok(all("SELECT * FROM events WHERE status='SCHEDULED' ORDER BY starts_at ASC").map(eventDto)));
on('GET', '/api/admin/content/events', (_p, q) =>
  ok(page(all('SELECT * FROM events ORDER BY created_at DESC').map(eventDto),
    Number(q.get('page') || 0), Number(q.get('size') || 50))));
on('POST', '/api/admin/content/events', (_p, _q, b) => {
  const id = uuid();
  run(`INSERT INTO events (id,title,description,event_type,status,visibility,rsvp_enabled,attendee_count,starts_at,ends_at,created_by,created_at)
       VALUES (?,?,?,?,'SCHEDULED',?,?,0,?,?,?,?)`,
    id, b?.title || '', b?.description || '', b?.eventType || 'MEETING', b?.visibility || 'PUBLIC',
    b?.rsvpEnabled === false ? 0 : 1, b?.startsAt || nowIso(), b?.endsAt || null, adminRow().id, nowIso());
  recordAudit({ adminId: adminRow().id, action: 'EVENT_CREATED', category: 'CONTENT', targetId: id });
  return ok(eventDto(get('SELECT * FROM events WHERE id = ?', id)));
});
on('POST', '/api/admin/content/events/:eventId/cancel', (p) => {
  run("UPDATE events SET status='CANCELLED' WHERE id=?", p.eventId);
  return ok(eventDto(get('SELECT * FROM events WHERE id = ?', p.eventId)));
});
on('DELETE', '/api/admin/content/events/:eventId', (p) => {
  run('DELETE FROM events WHERE id = ?', p.eventId);
  return ok({ success: true });
});

on('GET', '/api/admin/content/hashtags/trending', (_p, q) =>
  ok(all('SELECT * FROM hashtags ORDER BY trend_score DESC LIMIT ?', Number(q.get('limit') || 50)).map(tagDto)));
on('GET', '/api/admin/content/hashtags/popular', (_p, q) =>
  ok(all('SELECT * FROM hashtags ORDER BY usage_count DESC LIMIT ?', Number(q.get('limit') || 50)).map(tagDto)));
on('GET', '/api/admin/content/hashtags/search', (_p, q) => {
  const query = (q.get('query') || '').toLowerCase();
  const rows = all('SELECT * FROM hashtags ORDER BY usage_count DESC')
    .filter((r) => r.tag.toLowerCase().includes(query));
  return ok(page(rows.map(tagDto), Number(q.get('page') || 0), Number(q.get('size') || 20)));
});
const setBlocked = (id, blocked) => {
  run('UPDATE hashtags SET is_blocked=? WHERE id=?', blocked ? 1 : 0, id);
  const r = get('SELECT * FROM hashtags WHERE id = ?', id);
  if (r) recordAudit({ adminId: adminRow().id, action: blocked ? 'HASHTAG_BLOCKED' : 'HASHTAG_UNBLOCKED',
    category: 'CONTENT', targetId: id, description: r.tag, severity: blocked ? 'WARNING' : 'INFO' });
  return r ? ok(tagDto(r)) : notFound();
};
on('POST', '/api/admin/content/hashtags/:hashtagId/block', (p) => setBlocked(p.hashtagId, true));
on('POST', '/api/admin/content/hashtags/:hashtagId/unblock', (p) => setBlocked(p.hashtagId, false));

on('GET', '/api/admin/content/sticker-packs', (_p, q) => {
  const official = q.get('official');
  return ok(all('SELECT * FROM sticker_packs ORDER BY created_at DESC')
    .filter((r) => official !== 'true' || r.is_official).map(packDto));
});
on('POST', '/api/admin/content/sticker-packs', (_p, _q, b) => {
  const id = uuid();
  run(`INSERT INTO sticker_packs (id,name,description,is_official,is_published,is_free,price_cents,sticker_count,created_at)
       VALUES (?,?,?,?,0,?,?,0,?)`,
    id, b?.name || '', b?.description || '', b?.isOfficial ? 1 : 0,
    b?.isFree === false ? 0 : 1, Number(b?.priceCents || 0), nowIso());
  return ok(packDto(get('SELECT * FROM sticker_packs WHERE id = ?', id)));
});
on('POST', '/api/admin/content/sticker-packs/:packId/publish', (p) => {
  run('UPDATE sticker_packs SET is_published=1 WHERE id=?', p.packId);
  return ok(packDto(get('SELECT * FROM sticker_packs WHERE id = ?', p.packId)));
});
on('DELETE', '/api/admin/content/sticker-packs/:packId', (p) => {
  run('DELETE FROM sticker_packs WHERE id = ?', p.packId);
  return ok({ success: true });
});

// ── DINSTAR ──
on('GET', '/api/admin/dinstar/discover', () => ok({
  success: true, gatewayIp: process.env.DINSTAR_IP || '192.168.11.1',
  model: 'UC2000-VE-8G', status: 'ONLINE', portsDetected: 8,
  message: 'محاكاة تطوير — ليست بوابة حقيقية',
}));
on('GET', '/api/admin/dinstar/capabilities', () =>
  ok({ model: 'UC2000-VE-8G', ports: 8, sms: true, ussd: true, voice: true, apiVersion: '1102', digestAuth: true }));
on('GET', '/api/admin/dinstar/status', () => ok(dinstarSlots()));
on('GET', '/api/admin/dinstar/cdr', () => ok({
  cdr: Array.from({ length: 12 }, (_, i) => ({
    id: uuid(), port: i % 8, direction: i % 3 === 0 ? 'INBOUND' : 'OUTBOUND',
    callee: `+9677${(100000 + i * 37) % 1000000}`, startTime: iso(0, i),
    duration: 30 + i * 17, status: i % 5 === 0 ? 'FAILED' : 'ANSWERED',
  })),
}));
on('POST', '/api/admin/dinstar/ports/:port/reset', (p) => {
  recordAudit({ adminId: adminRow().id, action: 'DINSTAR_PORT_RESET', category: 'SYSTEM',
    targetId: p.port, description: `إعادة تشغيل المنفذ ${Number(p.port) + 1}`, severity: 'WARNING' });
  return ok({ success: true, port: Number(p.port) });
});
on('POST', '/api/admin/dinstar/ports/:port/ussd', (p, _q, b) => {
  recordAudit({ adminId: adminRow().id, action: 'DINSTAR_USSD_SENT', category: 'SYSTEM',
    targetId: p.port, description: b?.code });
  return ok({ success: true, port: Number(p.port), code: b?.code, response: 'رصيدك 1,250 ريال' });
});

// ── master/v1 ──
on('GET', '/api/master/v1/stats/realtime', () => {
  const a = currentAnalytics();
  const latest = get('SELECT payload FROM analytics_daily ORDER BY stat_date DESC LIMIT 1');
  const day = latest ? JSON.parse(latest.payload) : {};
  return ok({
    active_users: a.approvedUsers, pending_approvals: a.pendingUsers,
    gsm_signal: 'STABLE', db_storage: '4.2 GB / 25 GB',
    messages_24h: day.messagesSent ?? 0, messages_today: day.messagesSent ?? 0,
    delivery_rate_percent: day.messagesSent ? Number(((day.messagesDelivered / day.messagesSent) * 100).toFixed(1)) : 0,
    avg_latency_ms: 38, system_load: '12%', cpu_usage: 18.5, memory_usage: 42.1, db_health: 'UP',
  });
});
on('GET', '/api/master/v1/hardware/dinstar/slots', () => ok(dinstarSlots()));
on('GET', '/api/master/v1/media/active-calls', () => ok([
  { id: uuid(), type: 'VIDEO', participants: 3, startedAt: iso(0, 0.2), room: 'red-room-1', bitrateKbps: 1800 },
  { id: uuid(), type: 'AUDIO', participants: 2, startedAt: iso(0, 0.5), room: 'red-room-2', bitrateKbps: 64 },
]));

// ── الإشعارات والاجتماعي ──
on('GET', '/api/notifications/unread-count', () =>
  ok({ count: get('SELECT COUNT(*) c FROM notifications WHERE is_read = 0').c }));
on('GET', '/api/notifications', (_p, q) =>
  ok(page(all('SELECT * FROM notifications ORDER BY created_at DESC').map((r) => ({
    id: r.id, type: r.type, title: r.title, body: r.body, isRead: !!r.is_read, createdAt: r.created_at,
  })), Number(q.get('page') || 0), Number(q.get('size') || 50))));
on('PUT', '/api/notifications/read-all', () => {
  run('UPDATE notifications SET is_read = 1');
  return ok({ success: true });
});
on('PUT', '/api/notifications/:id/read', (p) => {
  run('UPDATE notifications SET is_read = 1 WHERE id = ?', p.id);
  return ok({ success: true });
});
on('GET', '/api/social/online-contacts', () =>
  ok(all("SELECT * FROM users WHERE status='APPROVED' LIMIT 5")
    .map((u) => ({ userId: u.id, username: u.username, displayName: u.display_name, status: 'ONLINE' }))));
on('GET', '/api/social/privacy', () =>
  ok({ lastSeen: 'CONTACTS', profilePhoto: 'EVERYONE', status: 'CONTACTS', readReceipts: 'EVERYONE' }));
on('PUT', '/api/social/privacy', () => ok({ success: true }));
on('PUT', '/api/social/status', () => ok({ success: true }));
on('GET', '/api/social/status/:userId', (p) => ok({ userId: p.userId, type: 'ONLINE', customText: null }));

// مسارات التطبيق تُسجَّل الآن — بعد كل مسارات اللوحة أعلاه.
appRoutes(on);

// ───────────────────────────── الخادم ─────────────────────────────
const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', req.headers.origin || '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, X-Device-Id');
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }

  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    let body = null;
    if (chunks.length) { try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { body = null; } }
    try {
      for (const route of routes) {
        if (route.method !== req.method) continue;
        const params = match(route.pattern, parsed.pathname);
        if (!params) continue;
        const result = route.handler(params, parsed.searchParams, body, {
          ip: req.socket.remoteAddress, userAgent: req.headers['user-agent'], headers: req.headers,
        });
        res.writeHead(result.status, { 'Content-Type': 'application/json; charset=utf-8' });
        return res.end(JSON.stringify(result.data));
      }
      console.warn(`[dev-server] مسار غير معرّف: ${req.method} ${parsed.pathname}`);
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: 'ROUTE_NOT_IMPLEMENTED', method: req.method, path: parsed.pathname }));
    } catch (e) {
      console.error('[dev-server] خطأ:', e);
      res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ error: e.message }));
    }
  });
});

// ── بث السجل الحي عبر WebSocket (RFC 6455 يدويًا — بلا اعتماديات) ──
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
function wsFrame(text) {
  const payload = Buffer.from(text, 'utf8');
  const len = payload.length;
  let header;
  if (len < 126) header = Buffer.from([0x81, len]);
  else if (len < 65536) { header = Buffer.alloc(4); header[0] = 0x81; header[1] = 126; header.writeUInt16BE(len, 2); }
  else { header = Buffer.alloc(10); header[0] = 0x81; header[1] = 127; header.writeBigUInt64BE(BigInt(len), 2); }
  return Buffer.concat([header, payload]);
}

server.on('upgrade', (req, socket) => {
  const parsed = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (parsed.pathname !== WS_PATH || !parsed.searchParams.get('ticket')) {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    return socket.destroy();
  }
  const accept = crypto.createHash('sha1')
    .update((req.headers['sec-websocket-key'] || '') + GUID).digest('base64');
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
    `Sec-WebSocket-Accept: ${accept}\r\n\r\n`);

  socket.write(wsFrame(`${nowIso()}  INFO  [dev-server] بدأ بث السجل الحي — مصدره سجل التدقيق الفعلي`));
  let lastSeen = nowIso();
  const rotating = [
    'INFO  [dinstar] استعلام حالة المنافذ — 7/8 مسجلة',
    'WARN  [media-sfu] ارتفاع زمن الاستجابة إلى 240ms',
    'INFO  [messages] توجيه رسالة مشفرة (metadata فقط)',
    'INFO  [health] فحص دوري: postgresql=UP redis=UP minio=UP',
  ];
  let i = 0;
  const timer = setInterval(() => {
    if (socket.destroyed) return clearInterval(timer);
    try {
      // بث أحداث التدقيق الحقيقية فور وقوعها — الموافقة تظهر هنا مباشرة
      const fresh = all('SELECT * FROM audit_log WHERE created_at > ? ORDER BY created_at ASC', lastSeen);
      for (const row of fresh) {
        lastSeen = row.created_at;
        socket.write(wsFrame(`${row.created_at}  ${row.severity.padEnd(5)} [audit] ${row.action} — ${row.description || ''}`));
      }
      if (fresh.length === 0) socket.write(wsFrame(`${nowIso()}  ${rotating[i++ % rotating.length]}`));
    } catch { /* أُغلقت القاعدة */ }
  }, 2000);
  socket.on('close', () => clearInterval(timer));
  socket.on('error', () => clearInterval(timer));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`RED dev-server (SQLite) على 0.0.0.0:${PORT}`);
  console.log(`  • قاعدة البيانات: ${d.DB_PATH}${d.seeded ? '  [تمت التعبئة الأولى]' : ''}`);
  console.log(`  • ${routes.length} مسار HTTP مطابق لعقد backend-server`);
  console.log(`  • بث السجل الحي: ws://0.0.0.0:${PORT}${WS_PATH}?ticket=...`);
});
