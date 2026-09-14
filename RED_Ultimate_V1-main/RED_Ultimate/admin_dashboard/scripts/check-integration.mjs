#!/usr/bin/env node
/**
 * فحص الربط الثلاثي: تطبيق الهاتف ↔ الخادم ↔ قاعدة البيانات ↔ لوحة الإدارة.
 *
 * الفكرة: لا يكفي أن يعمل كل طرف وحده. الفحص يتتبّع مستخدمًا واحدًا عبر
 * دورة حياته كاملة — يسجّل من التطبيق، يظهر في اللوحة، يعتمده المسؤول،
 * يدخل من التطبيق، يصبح قابلًا للاكتشاف، يكوّن جهات اتصال وينشر، ثم يُحظر
 * من اللوحة فينقطع فورًا. كل خطوة تُقرأ من الطرف الآخر لا من ردّ الكتابة.
 *
 * يغطّي أيضًا العطل المُبلَّغ عنه: «التطبيق لا يوجد فيه RED ID».
 *
 * التشغيل:
 *   npm run dev:server        (طرفية أولى)
 *   npm run check:integration (طرفية ثانية)
 */
const BASE = process.env.RED_API_TARGET || 'http://127.0.0.1:8080';

let pass = 0;
let fail = 0;

/**
 * @param token رمز صريح. عند إغفاله على مسار إداري يُستعمل رمز
 *   المسؤول تلقائيًا.
 *
 * خادم التطوير صار يفرض دور ADMIN على `/api/admin/**` مطابقةً
 * لـ SecurityConfig. وكثير من الفحوص هنا كانت تستدعي مسارات إدارية
 * بلا رمز لأن الخادم كان يقبلها — أي أنها كانت تثبّت السلوك الخاطئ.
 *
 * الفحوص التي تختبر الرفض تمرّر `null` صراحةً لتتجاوز هذا الافتراض.
 */
async function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  const effective = token === undefined && path.startsWith('/api/admin/')
    ? adminToken
    : token;
  if (effective) headers.Authorization = `Bearer ${effective}`;
  const res = await fetch(BASE + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
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

try {
  assert((await api('GET', '/health')).status === 200, 'down');
} catch {
  console.error(`\n⚠️  خادم التطوير غير مشغّل على ${BASE}\n   شغّله أولًا: npm run dev:server\n`);
  process.exit(1);
}

console.log(`\n🔗 فحص الربط الثلاثي على ${BASE}\n`);

// ═══ 0. تغطية ثابتة: كل مسار يستدعيه تطبيق الهاتف له معالج ═══
await check('كل مسار يستدعيه red-app مغطى في خادم التطوير', async () => {
  const { readFileSync, readdirSync, statSync, existsSync } = await import('node:fs');
  const root = new URL('../', import.meta.url).pathname;
  const appSrc = `${root}../red-app/src/main`;
  if (!existsSync(appSrc)) return 'تُخطّي — مصدر التطبيق غير موجود';

  const files = [];
  (function walk(dir) {
    for (const f of readdirSync(dir)) {
      const p = `${dir}/${f}`;
      if (statSync(p).isDirectory()) walk(p);
      else if (p.endsWith('.kt')) files.push(p);
    }
  })(appSrc);

  const called = new Set();
  for (const file of files) {
    for (const m of readFileSync(file, 'utf8').matchAll(/"(\/api\/[^"]*)"/g)) {
      // `$qs` و`${...}` تعويضات Kotlin: الأولى سلسلة استعلام والثانية معرّف
      let p = m[1].split('?')[0].replace(/\$\{[^}]*\}/g, 'X').replace(/\$qs\b/g, '').replace(/\$\w+/g, 'X');
      if (p.endsWith('/')) p = p.slice(0, -1);
      called.add(p);
    }
  }

  const handlers = ['dev-server/server.cjs', 'dev-server/app-routes.cjs']
    .map((f) => readFileSync(root + f, 'utf8')).join('\n');
  const routes = [...handlers.matchAll(/^\s*on\('(\w+)',\s*'([^']+)'/gm)].map((m) => m[2]);
  const seg = (r) => r.split('/').filter(Boolean).map((s) => (s.startsWith(':') ? 'X' : s));

  const missing = [...called].sort().filter((p) => {
    const u = p.split('/').filter(Boolean);
    return !routes.some((r) => {
      const n = seg(r);
      return n.length === u.length && n.every((s, i) => s === 'X' || u[i] === 'X' || s === u[i]);
    });
  });
  assert(missing.length === 0, `بلا معالج:\n   ${missing.join('\n   ')}`);
  return `${called.size} مسارًا يستدعيه التطبيق — كلها مغطاة`;
});

const PASSWORD = process.env.RED_DEV_ADMIN_PASSWORD || 'SovereignAdmin1';
const username = `qa_link_${Date.now().toString(36)}`;
let redId = null;
let userId = null;
let appToken = null;
let adminToken = null;

// ═══ 1. عقد الهوية الذي كان مكسورًا ═══
console.log('── هوية RED في التطبيق (العطل المُبلَّغ عنه) ──');

await check('الدخول يعيد UserResponse كاملًا بحقل redId', async () => {
  const res = await api('POST', '/api/auth/login', { username: 'younes_sovereign', password: PASSWORD });
  assert(res.status === 200, `HTTP ${res.status}`);
  const u = res.data.user;
  // red-app/auth/AuthModels.kt يعرّف هذه الحقول، وredId غير قابل للإغفال
  for (const field of ['id', 'redId', 'username', 'displayName', 'status', 'role', 'devices']) {
    assert(field in u, `الحقل ${field} مفقود — سيفشل فك الترميز في التطبيق`);
  }
  assert(/^[1-9][0-9]{4}$/.test(u.redId), `صيغة معرّف يونس غير صحيحة: ${u.redId}`);
  assert(res.data.accessToken, 'لا يوجد رمز وصول');
  adminToken = res.data.accessToken;
  return `${u.redId} — ${Object.keys(u).length} حقلًا`;
});

await check('كل معرّفات RED تطابق نمط التطبيق (RedIdGenerator)', async () => {
  // النمط نفسه في red-app/ui/RedDashboard.kt و features/contacts/QrScannerSheet.kt.
  // أي معرّف خارجه يجعل أزرار الاتصال والإضافة معطّلة في التطبيق بلا سبب ظاهر.
  const pattern = /^[1-9][0-9]{4}$/;
  const users = (await api('GET', '/api/admin/users?size=100')).data.content;
  const invalid = users.filter((u) => !pattern.test(u.redId));
  assert(invalid.length === 0, `معرّفات مرفوضة من التطبيق: ${invalid.map((u) => u.redId).join(', ')}`);
  return `${users.length} معرّفًا — كلها خمسة أرقام`;
});

await check('‎/api/me يعيد هوية صاحب الرمز نفسه لا حسابًا آخر', async () => {
  const me = await api('GET', '/api/me', undefined, adminToken);
  assert(me.status === 200, `HTTP ${me.status}`);
  assert(me.data.redId === '10001', `أعاد ${me.data.redId}`);
  return `${me.data.redId} ${me.data.displayName}`;
});

await check('الطلب بلا رمز وصول يُرفض بـ 401', async () => {
  const res = await api('GET', '/api/me');
  assert(res.status === 401, `HTTP ${res.status}`);
  return 'مرفوض';
});

// ═══ 2. الدورة الكاملة ═══
console.log('\n── دورة الحياة: تطبيق → لوحة → تطبيق ──');

await check('١ التطبيق يسجّل حسابًا بلا هاتف/بريد/OTP', async () => {
  const res = await api('POST', '/api/auth/register', {
    username, password: PASSWORD, displayName: 'حساب ربط آلي',
    device: {
      deviceName: 'Galaxy S24', platform: 'ANDROID',
      identityKey: 'BASE64_PUBLIC_IDENTITY_KEY', registrationId: 4211, protocolDeviceId: 1,
      signedPreKeyId: 7, signedPreKey: 'SPK', signedPreKeySignature: 'SIG',
      kyberPreKeyId: 9, kyberPreKey: 'KPK', kyberPreKeySignature: 'KSIG',
    },
  });
  assert(res.status === 200, `HTTP ${res.status}`);
  assert(res.data.status === 'PENDING', `الحالة ${res.data.status}`);
  assert(res.data.message === 'ACCOUNT_PENDING_ADMIN_APPROVAL', 'رسالة غير متوقعة');
  redId = res.data.user.redId;
  userId = res.data.user.id;
  assert(res.data.user.devices[0].status === 'PENDING', 'الجهاز ليس معلقًا');
  return `${redId} — الحساب والجهاز معلقان`;
});

await check('٢ الدخول ممنوع قبل موافقة المسؤول', async () => {
  const res = await api('POST', '/api/auth/login', { username, password: PASSWORD });
  assert(res.data.status === 'PENDING', `الحالة ${res.data.status}`);
  assert(!res.data.accessToken, 'مُنح رمز وصول لحساب معلق');
  return res.data.message;
});

await check('٣ يظهر فورًا في صفحة الموافقات باللوحة', async () => {
  const pending = (await api('GET', '/api/admin/users/pending')).data;
  assert(pending.some((u) => u.id === userId), 'غير موجود في قائمة الموافقات');
  return `${redId} ظاهر للمسؤول`;
});

await check('٤ موافقة المسؤول تُصدر شهادة جهاز موقّعة', async () => {
  const res = await api('POST', '/api/admin/users/action', { userId, action: 'APPROVED', reason: null });
  assert(res.data.status === 'APPROVED', `الحالة ${res.data.status}`);
  const device = res.data.devices[0];
  assert(device.status === 'APPROVED', `الجهاز ${device.status}`);
  assert(device.authorizationCertificate, 'لم تصدر شهادة');
  const v = (await api('GET', `/api/admin/security/device-certificate/${device.id}`)).data;
  assert(v.valid === true, 'توقيع الشهادة غير صالح');
  assert(v.redId === redId, 'RED ID داخل الشهادة لا يطابق');
  return `شهادة صالحة لـ ${v.redId}`;
});

await check('٥ المستخدم نفسه يدخل الآن من التطبيق ويحصل على RED ID', async () => {
  const res = await api('POST', '/api/auth/login', { username, password: PASSWORD });
  assert(res.data.status === 'APPROVED', `الحالة ${res.data.status}`);
  assert(res.data.accessToken, 'لم يُمنح رمز وصول');
  assert(res.data.user.redId === redId, `RED ID تغيّر: ${res.data.user.redId}`);
  appToken = res.data.accessToken;
  return `${redId} دخل بنجاح`;
});

await check('٦ صار قابلًا للاكتشاف في دليل RED لمستخدم آخر', async () => {
  const byId = (await api('GET', `/api/directory/search?query=${redId}`, undefined, adminToken)).data;
  assert(byId.length === 1 && byId[0].redId === redId, 'لم يُعثر عليه بمعرّف RED');
  // الدليل العام يكشف الحقول الثلاثة فقط
  assert(Object.keys(byId[0]).sort().join() === 'displayName,redId,username', `حقول زائدة: ${Object.keys(byId[0])}`);
  return `مكتشَف بـ ${redId} — 3 حقول عامة فقط`;
});

await check('٧ حزمة مفاتيحه العامة متاحة للتشفير الطرفي', async () => {
  // الدليل صار يتطلب مصادقة (كان مكشوفًا): نمرّر رمز مستخدم آخر
  // لأن هذا هو السيناريو الواقعي — طرف يريد مراسلة صاحب المعرّف.
  const dir = (await api('GET', `/api/identity/directory/${redId}`, undefined, adminToken)).data;
  assert(dir.redId === redId, 'redId غير مطابق');
  assert(dir.devices.length === 1, `عدد الأجهزة ${dir.devices.length}`);
  const dev = dir.devices[0];
  assert(dev.identityKey === 'BASE64_PUBLIC_IDENTITY_KEY', 'المفتاح العام غير محفوظ');
  assert(dev.authorizationCertificate, 'بلا شهادة تفويض');
  // لا يجوز أن تحتوي الاستجابة أي مفتاح خاص
  assert(!JSON.stringify(dir).toLowerCase().includes('private'), 'تسريب مفتاح خاص!');
  return `${dir.devices.length} جهاز معتمد — مفاتيح عامة فقط`;
});

await check('٨ طلب صداقة → قبول → جهة اتصال متبادلة', async () => {
  const sent = await api('POST', `/api/contacts/requests/${redId}`, undefined, adminToken);
  assert(sent.status === 200, `فشل الإرسال HTTP ${sent.status}`);
  const inbox = (await api('GET', '/api/contacts/requests', undefined, appToken)).data;
  assert(inbox.length === 1, `الوارد ${inbox.length}`);
  assert(inbox[0].requester.redId === '10001', 'المُرسِل غير صحيح');
  // معرّف الطلب لا معرّف المستخدم — وإلا فشل القبول بـ 404
  const accepted = await api('POST', `/api/contacts/requests/${inbox[0].id}/accept`, undefined, appToken);
  assert(accepted.status === 204, `القبول HTTP ${accepted.status}`);
  const mine = (await api('GET', '/api/contacts', undefined, appToken)).data;
  assert(mine.some((c) => c.redId === '10001'), 'لم تُضف جهة الاتصال');
  const theirs = (await api('GET', '/api/contacts', undefined, adminToken)).data;
  assert(theirs.some((c) => c.redId === redId), 'العلاقة ليست متبادلة');
  const presence = await api('GET', '/api/contacts/presence/detailed?ids=10001', undefined, appToken);
  assert(presence.status === 200, `الحضور المفصّل فشل: ${presence.status}`);
  assert(typeof presence.data['10001']?.online === 'boolean', 'عقد online مفقود من الحضور المفصّل');
  assert('lastSeen' in presence.data['10001'], 'عقد lastSeen مفقود من الحضور المفصّل');
  return 'متبادلة بين الطرفين + حضور مفصّل';
});

await check('٩ ينشر في التغذية ويراه مستخدم آخر', async () => {
  const post = await api('POST', '/api/feed/posts',
    { text: 'منشور فحص الربط الآلي', visibility: 'LOCAL_YEMEN', hashtags: ['RED'] }, appToken);
  assert(post.status === 201, `HTTP ${post.status}`);
  assert(post.data.authorRedId === redId, `الكاتب ${post.data.authorRedId}`);
  const feed = (await api('GET', '/api/feed?limit=20', undefined, adminToken)).data;
  assert(feed.posts.some((p) => p.id === post.data.id), 'المنشور غير مرئي للآخرين');
  // تفاعل
  const reacted = await api('POST', `/api/feed/posts/${post.data.id}/reactions`, { type: 'LIKE', active: true }, adminToken);
  assert(reacted.data.reactionCounts.LIKE >= 1, 'التفاعل لم يُحفظ');
  return `${post.data.authorRedId} — تفاعل مسجّل`;
});

await check('١٠ الحظر من اللوحة يقطع التطبيق ويُخفيه من الدليل', async () => {
  await api('POST', '/api/admin/users/action', { userId, action: 'BANNED', reason: 'فحص آلي' });
  const relogin = await api('POST', '/api/auth/login', { username, password: PASSWORD });
  assert(relogin.data.status === 'BANNED', `الحالة ${relogin.data.status}`);
  assert(!relogin.data.accessToken, 'مُنح رمز رغم الحظر');
  const dir = (await api('GET', `/api/directory/search?query=${redId}`, undefined, adminToken)).data;
  assert(dir.length === 0, 'ما زال ظاهرًا في الدليل');
  const user = (await api('GET', `/api/admin/users/${userId}`)).data;
  assert(user.devices.every((dv) => dv.status === 'REVOKED'), 'بقيت أجهزة فعّالة');
  return 'الجلسة والدليل والأجهزة — كلها قُطعت';
});

// ═══ 3. الخصوصية ═══
console.log('\n── ثوابت الخصوصية ──');

await check('لا يوجد جدول أو حقل لنص الرسائل الخاصة', async () => {
  const { readFileSync } = await import('node:fs');
  const root = new URL('../dev-server/', import.meta.url).pathname;
  const sources = ['db.cjs', 'server.cjs', 'app-routes.cjs']
    .map((f) => readFileSync(root + f, 'utf8')).join('\n');
  for (const banned of ['message_body', 'message_text', 'plaintext', 'cipher_text_stored']) {
    assert(!sources.includes(banned), `وُجد ${banned}`);
  }
  return 'الرسائل تمر مشفّرة عبر /ws/master ولا تُخزَّن';
});

await check('لا يُعاد أي مفتاح خاص لمستخدم عبر أي مسار', async () => {
  const probes = ['/api/me', '/api/contacts', `/api/identity/directory/10001`, '/api/devices'];
  for (const p of probes) {
    const body = JSON.stringify((await api('GET', p, undefined, adminToken)).data || {}).toLowerCase();
    assert(!body.includes('privatekey'), `تسريب في ${p}`);
    assert(!body.includes('passwordhash'), `تسريب تجزئة كلمة المرور في ${p}`);
  }
  return `${probes.length} مسارات نظيفة`;
});

await check('سلطة التوقيع تكشف المفتاح العام فقط', async () => {
  const res = (await api('GET', '/api/identity/authority')).data;
  assert(res.publicKey && (res.algorithm === 'ECDSA_P256_SHA256' || res.algorithm === 'SHA256withECDSA'), 'عقد غير متوقع');
  assert(!JSON.stringify(res).toLowerCase().includes('private'), 'تسريب المفتاح الخاص للسلطة');
  return res.curve;
});

await check('معرّف يونس خمسة أرقام في كل مسار يعيده الخادم', async () => {
  // مصدر الحقيقة: RedIdGenerator.PATTERN و YounesId.PATTERN — أي انحراف
  // هنا يعني أن التطبيق سيرفض المعرّف ويعطّل أزرار الاتصال والإضافة.
  const pattern = /^[1-9][0-9]{4}$/;
  const me = (await api('GET', '/api/me', undefined, adminToken)).data;
  assert(pattern.test(me.redId), `/api/me أعاد ${me.redId}`);

  const users = (await api('GET', '/api/admin/users?size=100', undefined, adminToken)).data.content;
  const bad = users.filter((u) => !pattern.test(u.redId));
  assert(bad.length === 0, `معرّفات مخالفة: ${bad.map((u) => u.redId).join(', ')}`);

  // المعرّف المولّد عند التسجيل يجب أن يلتزم الصيغة نفسها لا صيغة البذرة
  const uniq = `probe_${Date.now().toString(36)}`;
  const reg = (await api('POST', '/api/auth/register', {
    username: uniq, displayName: 'فحص الصيغة', password: 'Passw0rd#2026',
    deviceName: 'CheckDevice', platform: 'ANDROID', identityFingerprint: 'ff:00:11',
  })).data;
  assert(pattern.test(reg.user.redId), `التوليد أعاد ${reg.user.redId}`);

  return `${users.length} مستخدمًا + معرّف مولّد (${reg.user.redId}) — كلها خمسة أرقام`;
});

await check('البحث في الدليل محكوم بحدّ معدل يمنع حصاد الـ90 ألف معرّف', async () => {
  // اختصار المعرّف إلى 5 أرقام يجعل الفضاء قابلًا للتعداد بالكامل،
  // فالحماية انتقلت من طول المعرّف إلى ضبط المعدل. سقوط هذا الفحص
  // يعني أن الدليل كله صار قابلًا للحصاد من حساب معتمد واحد.
  // ⚠️ عزل: هذا الفحص يستنفد ميزانية المسؤول عمدًا، والنافذة منزلقة
  // لمدة دقيقة. لو استُهلكت ميزانية المسؤول لأفسد ذلك كل فحص لاحق
  // يبحث في الدليل — بل وأفسد إعادة تشغيل السكربت خلال الدقيقة نفسها.
  // لذلك نُنشئ حسابًا خاصًا بهذا الفحص ونستنفد ميزانيته هو.
  const victim = `ratelimit_${Date.now().toString(36)}`;
  const reg = (await api('POST', '/api/auth/register', {
    username: victim, displayName: 'فحص حدّ المعدل', password: 'Passw0rd#2026',
    deviceName: 'RateProbe', platform: 'ANDROID', identityFingerprint: 'rl:00:01',
  })).data;
  await api('POST', '/api/admin/users/action',
    { userId: reg.user.id, action: 'APPROVED', reason: 'فحص آلي' }, adminToken);
  const probeToken = (await api('POST', '/api/auth/login',
    { username: victim, password: 'Passw0rd#2026' })).data.accessToken;
  assert(probeToken, 'تعذّر تسجيل دخول حساب الفحص');

  let limited = null;
  for (let i = 0; i < 40; i++) {
    const r = await api('GET', '/api/directory/search?query=38715', undefined, probeToken);
    if (r.status === 429) { limited = r; break; }
  }
  assert(limited, 'لم يُفعَّل حدّ المعدل بعد 40 طلبًا — الدليل مكشوف');

  // الحدّ لكل مستخدم لا عام: المسؤول يجب أن يبقى قادرًا على البحث.
  const admin = await api('GET', '/api/directory/search?query=38715', undefined, adminToken);
  assert(admin.status === 200, `الحدّ سرى على مستخدم آخر — يجب أن يكون لكل حساب (${admin.status})`);
  assert(limited.data.error === 'DIRECTORY_RATE_LIMITED', `رمز غير متوقع: ${limited.data.error}`);
  assert(limited.data.retryAfterSeconds > 0, 'يجب إبلاغ العميل بمدة الانتظار');
  return `429 DIRECTORY_RATE_LIMITED بعد الحدّ · retryAfter=${limited.data.retryAfterSeconds}s`;
});

await check('عقد الإشراف يطابق ModerationController لا صفحة البلاغات', async () => {
  // العطل: خادم التطوير كان يعيد شكل `reportDto` (reporterUsername /
  // description) على مسار الإشراف، بينما المتحكّم الحقيقي يعيد
  // reporterRedId / details. النتيجة صفحة «الثقة والسلامة» تعرض
  // أعمدة فارغة رغم وجود البيانات.
  const open = (await api('GET', '/api/admin/moderation/reports?status=OPEN', undefined, adminToken)).data;
  assert(Array.isArray(open), 'يجب أن تكون مصفوفة');
  assert(open.length > 0, 'لا بلاغات مفتوحة للفحص');
  for (const r of open) {
    for (const field of ['id', 'reporterRedId', 'category', 'status', 'createdAt']) {
      assert(r[field] != null, `الحقل ${field} مفقود — الواجهة ستعرض عمودًا فارغًا`);
    }
    assert(/^[1-9][0-9]{4}$/.test(r.reporterRedId), `معرّف المُبلّغ ليس خماسيًا: ${r.reporterRedId}`);
    assert(!('reporterUsername' in r), 'عاد شكل reportDto بدل عقد الإشراف');
  }

  // المعالجة تعيد البلاغ المحدَّث لا ردًّا فارغًا
  const target = open[0];
  const patched = await api('PATCH', `/api/admin/moderation/reports/${target.id}?status=RESOLVED`, undefined, adminToken);
  assert(patched.status === 200, `المعالجة فشلت: ${patched.status}`);
  assert(patched.data.status === 'RESOLVED', `الحالة لم تتغيّر: ${patched.data.status}`);
  assert(patched.data.reporterRedId === target.reporterRedId, 'أُعيد بلاغ مختلف');

  // لا يجوز إرجاع بلاغ إلى OPEN، ولا معالجة بلاغ غير موجود
  const reopened = await api('PATCH', `/api/admin/moderation/reports/${target.id}?status=OPEN`, undefined, adminToken);
  assert(reopened.status === 400, `إعادة الفتح يجب أن تُرفض، عاد ${reopened.status}`);
  const missing = await api('PATCH', '/api/admin/moderation/reports/does-not-exist?status=RESOLVED', undefined, adminToken);
  assert(missing.status === 404, `بلاغ مفقود يجب أن يعيد 404، عاد ${missing.status}`);

  return `${open.length} بلاغًا بحقول العقد · معالجة تعيد الصف · 400 لإعادة الفتح · 404 للمفقود`;
});

await check('حزم المفاتيح ومخزون prekey لا تُكشف بلا مصادقة', async () => {
  // كان `/api/identity/directory` معلَّمًا permitAll، فأمكن حصاد
  // المفاتيح العامة والبصمات وشهادات التخويل بلا حساب. والأخطر:
  // كل نداء لـ `…/prekey` يستهلك مفتاحًا لمرة واحدة استهلاكًا فعليًا،
  // فحلقة بسيطة تستنزف مخزون أي مستخدم وتُدهور جلساته الجديدة.
  const anon = await api('GET', '/api/identity/directory/10001');
  assert(anon.status === 401, `الدليل يجب أن يرفض بلا رمز، عاد ${anon.status}`);

  const authed = await api('GET', '/api/identity/directory/10001', undefined, adminToken);
  assert(authed.status === 200, `المستخدم المصادَق يجب أن يقرأ الدليل، عاد ${authed.status}`);
  const device = authed.data.devices?.[0];
  assert(device?.deviceId, 'لا جهاز معتمد للفحص');

  const anonPrekey = await api('GET', `/api/identity/directory/10001/${device.deviceId}/prekey`);
  assert(anonPrekey.status === 401, `استهلاك prekey يجب أن يُرفض بلا رمز، عاد ${anonPrekey.status}`);

  const authedPrekey = await api('GET', `/api/identity/directory/10001/${device.deviceId}/prekey`, undefined, adminToken);
  assert(authedPrekey.status === 200, `المصادَق يجب أن يستهلك prekey، عاد ${authedPrekey.status}`);

  // لا يُعاد أي مفتاح خاص مهما كانت الصلاحية
  const body = JSON.stringify(authedPrekey.data).toLowerCase();
  assert(!body.includes('privatekey'), 'تسرّب مفتاح خاص في حزمة prekey');

  return '401 للمجهول على المسارين · 200 للمصادَق · بلا مفاتيح خاصة';
});

await check('مستخدم عادي يصوّت ويؤكّد حضوره، ولا يبلغ المسارات الإدارية', async () => {
  // كانت `/api/admin/content/**` كلها `hasRole("ADMIN")`، وفيها
  // vote/rsvp/checkin — أفعال مشارِك تأخذ هوية المستدعي نفسه.
  // النتيجة: لا مستخدم عادي يستطيع التصويت أو تأكيد الحضور (403).
  // وخادم التطوير كان يفتح كل /api/admin بلا فحص دور، فأخفى العطل.
  const uname = `poll_${Date.now().toString(36)}`;
  const reg = (await api('POST', '/api/auth/register', {
    username: uname, displayName: 'ناخب', password: 'Passw0rd#2026',
    deviceName: 'VoteProbe', platform: 'ANDROID', identityFingerprint: 'vp:01',
  })).data;
  await api('POST', '/api/admin/users/action',
    { userId: reg.user.id, action: 'APPROVED', reason: 'فحص آلي' }, adminToken);
  const userToken = (await api('POST', '/api/auth/login',
    { username: uname, password: 'Passw0rd#2026' })).data.accessToken;

  // إداري بحق ⇒ 403 للمستخدم العادي، 200 للمسؤول
  const denied = await api('GET', '/api/admin/users', undefined, userToken);
  assert(denied.status === 403, `مسار إداري يجب أن يُرفض بـ403، عاد ${denied.status}`);
  const allowed = await api('GET', '/api/admin/users', undefined, adminToken);
  assert(allowed.status === 200, `المسؤول يجب أن يمرّ، عاد ${allowed.status}`);

  // قراءة المحتوى المنشور متاحة للمستخدم
  const polls = await api('GET', '/api/admin/content/polls/active', undefined, userToken);
  assert(polls.status === 200, `قراءة الاستطلاعات يجب أن تمرّ، عادت ${polls.status}`);
  const poll = polls.data[0];
  assert(poll?.options?.length, 'لا استطلاع نشط للفحص');

  // العقد `optionIds` مصفوفةً — كما في PollsApi و ContentController.
  // كان خادم التطوير يقرأ `optionId` مفردًا فيرفض كل تصويت حقيقي.
  const before = poll.options[0].votes;
  const voted = await api('POST', `/api/admin/content/polls/${poll.id}/vote`,
    { optionIds: [poll.options[0].id] }, userToken);
  assert(voted.status === 200, `التصويت فشل: ${voted.status} ${JSON.stringify(voted.data)}`);

  const after = (await api('GET', '/api/admin/content/polls/active', undefined, userToken))
    .data.find((x) => x.id === poll.id).options[0].votes;
  assert(after === before + 1, `العدّاد لم يتغيّر: ${before} → ${after}`);

  // التصويت المكرر يفسد نتيجة الاستطلاع لو مرّ
  const again = await api('POST', `/api/admin/content/polls/${poll.id}/vote`,
    { optionIds: [poll.options[0].id] }, userToken);
  assert(again.status === 400 && again.data.error === 'ALREADY_VOTED',
    `التصويت المكرر يجب أن يُرفض، عاد ${again.status} ${again.data.error}`);

  // مسارات الملصقات تحت /api/admin/content هي أيضًا أفعال مستخدم عادي.
  const packs = await api('GET', '/api/admin/content/sticker-packs/published', undefined, userToken);
  assert(packs.status === 200 && packs.data.length > 0, `الحزم المنشورة فشلت: ${packs.status}`);
  const pack = packs.data[0];
  const stickers = await api('GET', `/api/admin/content/sticker-packs/${pack.id}/stickers`, undefined, userToken);
  assert(stickers.status === 200 && stickers.data.length > 0, `ملصقات الحزمة فشلت: ${stickers.status}`);
  const installed = await api('POST', `/api/admin/content/sticker-packs/${pack.id}/install`, undefined, userToken);
  assert(installed.status === 200 && installed.data.success, `تثبيت الحزمة فشل: ${installed.status}`);
  const mine = await api('GET', '/api/admin/content/sticker-packs/installed', undefined, userToken);
  assert(mine.status === 200 && mine.data.some((x) => x.id === pack.id), 'الحزمة المثبتة لم تظهر للمستخدم');
  const removed = await api('DELETE', `/api/admin/content/sticker-packs/${pack.id}/install`, undefined, userToken);
  assert(removed.status === 200 && removed.data.success, `إلغاء التثبيت فشل: ${removed.status}`);

  // الاستكشاف ليس fixtures: إنشاء بث/مساحة يظهر فوراً لطرف آخر ويمكنه الانضمام.
  const createdStream = await api('POST', '/api/livestream/create', { title: 'بث فحص حي', isPrivate: false }, userToken);
  assert(createdStream.status === 200 && createdStream.data.streamId, `إنشاء البث فشل: ${createdStream.status}`);
  const streams = await api('GET', '/api/livestream/public?query=فحص', undefined, adminToken);
  assert(streams.status === 200 && streams.data.some((x) => x.streamId === createdStream.data.streamId), 'البث لم يظهر في الاستكشاف');
  const joinedStream = await api('POST', `/api/livestream/${createdStream.data.streamId}/join`, {}, adminToken);
  assert(joinedStream.status === 200 && joinedStream.data.authorized, 'تعذر الانضمام للبث');
  const leftStream = await api('POST', `/api/livestream/${createdStream.data.streamId}/leave`, {}, adminToken);
  assert(leftStream.status === 200 && leftStream.data.viewerCount === 0, 'عداد المشاهدين لم ينخفض بعد المغادرة');
  const stoppedStream = await api('POST', `/api/livestream/${createdStream.data.streamId}/stop`, {}, userToken);
  assert(stoppedStream.status === 200 && stoppedStream.data.stopped, 'صاحب البث لم يستطع إيقافه');

  const createdSpace = await api('POST', '/api/conference/create', { title: 'مساحة فحص حية', isSpace: true }, userToken);
  assert(createdSpace.status === 200 && createdSpace.data.roomId, `إنشاء المساحة فشل: ${createdSpace.status}`);
  const spaces = await api('GET', '/api/conference/public?isSpace=true&query=فحص', undefined, adminToken);
  assert(spaces.status === 200 && spaces.data.some((x) => x.roomId === createdSpace.data.roomId), 'المساحة لم تظهر في الاستكشاف');
  const joinedSpace = await api('POST', `/api/conference/${createdSpace.data.roomId}/join`, {}, adminToken);
  assert(joinedSpace.status === 200 && joinedSpace.data.authorized, 'تعذر الانضمام للمساحة');

  const community = await api('POST', '/api/communities', {
    name: 'مجتمع فحص حي', description: 'بيانات تنفيذية', category: 'TECH', isPublic: true,
  }, userToken);
  assert(community.status === 201 && community.data.isJoined, `إنشاء المجتمع فشل: ${community.status}`);
  const joinedCommunity = await api('POST', `/api/communities/${community.data.id}/join`, {}, adminToken);
  assert(joinedCommunity.status === 200 && joinedCommunity.data.isJoined && joinedCommunity.data.memberCount === 2,
    'دورة انضمام المجتمع لم تُحفظ');
  const leftCommunity = await api('POST', `/api/communities/${community.data.id}/leave`, {}, adminToken);
  assert(leftCommunity.status === 204, `مغادرة المجتمع فشلت: ${leftCommunity.status}`);

  return `403 للإداري · تصويت ${before}→${after} · ملصقات · بث · مساحة · مجتمع كامل`;
});

console.log(`\n${'─'.repeat(60)}`);
if (fail === 0) {
  console.log(`🎉 نجحت كل الفحوص: ${pass}/${pass} — التطبيق والخادم واللوحة على قاعدة واحدة`);
  process.exit(0);
} else {
  console.log(`⚠️  ${pass} نجح · ${fail} فشل`);
  process.exit(1);
}
