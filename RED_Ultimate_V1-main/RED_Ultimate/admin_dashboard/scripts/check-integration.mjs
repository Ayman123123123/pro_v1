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

async function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
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

const PASSWORD = 'RedSovereign#2026';
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
  for (const field of ['id', 'redId', 'username', 'displayName', 'status', 'role', 'pstnEnabled', 'pstnDailyLimit', 'devices']) {
    assert(field in u, `الحقل ${field} مفقود — سيفشل فك الترميز في التطبيق`);
  }
  assert(/^(RED|YNS)-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$/.test(u.redId), `صيغة RED ID غير صحيحة: ${u.redId}`);
  assert(res.data.accessToken, 'لا يوجد رمز وصول');
  adminToken = res.data.accessToken;
  return `${u.redId} — ${Object.keys(u).length} حقلًا`;
});

await check('كل معرّفات RED تطابق نمط التطبيق (RedIdGenerator)', async () => {
  // النمط نفسه في red-app/ui/RedDashboard.kt و features/contacts/QrScannerSheet.kt.
  // أي معرّف خارجه يجعل أزرار الاتصال والإضافة معطّلة في التطبيق بلا سبب ظاهر.
  const pattern = /^(RED|YNS)-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$/;
  const users = (await api('GET', '/api/admin/users?size=100')).data.content;
  const invalid = users.filter((u) => !pattern.test(u.redId));
  assert(invalid.length === 0, `معرّفات مرفوضة من التطبيق: ${invalid.map((u) => u.redId).join(', ')}`);
  return `${users.length} معرّفًا — كلها بصيغة YNS-XXXX-XXXX`;
});

await check('‎/api/me يعيد هوية صاحب الرمز نفسه لا حسابًا آخر', async () => {
  const me = await api('GET', '/api/me', undefined, adminToken);
  assert(me.status === 200, `HTTP ${me.status}`);
  assert(me.data.redId === 'YNS-7K4M-82QX', `أعاد ${me.data.redId}`);
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
  const dir = (await api('GET', `/api/identity/directory/${redId}`)).data;
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
  assert(inbox[0].requester.redId === 'YNS-7K4M-82QX', 'المُرسِل غير صحيح');
  // معرّف الطلب لا معرّف المستخدم — وإلا فشل القبول بـ 404
  const accepted = await api('POST', `/api/contacts/requests/${inbox[0].id}/accept`, undefined, appToken);
  assert(accepted.status === 204, `القبول HTTP ${accepted.status}`);
  const mine = (await api('GET', '/api/contacts', undefined, appToken)).data;
  assert(mine.some((c) => c.redId === 'YNS-7K4M-82QX'), 'لم تُضف جهة الاتصال');
  const theirs = (await api('GET', '/api/contacts', undefined, adminToken)).data;
  assert(theirs.some((c) => c.redId === redId), 'العلاقة ليست متبادلة');
  return 'متبادلة بين الطرفين';
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
  const probes = ['/api/me', '/api/contacts', `/api/identity/directory/YNS-7K4M-82QX`, '/api/devices'];
  for (const p of probes) {
    const body = JSON.stringify((await api('GET', p, undefined, adminToken)).data || {}).toLowerCase();
    assert(!body.includes('privatekey'), `تسريب في ${p}`);
    assert(!body.includes('passwordhash'), `تسريب تجزئة كلمة المرور في ${p}`);
  }
  return `${probes.length} مسارات نظيفة`;
});

await check('سلطة التوقيع تكشف المفتاح العام فقط', async () => {
  const res = (await api('GET', '/api/identity/authority')).data;
  assert(res.publicKey && res.algorithm === 'SHA256withECDSA', 'عقد غير متوقع');
  assert(!JSON.stringify(res).toLowerCase().includes('private'), 'تسريب المفتاح الخاص للسلطة');
  return res.curve;
});

console.log(`\n${'─'.repeat(60)}`);
if (fail === 0) {
  console.log(`🎉 نجحت كل الفحوص: ${pass}/${pass} — التطبيق والخادم واللوحة على قاعدة واحدة`);
  process.exit(0);
} else {
  console.log(`⚠️  ${pass} نجح · ${fail} فشل`);
  process.exit(1);
}
