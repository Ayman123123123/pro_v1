/**
 * dev-mock-api.mjs — منطق الـ API المحاكي المشترك
 * ------------------------------------------------
 * يستخدمه اثنان:
 *  1) vite.config.ts (middleware مدمج — يعمل تلقائياً حين لا يجد خادماً حقيقياً)
 *  2) dev-mock-server.mjs (خادم مستقل اختياري على 8088)
 *
 * ⚠️ للتطوير والعرض فقط — في الإنتاج الخادم الحقيقي (Spring Boot) هو المرجع.
 */
import crypto from 'node:crypto';
import http from 'node:http';
import { EventEmitter } from 'node:events';

const ADMIN_USER = process.env.RED_ADMIN_USERNAME || 'admin';
const ADMIN_PASS = process.env.RED_ADMIN_PASSWORD || 'admin123';

// ─── بيانات تجريبية ────────────────────────────────────────────────────────
const FIRST = ['أحمد','محمد','خالد','يوسف','عمر','سالم','فهد','نورة','سارة','ليلى','مريم','هند','طارق','بدر','ماجد','ريان','لينا','جنى','زياد','كرم'];
const LAST = ['العتيبي','الحربي','القحطاني','الشمري','الزهراني','المطيري','السالم','الدوسري','الغامدي','البلوي'];
const STATUSES = ['APPROVED','APPROVED','APPROVED','APPROVED','PENDING','PENDING','SUSPENDED','BANNED','REJECTED'];
const ROLES = ['USER','USER','USER','USER','MODERATOR','SUPPORT','VIEWER'];

function seedUsers(n) {
  const users = [];
  for (let i = 1; i <= n; i++) {
    const status = STATUSES[i % STATUSES.length];
    const created = new Date(Date.now() - i * 3.7e8).toISOString();
    users.push({
      id: `u-${String(i).padStart(4, '0')}`,
      redId: `RED-${100000 + i * 137}`,
      username: `user_${i}_${FIRST[i % FIRST.length]}`,
      displayName: `${FIRST[i % FIRST.length]} ${LAST[i % LAST.length]}`,
      email: `user${i}@red.local`,
      phone: `+9677${String(10000000 + i * 4231).slice(0, 8)}`,
      status,
      role: ROLES[i % ROLES.length],
      createdAt: created,
      approvedAt: status !== 'PENDING' ? created : undefined,
      lastSeen: Date.now() - i * 6e5,
      riskScore: Math.round((i * 7) % 40),
      isOnline: i % 5 === 0,
      deviceCount: (i % 3) + 1,
      sessionCount: (i % 2) + 1,
    });
  }
  return users;
}
const db = {
  users: seedUsers(57),
  flags: [
    { key: 'voice_calls_v2', description: 'محرك مكالمات v2', enabled: true, rollout: 100, updatedAt: Date.now() },
    { key: 'stories_e2ee', description: 'قصص مشفرة طرف-لطرف', enabled: false, rollout: 15, updatedAt: Date.now() },
    { key: 'communities', description: 'المجتمعات الكبيرة', enabled: true, rollout: 60, updatedAt: Date.now() },
    { key: 'ai_moderation', description: 'إشراف آلي بالذكاء الاصطناعي', enabled: true, rollout: 35, updatedAt: Date.now() },
  ],
  backups: [
    { id: 'bk-001', name: 'full-2026-09-22', type: 'FULL', sizeBytes: 4.2e9, status: 'COMPLETED', createdAt: '2026-09-22T03:00:00Z' },
    { id: 'bk-002', name: 'incr-2026-09-22', type: 'INCREMENTAL', sizeBytes: 3.1e8, status: 'COMPLETED', createdAt: '2026-09-22T15:00:00Z' },
  ],
  roles: [
    { id: 'role-admin', name: 'ADMIN', description: 'مدير النظام', permissions: ['*'], userCount: 2 },
    { id: 'role-mod', name: 'MODERATOR', description: 'مشرف محتوى', permissions: ['users.read', 'reports.manage'], userCount: 7 },
    { id: 'role-support', name: 'SUPPORT', description: 'دعم فني', permissions: ['users.read'], userCount: 4 },
  ],
};
let accessSeq = 0;

// ─── أدوات ─────────────────────────────────────────────────────────────────
function json(res, status, body) {
  const buf = Buffer.from(JSON.stringify(body));
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': buf.length });
  res.end(buf);
}
function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch { resolve({}); } });
  });
}
function pageOf(arr, url) {
  const q = new URL(url, 'http://x').searchParams;
  const page = Math.max(0, Number(q.get('page') ?? 0));
  const size = Math.min(200, Math.max(1, Number(q.get('size') ?? 20)));
  let items = arr;
  const status = q.get('status');
  const search = q.get('search')?.toLowerCase();
  if (status && status !== 'ALL') items = items.filter((u) => u.status === status);
  if (search) items = items.filter((u) => u.username.toLowerCase().includes(search) || u.displayName.includes(search) || u.redId.toLowerCase().includes(search));
  return { content: items.slice(page * size, (page + 1) * size), page, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) };
}
function adminUser() {
  return { id: 'admin-0001', redId: 'RED-000001', username: ADMIN_USER, displayName: 'يونس ماستر', role: 'ADMIN', email: 'admin@red.local', createdAt: '2026-01-01T00:00:00Z' };
}
function issueTokens() {
  accessSeq++;
  return { accessToken: `mock-access-${Date.now()}-${accessSeq}`, refreshToken: `mock-refresh-${crypto.randomBytes(8).toString('hex')}` };
}
const ARRAY_PATHS = /(reports|groups|posts|polls|hashtags|sticker-packs|events|alerts|sessions|api-keys|notifications|channels|members|devices|activity|security-events|announcements|backups|roles|recordings|viewers|reactions|gifts)$/i;

// ─── المعالج المشترك (connect-style) ──────────────────────────────────────
export function mockApiMiddleware() {
  return async function redMockApi(req, res, next) {
    const pathname = (req.url || '').split('?')[0];
    const isApi = pathname === '/api' || pathname.startsWith('/api/');
    const isHealth = pathname === '/health' || pathname === '/sfu-health';
    if (!isApi && !isHealth) return next();

    const url = new URL(req.url, 'http://local');
    // داخل vite قد يُمرَّر المسار مع أو بدون /api — نوحّد
    let p = url.pathname.replace(/^\/api\/?/, '');
    const method = req.method.toUpperCase();

    if (isHealth) return json(res, 200, { status: 'ok', mock: true });

    await new Promise((r) => setTimeout(r, 30)); // محاكاة زمن استجابة

    // SSE
    if (p === 'admin/events/stream') {
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
      res.write(': connected\n\n');
      const iv = setInterval(() => { try { res.write(': ping\n\n'); } catch { clearInterval(iv); } }, 15000);
      req.on('close', () => clearInterval(iv));
      return;
    }

    // المصادقة
    if (p === 'auth/login' && method === 'POST') {
      const { username, password } = await readBody(req);
      if (username === ADMIN_USER && password === ADMIN_PASS) {
        const t = issueTokens();
        return json(res, 200, { ...t, tokenType: 'Bearer', expiresIn: 3600, user: adminUser() });
      }
      return json(res, 401, { code: 'INVALID_CREDENTIALS', message: 'بيانات الدخول غير صحيحة' });
    }
    if (p === 'auth/refresh' && method === 'POST') {
      const { refreshToken } = await readBody(req);
      if (typeof refreshToken === 'string' && refreshToken.startsWith('mock-refresh-')) {
        const t = issueTokens();
        return json(res, 200, { accessToken: t.accessToken, refreshToken: t.refreshToken });
      }
      return json(res, 401, { code: 'INVALID_REFRESH', message: 'جلسة منتهية' });
    }
    if (p === 'auth/logout') return json(res, 200, { success: true });
    if (p === 'auth/me') {
      const auth = req.headers.authorization || '';
      return auth.startsWith('Bearer mock-access-') ? json(res, 200, adminUser()) : json(res, 401, { code: 'UNAUTHENTICATED' });
    }

    // لوحة القيادة
    if (p === 'dashboard/metrics') {
      const users = db.users;
      const count = (s) => users.filter((u) => u.status === s).length;
      return json(res, 200, {
        users: { total: users.length, approved: count('APPROVED'), pending: count('PENDING'), banned: count('BANNED'), new24h: 6, approvalRate: Math.round((count('APPROVED') / users.length) * 100) },
        messages: { total: 1284000, last24h: 18322, perSecond: 0.21 },
        calls: { total: 84210, active: 12, avgDuration: 436 },
        channels: { total: 1543, active: 388 },
        revenue: { total: 52400, last30d: 8150, currency: 'USD' },
        health: { cpu: 34, memory: 58, disk: 41, network: 22, dbConnections: 37, queueDepth: 3 },
      });
    }
    if (p === 'dashboard/charts') {
      const points = [];
      for (let i = 29; i >= 0; i--) {
        const d = new Date(Date.now() - i * 864e5);
        points.push({
          date: d.toISOString().slice(0, 10),
          users: 900 + i * 31, newUsers: 8 + (i % 7) * 4,
          messages: 21000 + i * 650, calls: 1400 + (i % 9) * 120,
          revenue: 180 + (i % 5) * 45, dau: 640 + (i % 11) * 38, mau: 3900 + i * 22,
        });
      }
      return json(res, 200, points);
    }
    if (p === 'dashboard/health') {
      return json(res, 200, [
        { id: 'api', component: 'API Gateway', status: 'HEALTHY', cpuUsage: 31, latencyMs: 12 },
        { id: 'pg', component: 'PostgreSQL', status: 'HEALTHY', cpuUsage: 22, latencyMs: 3 },
        { id: 'mongo', component: 'MongoDB', status: 'HEALTHY', cpuUsage: 27, latencyMs: 5 },
        { id: 'redis', component: 'Redis', status: 'HEALTHY', cpuUsage: 9, latencyMs: 1 },
        { id: 'sfu', component: 'Media SFU', status: 'DEGRADED', cpuUsage: 68, latencyMs: 84 },
      ]);
    }
    if (p === 'dashboard/alerts') {
      return json(res, 200, [
        { id: 'al-1', severity: 'WARNING', title: 'Media SFU', message: 'حمل مرتفع على خادم الوسائط (68% CPU)', at: Date.now() - 9e5 },
        { id: 'al-2', severity: 'INFO', title: 'نسخ احتياطي', message: 'اكتمل النسخ التزايدي بنجاح', at: Date.now() - 36e5 },
      ]);
    }
    if (p === 'dashboard/realtime' || p === 'stats/realtime') {
      return json(res, 200, { onlineUsers: 214 + Math.floor(Math.random() * 60), activeCalls: 8 + Math.floor(Math.random() * 9), messagesPerMin: 240 + Math.floor(Math.random() * 120), timestamp: Date.now() });
    }
    if (p === 'media/active-calls') return json(res, 200, { activeCalls: [], count: 0 });
    if (p === 'operations/overview') {
      return json(res, 200, { status: 'OPERATIONAL', services: 18, healthyServices: 17, degradedServices: 1, uptime: 99.97, timestamp: Date.now() });
    }

    // المستخدمون
    let m;
    if (p === 'admin/users' && method === 'GET') return json(res, 200, pageOf(db.users, req.url));
    if (p === 'admin/users/pending') return json(res, 200, pageOf(db.users.filter((u) => u.status === 'PENDING'), req.url));
    if (p === 'admin/users/action' && method === 'POST') {
      const { userId, action } = await readBody(req);
      const u = db.users.find((x) => x.id === userId);
      if (u && action) {
        const map = { APPROVE: 'APPROVED', REJECT: 'REJECTED', BAN: 'BANNED', UNBAN: 'APPROVED', SUSPEND: 'SUSPENDED' };
        if (map[action]) u.status = map[action];
      }
      return json(res, 200, { success: true });
    }
    if ((m = p.match(/^admin\/users\/([^/]+)\/(approve|reject|ban|unban)$/)) && method === 'POST') {
      const u = db.users.find((x) => x.id === m[1]);
      if (u) u.status = { approve: 'APPROVED', reject: 'REJECTED', ban: 'BANNED', unban: 'APPROVED' }[m[2]];
      return json(res, 200, { success: true });
    }
    if ((m = p.match(/^admin\/users\/([^/]+)$/)) && method === 'GET') {
      const u = db.users.find((x) => x.id === m[1]);
      return u ? json(res, 200, u) : json(res, 404, { code: 'NOT_FOUND' });
    }

    // مجموعات/منشورات/تقارير/مكالمات
    if (p === 'admin/social/groups' && method === 'GET') {
      return json(res, 200, pageOf([
        { id: 'g-1', name: 'عائلة الأمل', membersCount: 214, messagesCount: 8934, status: 'ACTIVE', createdAt: '2026-03-11T10:00:00Z' },
        { id: 'g-2', name: 'فريق التطوير', membersCount: 18, messagesCount: 41230, status: 'ACTIVE', createdAt: '2026-04-02T10:00:00Z' },
        { id: 'g-3', name: 'سوق عدن', membersCount: 1204, messagesCount: 33411, status: 'LOCKED', createdAt: '2026-05-19T10:00:00Z' },
      ], req.url));
    }
    if (p === 'admin/social/posts' && method === 'GET') {
      return json(res, 200, pageOf([
        { id: 'p-1', authorName: 'أحمد العتيبي', contentPreview: 'تحديث التطبيق الجديد متوفر الآن 🎉', likes: 842, comments: 133, status: 'PUBLISHED', createdAt: '2026-09-20T12:00:00Z' },
        { id: 'p-2', authorName: 'نورة الحربي', contentPreview: 'صورة من مؤتمر عدن التقني', likes: 421, comments: 54, status: 'PUBLISHED', createdAt: '2026-09-21T09:30:00Z' },
        { id: 'p-3', authorName: 'spammer_x', contentPreview: 'اشترك الآن...', likes: 0, comments: 2, status: 'REMOVED', createdAt: '2026-09-22T08:10:00Z' },
      ], req.url));
    }
    if (p === 'admin/moderation/reports' || p === 'admin/reports') {
      return json(res, 200, pageOf([
        { id: 'r-1', type: 'USER_REPORT', targetType: 'MESSAGE', reason: 'SPAM', status: 'PENDING', reporterName: 'سالم', createdAt: '2026-09-22T11:00:00Z' },
        { id: 'r-2', type: 'CONTENT_REPORT', targetType: 'POST', reason: 'INAPPROPRIATE', status: 'RESOLVED', reporterName: 'ليلى', createdAt: '2026-09-21T14:20:00Z' },
      ], req.url));
    }
    if (p === 'admin/calls' || p === 'admin/call-history') {
      return json(res, 200, pageOf([
        { id: 'c-1', callerName: 'يوسف', calleeName: 'سارة', type: 'VOICE', durationSec: 654, quality: 'GOOD', createdAt: '2026-09-22T10:15:00Z' },
        { id: 'c-2', callerName: 'بدر', calleeName: 'مجموعة الأمل', type: 'VIDEO', durationSec: 2410, quality: 'EXCELLENT', createdAt: '2026-09-22T13:40:00Z' },
      ], req.url));
    }

    // النظام والأمان
    if (p === 'system/feature-flags' && method === 'GET') return json(res, 200, db.flags);
    if (p === 'system/backups' && method === 'GET') return json(res, 200, db.backups);
    if (p === 'security/roles' && method === 'GET') return json(res, 200, db.roles);
    if (p === 'security/sessions' && method === 'GET') {
      return json(res, 200, [
        { id: 's-1', userId: 'admin-0001', device: 'Chrome / Linux', ip: '127.0.0.1', createdAt: Date.now() - 36e5, lastActive: Date.now(), current: true },
      ]);
    }
    if (p === 'notifications' && method === 'GET') return json(res, 200, []);

    // الافتراضي الآمن
    if (method === 'GET') {
      if (ARRAY_PATHS.test(p)) return json(res, 200, []);
      return json(res, 200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, success: true });
    }
    return json(res, 200, { success: true });
  };
}

// ─── مسبار: هل يوجد خادم حقيقي على الهدف؟ ─────────────────────────────────
export function probeRealApi(target) {
  return new Promise((resolve) => {
    const req = httpGetProbe(`${target}/health`);
    req.on('response', () => resolve(true));
    req.on('error', () => resolve(false));
  });
}
function httpGetProbe(url) {
  return httpGet(url, 700);
}
function httpGet(url, timeoutMs) {
  try {
    const req = http.get(url, { timeout: timeoutMs }, (res) => { res.resume(); });
    req.setTimeout(timeoutMs, () => req.destroy(new Error('probe-timeout')));
    return req;
  } catch {
    const dummy = new EventEmitter();
    process.nextTick(() => dummy.emit('error', new Error('bad-url')));
    return dummy;
  }
}
