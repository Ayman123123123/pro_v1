#!/usr/bin/env node
/**
 * فحص تنفيذي: كل زر في اللوحة يُحدث قاعدة البيانات فعليًا.
 *
 * لا يكفي أن يرد الخادم {success:true} — هذا بالضبط سبب العطل الذي ظهر في
 * "الموافقات المعلقة": نجاح ظاهري بلا تغيير حالة، فيبقى الصف في القائمة.
 * لذلك كل حالة هنا تتبع النمط: نفّذ الإجراء ← أعد الجلب ← تحقق أن الحالة تغيّرت.
 *
 * التشغيل:
 *   npm run dev:server        (طرفية أولى)
 *   npm run check:server      (طرفية ثانية)
 */
const BASE = process.env.RED_API_TARGET || 'http://127.0.0.1:8080';

let pass = 0;
let fail = 0;

/**
 * رمز المسؤول — يُملأ مرة واحدة قبل الفحوص.
 *
 * كان هذا السكربت يستدعي مسارات `/api/admin/**` **بلا أي ترويسة
 * تفويض**، وكان يمرّ لأن خادم التطوير لم يكن يفرض دور ADMIN إطلاقًا
 * بخلاف `SecurityConfig`. أي أن الفحوص كانت تثبّت السلوك الخاطئ.
 * بعد إضافة حارس الدور صار التفويض لازمًا هنا كما في الإنتاج.
 */
let adminToken = null;

async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (adminToken) headers.Authorization = `Bearer ${adminToken}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status, data };
}

async function check(name, fn) {
  try {
    const detail = await fn();
    console.log(`✅ ${name}${detail ? ` — ${detail}` : ''}`);
    pass++;
  } catch (e) {
    console.log(`❌ ${name} — ${e.message}`);
    fail++;
  }
}

const assert = (cond, msg) => { if (!cond) throw new Error(msg); };

// ═══ 0. تغطية ثابتة: كل مسار تستدعيه الواجهة له معالج في خادم التطوير ═══
{
  const { readFileSync, readdirSync, statSync } = await import('node:fs');
  const root = new URL('../', import.meta.url).pathname;
  const files = [];
  (function walk(dir) {
    for (const f of readdirSync(dir)) {
      const p = `${dir}/${f}`;
      if (statSync(p).isDirectory()) walk(p);
      else if (/\.(ts|tsx)$/.test(p)) files.push(p);
    }
  })(`${root}src`);

  const called = new Set();
  for (const file of files) {
    const text = readFileSync(file, 'utf8');
    for (const m of text.matchAll(/(?:apiFetch|fetch)\(\s*[`'"](\/[^`'"]*)/g)) {
      called.add(m[1].split('?')[0].replace(/\$\{[^}]+\}/g, 'X'));
    }
  }

  // المعالجات موزّعة على ملفين: مسارات اللوحة ومسارات التطبيق.
  const server = ['dev-server/server.cjs', 'dev-server/app-routes.cjs']
    .map((f) => readFileSync(root + f, 'utf8')).join('\n');
  const routes = [...server.matchAll(/^\s*on\('(\w+)',\s*'([^']+)'/gm)].map((m) => m[2]);
  const seg = (r) => r.split('/').filter(Boolean).map((s) => (s.startsWith(':') ? 'X' : s));

  const missing = [...called].sort().filter((p) => {
    const u = p.split('/').filter(Boolean);
    return !routes.some((r) => {
      const n = seg(r);
      return n.length === u.length && n.every((s, i) => s === 'X' || u[i] === 'X' || s === u[i]);
    });
  });

  if (missing.length) {
    console.log(`❌ مسارات تستدعيها الواجهة بلا معالج:\n   ${missing.join('\n   ')}`);
    fail++;
  } else {
    console.log(`✅ تغطية المسارات — ${called.size} مسار تستدعيه الواجهة، كلها مغطاة بـ ${routes.length} معالجًا`);
    pass++;
  }
}

// ── جاهزية الخادم ──
try {
  const h = await api('GET', '/health');
  assert(h.status === 200, `الخادم لا يستجيب على ${BASE}`);
} catch {
  console.error(`\n⚠️  خادم التطوير غير مشغّل على ${BASE}\n   شغّله أولًا: npm run dev:server\n`);
  process.exit(1);
}

console.log(`\n🔍 فحص خادم التطوير على ${BASE}\n`);

// ── تفويض المسؤول قبل أي فحص ──
// خادم التطوير صار يفرض دور ADMIN على `/api/admin/**` مطابقةً
// لـ SecurityConfig، فبلا رمز تعود كل هذه الفحوص بـ401.
const ADMIN_USERNAME = process.env.RED_DEV_ADMIN_USERNAME || 'younes_sovereign';
const ADMIN_PASSWORD = process.env.RED_DEV_ADMIN_PASSWORD || 'SovereignAdmin1';

{
  let data = {};
  for (const username of [ADMIN_USERNAME, 'younes_sovereign', 'red_admin']) {
    const res = await fetch(`${BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password: ADMIN_PASSWORD }),
    });
    data = await res.json().catch(() => ({}));
    if (data.accessToken && data.user?.role === 'ADMIN') break;
  }
  if (!data.accessToken) {
    console.error('❌ تعذّر تسجيل دخول المسؤول — هل خادم التطوير يعمل؟ استخدم SovereignAdmin1');
    process.exit(1);
  }
  adminToken = data.accessToken;
}

await check('الصحة تعرض YOUNES وFlyway وخدمات القاعدة', async () => {
  const h = await api('GET', '/health');
  assert(h.status === 200, `HTTP ${h.status}`);
  assert(h.data.brand === 'YOUNES', `brand=${h.data.brand}`);
  assert(h.data.status === 'UP' || h.data.status === 'DEGRADED', `status=${h.data.status}`);
  assert(h.data.services?.redis?.status === 'UP', 'redis ليس UP');
  assert(h.data.services?.minio?.status === 'UP', 'minio ليس UP');
  assert(h.data.flyway?.appliedCount >= 1, `flyway.appliedCount=${h.data.flyway?.appliedCount}`);
  return `V${h.data.flyway.latestVersion} · ${h.data.flyway.appliedCount} ترحيل`;
});

await check('سلطة الهوية ECDSA_P256_SHA256 v1', async () => {
  const r = await api('GET', '/api/identity/authority');
  assert(r.status === 200, `HTTP ${r.status}`);
  assert(r.data.algorithm === 'ECDSA_P256_SHA256' || r.data.algorithm === 'SHA256withECDSA', r.data.algorithm);
  assert(r.data.publicKey, 'لا يوجد publicKey');
  return `${r.data.algorithm} · ${r.data.version || 'v1'}`;
});

await check('جرد العمليات يعيد أقسام المستخدمين والإشراف', async () => {
  const r = await api('GET', '/api/admin/operations/overview');
  assert(r.status === 200, `HTTP ${r.status} ${JSON.stringify(r.data)}`);
  assert(r.data.users && typeof r.data.users.total === 'number', 'users.total مفقود');
  assert(r.data.moderation && typeof r.data.moderation.openReports === 'number', 'moderation مفقود');
  return `${r.data.users.total} مستخدم · ${r.data.moderation.openReports} بلاغ`;
});

// ═══ 1. الموافقات: العطل المُبلَّغ عنه ═══
console.log('── التسجيل ثم الموافقة (الدورة الكاملة) ──');

/** يسجّل مستخدمًا جديدًا فيصير معلقًا — كي لا يعتمد الفحص على بيانات أولية قابلة للنفاد. */
let fixtureSeq = 0;
async function registerPending() {
  fixtureSeq++;
  const username = `qa_probe_${Date.now().toString(36)}_${fixtureSeq}`;
  const res = await api('POST', '/api/auth/register', {
    username,
    password: 'Qa!Probe#2026$secure',
    displayName: `حساب فحص ${fixtureSeq}`,
    device: { deviceName: 'جهاز فحص', platform: 'ANDROID', identityKey: username },
  });
  assert(res.status === 200, `فشل التسجيل: HTTP ${res.status} ${JSON.stringify(res.data)}`);
  assert(res.data.status === 'PENDING', `الحالة بعد التسجيل ${res.data.status}`);
  return res.data.user;
}

await check('التسجيل بلا هاتف/بريد/OTP ينشئ حسابًا معلقًا بجهاز معلق', async () => {
  const user = await registerPending();
  assert(user.status === 'PENDING', 'الحساب ليس معلقًا');
  assert(user.devices.length === 1 && user.devices[0].status === 'PENDING', 'الجهاز ليس معلقًا');
  assert(!user.devices[0].authorizationCertificate, 'صدرت شهادة قبل الموافقة');
  const pending = (await api('GET', '/api/admin/users/pending')).data;
  assert(pending.some((u) => u.id === user.id), 'لم يظهر في قائمة الموافقات');
  return `${user.redId} ظهر في الموافقات المعلقة`;
});

await check('التسجيل يرفض كلمة مرور ضعيفة واسمًا مكررًا', async () => {
  const weak = await api('POST', '/api/auth/register', {
    username: 'qa_weak_probe', password: 'short', displayName: 'ضعيف',
    device: { deviceName: 'x', platform: 'ANDROID' },
  });
  assert(weak.status === 400, `كلمة المرور القصيرة قُبلت (HTTP ${weak.status})`);
  const existing = (await api('GET', '/api/admin/users?size=1')).data.content[0];
  const dup = await api('POST', '/api/auth/register', {
    username: existing.username, password: 'Qa!Probe#2026$secure', displayName: 'مكرر',
    device: { deviceName: 'x', platform: 'ANDROID' },
  });
  assert(dup.status === 400, `الاسم المكرر قُبل (HTTP ${dup.status})`);
  return 'كلاهما مرفوض بـ 400';
});

console.log('\n── الموافقات المعلقة ──');

await check('الموافقة تزيل المستخدم من القائمة المعلقة فعليًا', async () => {
  const target = await registerPending();
  const before = (await api('GET', '/api/admin/users/pending')).data;
  assert(before.some((u) => u.id === target.id), 'المستخدم غير موجود في القائمة');
  const res = await api('POST', '/api/admin/users/action', { userId: target.id, action: 'APPROVED', reason: null });
  assert(res.status === 200, `HTTP ${res.status}`);
  assert(res.data.status === 'APPROVED', `الحالة ${res.data.status}`);
  const after = (await api('GET', '/api/admin/users/pending')).data;
  assert(!after.some((u) => u.id === target.id), 'المستخدم ما زال في القائمة المعلقة بعد الموافقة');
  return `${target.redId} اختفى (${before.length} ← ${after.length})`;
});

await check('الموافقة تُصدر شهادة تفويض موقّعة وصالحة تشفيريًا', async () => {
  const target = await registerPending();
  const user = (await api('POST', '/api/admin/users/action',
    { userId: target.id, action: 'APPROVED', reason: null })).data;
  const device = user.devices.find((dv) => dv.authorizationCertificate);
  assert(device, 'لم تصدر شهادة بعد الموافقة');
  assert(device.status === 'APPROVED', `حالة الجهاز ${device.status}`);
  const v = (await api('GET', `/api/admin/security/device-certificate/${device.id}`)).data;
  assert(v.valid === true, 'فشل التحقق من التوقيع');
  assert(v.expired === false, 'الشهادة منتهية');
  assert(v.redId === user.redId, 'RED ID داخل الشهادة لا يطابق المستخدم');
  return `${v.redId} صالحة حتى ${String(v.expiresAt).slice(0, 10)}`;
});

await check('الرفض يلغي كل الأجهزة ويشذّب سبب الرفض', async () => {
  const target = await registerPending();
  const res = await api('POST', '/api/admin/users/action',
    { userId: target.id, action: 'REJECTED', reason: '  بيانات غير مكتملة  ' });
  assert(res.data.status === 'REJECTED', 'الحالة لم تتغير');
  assert(res.data.rejectionReason === 'بيانات غير مكتملة', 'السبب لم يُشذَّب');
  assert(res.data.devices.every((dv) => dv.status === 'REVOKED'), 'بقيت أجهزة غير ملغاة');
  return `${res.data.redId} — ${res.data.devices.length} جهاز ملغى`;
});

await check('حساب المسؤول محمي من الحظر عبر هذا المسار', async () => {
  const admin = (await api('GET', '/api/admin/users?role=ADMIN')).data.content[0];
  const res = await api('POST', '/api/admin/users/action', { userId: admin.id, action: 'BANNED' });
  assert(res.status === 400, `تم قبول حظر المسؤول (HTTP ${res.status})`);
  return 'مرفوض بـ 400';
});

await check('الإجراء غير المدعوم يُرفض', async () => {
  const admin = (await api('GET', '/api/admin/users?role=ADMIN')).data.content[0];
  const res = await api('POST', '/api/admin/users/action', { userId: admin.id, action: 'DELETED' });
  assert(res.status === 400, `HTTP ${res.status}`);
  return 'مرفوض بـ 400';
});

await check('كل إجراء موافقة يُسجَّل في سجل التدقيق', async () => {
  const logs = (await api('GET', '/api/admin/audit?size=50')).data.content;
  const acct = logs.filter((l) => l.action.startsWith('ACCOUNT_'));
  assert(acct.length >= 3, `عدد السجلات ${acct.length}`);
  return `${acct.length} سجل ACCOUNT_*`;
});

// ═══ 2. بقية الأزرار الكاتبة ═══
console.log('\n── الإعلانات ──');
let announcementId = null;

await check('إنشاء إعلان يظهر في القائمة', async () => {
  const before = (await api('GET', '/api/admin/announcements')).data.length;
  const res = await api('POST', '/api/admin/announcements',
    { title: 'اختبار آلي', body: 'محتوى', type: 'INFO', targetAudience: 'ALL', priority: 1 });
  announcementId = res.data.id;
  assert(announcementId, 'لا يوجد معرّف');
  const after = (await api('GET', '/api/admin/announcements')).data;
  assert(after.length === before + 1, `العدد ${before} ← ${after.length}`);
  assert(after.some((a) => a.id === announcementId), 'الإعلان الجديد غير موجود');
  return `${before} ← ${after.length}`;
});

await check('النشر يحوّل isPublished إلى true ويثبت', async () => {
  await api('POST', `/api/admin/announcements/${announcementId}/publish`);
  const item = (await api('GET', '/api/admin/announcements')).data.find((a) => a.id === announcementId);
  assert(item.isPublished === true, 'ما زال غير منشور');
  assert(item.publishedAt, 'publishedAt فارغ');
  return 'منشور';
});

await check('الحذف يزيل الإعلان فعليًا', async () => {
  await api('DELETE', `/api/admin/announcements/${announcementId}`);
  const after = (await api('GET', '/api/admin/announcements')).data;
  assert(!after.some((a) => a.id === announcementId), 'الإعلان ما زال موجودًا');
  return 'حُذف';
});

console.log('\n── أعلام الميزات ──');
await check('تبديل العلم يُحفظ ويُقرأ من القاعدة', async () => {
  const flag = (await api('GET', '/api/admin/feature-flags')).data[0];
  const target = !flag.enabled;
  await api('PUT', `/api/admin/feature-flags/${flag.flagName}`, { enabled: target, rolloutPercentage: 55 });
  const after = (await api('GET', '/api/admin/feature-flags')).data.find((f) => f.flagName === flag.flagName);
  assert(after.enabled === target, 'القيمة لم تتغير');
  assert(after.rolloutPercentage === 55, `النسبة ${after.rolloutPercentage}`);
  await api('PUT', `/api/admin/feature-flags/${flag.flagName}`,
    { enabled: flag.enabled, rolloutPercentage: flag.rolloutPercentage });
  return `${flag.flagName}: ${flag.enabled} ← ${target}`;
});

console.log('\n── البلاغات ──');
await check('بلاغ مستخدم جديد يصل إلى قائمة الإشراف', async () => {
  const before = (await api('GET', '/api/admin/reports?status=PENDING')).data.totalElements;
  const res = await api('POST', '/api/reports', { category: 'SPAM', reason: 'بلاغ فحص آلي' });
  assert(res.data.status === 'PENDING', `الحالة ${res.data.status}`);
  const after = (await api('GET', '/api/admin/reports?status=PENDING')).data;
  assert(after.totalElements === before + 1, `العدد ${before} ← ${after.totalElements}`);
  return `${before} ← ${after.totalElements} معلق`;
});

await check('حل البلاغ ينقله خارج قائمة المعلقة', async () => {
  const target = (await api('POST', '/api/reports',
    { category: 'ABUSE', reason: 'بلاغ فحص للحل' })).data;
  const pending = (await api('GET', '/api/admin/reports?status=PENDING')).data.content;
  assert(pending.some((r) => r.id === target.id), 'البلاغ الجديد غير معلق');
  const res = await api('POST', `/api/admin/reports/${target.id}/resolve`,
    { resolution: 'تم اتخاذ إجراء', notes: 'اختبار آلي' });
  assert(res.data.status === 'RESOLVED', `الحالة ${res.data.status}`);
  assert(res.data.resolvedAt, 'resolvedAt فارغ');
  const after = (await api('GET', '/api/admin/reports?status=PENDING')).data.content;
  assert(!after.some((r) => r.id === target.id), 'البلاغ ما زال معلقًا');
  return `${pending.length} ← ${after.length} معلق`;
});

console.log('\n── النسخ الاحتياطية ──');
await check('إنشاء نسخة يضيف صفًا بحالة IN_PROGRESS', async () => {
  const before = (await api('GET', '/api/admin/backups')).data.totalElements;
  const res = await api('POST', '/api/admin/backups', { type: 'INCREMENTAL', notes: 'اختبار آلي' });
  assert(res.data.status === 'IN_PROGRESS', `الحالة ${res.data.status}`);
  const after = (await api('GET', '/api/admin/backups')).data;
  assert(after.totalElements === before + 1, 'العدد لم يزد');
  await api('DELETE', `/api/admin/backups/${res.data.id}`);
  const cleaned = (await api('GET', '/api/admin/backups')).data.totalElements;
  assert(cleaned === before, 'الحذف لم ينفّذ');
  return `${before} ← ${before + 1} ← ${cleaned}`;
});

console.log('\n── المحتوى ──');
await check('إنشاء استطلاع ثم إغلاقه يغيّر الحالة', async () => {
  const res = await api('POST', '/api/admin/content/polls',
    { question: 'سؤال اختبار؟', options: ['نعم', 'لا'], pollType: 'SINGLE_CHOICE' });
  assert(res.data.status === 'ACTIVE', 'ليس نشطًا');
  assert(res.data.options.length === 2, 'الخيارات ناقصة');
  const active = (await api('GET', '/api/admin/content/polls/active')).data;
  assert(active.some((p) => p.id === res.data.id), 'غير موجود في النشطة');
  const closed = await api('POST', `/api/admin/content/polls/${res.data.id}/close`);
  assert(closed.data.status === 'CLOSED', 'لم يُغلق');
  const stillActive = (await api('GET', '/api/admin/content/polls/active')).data;
  assert(!stillActive.some((p) => p.id === res.data.id), 'ما زال في النشطة');
  await api('DELETE', `/api/admin/content/polls/${res.data.id}`);
  return 'ACTIVE ← CLOSED ← محذوف';
});

await check('إنشاء حدث ثم إلغاؤه يغيّر الحالة', async () => {
  const res = await api('POST', '/api/admin/content/events',
    { title: 'حدث اختبار', description: '—', eventType: 'MEETING', startsAt: new Date().toISOString() });
  assert(res.data.status === 'SCHEDULED', `الحالة ${res.data.status}`);
  const cancelled = await api('POST', `/api/admin/content/events/${res.data.id}/cancel`);
  assert(cancelled.data.status === 'CANCELLED', 'لم يُلغَ');
  await api('DELETE', `/api/admin/content/events/${res.data.id}`);
  return 'SCHEDULED ← CANCELLED ← محذوف';
});

await check('حظر الهاشتاق يُحفظ ثم يُرفع', async () => {
  const tag = (await api('GET', '/api/admin/content/hashtags/trending')).data[0];
  const blocked = await api('POST', `/api/admin/content/hashtags/${tag.id}/block`);
  assert(blocked.data.isBlocked === true, 'لم يُحظر');
  const unblocked = await api('POST', `/api/admin/content/hashtags/${tag.id}/unblock`);
  assert(unblocked.data.isBlocked === false, 'لم يُرفع الحظر');
  return `${tag.tag}`;
});

console.log('\n── الأمان والجلسات ──');
await check('صلاحية PSTN تُحفظ على المستخدم', async () => {
  const user = (await api('GET', '/api/admin/users?status=APPROVED&size=10')).data.content
    .find((u) => u.role !== 'ADMIN');
  await api('PUT', '/api/admin/users/pstn', { userId: user.id, enabled: !user.pstnEnabled, dailyLimit: 42 });
  const after = (await api('GET', `/api/admin/users/${user.id}`)).data;
  assert(after.pstnEnabled === !user.pstnEnabled, 'لم تتغير');
  assert(after.pstnDailyLimit === 42, `الحد ${after.pstnDailyLimit}`);
  return `${user.redId} → ${after.pstnEnabled ? 'مفعّل' : 'موقوف'} / 42`;
});

await check('المسح عن بُعد يلغي كل أجهزة المستخدم', async () => {
  const user = (await api('GET', '/api/admin/users?status=APPROVED&size=10')).data.content
    .find((u) => u.role !== 'ADMIN' && u.devices?.some((dv) => dv.status !== 'REVOKED'));
  assert(user, 'لا يوجد مستخدم بأجهزة فعّالة');
  await api('POST', `/api/admin/users/${user.id}/remote-app-wipe`);
  const after = (await api('GET', `/api/admin/users/${user.id}`)).data;
  assert(after.devices.every((dv) => dv.status === 'REVOKED'), 'بقيت أجهزة فعّالة');
  return `${user.redId} — ${after.devices.length} جهاز`;
});

await check('الدخول يفتح جلسة إدارية حقيقية', async () => {
  const before = (await api('GET', '/api/admin/sessions')).data.length;
  const login = await api('POST', '/api/auth/login', { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
  assert(login.data.user.role === 'ADMIN', 'الدور ليس ADMIN');
  const after = (await api('GET', '/api/admin/sessions')).data;
  assert(after.length === before + 1, `الجلسات ${before} ← ${after.length}`);
  return `${before} ← ${after.length} جلسة`;
});

await check('إنهاء الجلسة يزيلها من القائمة', async () => {
  await api('POST', '/api/auth/login', { username: ADMIN_USERNAME, password: ADMIN_PASSWORD });
  const sessions = (await api('GET', '/api/admin/sessions')).data;
  assert(sessions.length > 0, 'لا توجد جلسات');
  const target = sessions[0];
  await api('POST', `/api/admin/sessions/${target.id}/terminate`, { reason: 'اختبار آلي' });
  const after = (await api('GET', '/api/admin/sessions')).data;
  assert(!after.some((s) => s.id === target.id), 'الجلسة ما زالت موجودة');
  return `${sessions.length} ← ${after.length}`;
});

console.log('\n── الإشعارات ──');
await check('تعليم الكل كمقروء يصفّر العداد', async () => {
  await api('PUT', '/api/notifications/read-all');
  const { count } = (await api('GET', '/api/notifications/unread-count')).data;
  assert(count === 0, `العداد ${count}`);
  return 'العداد = 0';
});

console.log('\n── ثبات البيانات ──');
await check('التغييرات مكتوبة على القرص (SQLite) لا في الذاكرة', async () => {
  const { readFileSync, existsSync } = await import('node:fs');
  const path = process.env.RED_DEV_DB
    || new URL('../dev-server/data/red-dev.sqlite', import.meta.url).pathname;
  assert(existsSync(path), `ملف القاعدة غير موجود: ${path}`);
  const header = readFileSync(path).subarray(0, 15).toString('utf8');
  assert(header === 'SQLite format 3', `ترويسة غير متوقعة: ${header}`);
  return 'ملف SQLite صالح على القرص';
});

// ── الخلاصة ──
console.log(`\n${'─'.repeat(56)}`);
if (fail === 0) {
  console.log(`🎉 نجحت كل الفحوص: ${pass}/${pass} — كل إجراء يُغيّر الحالة فعليًا`);
  process.exit(0);
} else {
  console.log(`⚠️  ${pass} نجح · ${fail} فشل`);
  process.exit(1);
}
