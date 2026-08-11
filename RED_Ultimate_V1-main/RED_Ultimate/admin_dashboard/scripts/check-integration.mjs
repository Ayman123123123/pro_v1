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
  assert(res.publicKey && res.algorithm === 'SHA256withECDSA', 'عقد غير متوقع');
  assert(!JSON.stringify(res).toLowerCase().includes('private'), 'تسريب المفتاح الخاص للسلطة');
  return res.curve;
});

console.log('\n── بوابات DINSTAR: الأسطول وتفسير الإشارة ──');

await check('تفسير الإشارة يطابق 3GPP TS 27.007 في الخادم والواجهة', async () => {
  const { readFileSync } = await import('node:fs');
  const root = new URL('../', import.meta.url).pathname;

  // القراءة 99 تعني «غير قابلة للكشف». أي شيفرة تقصرها على 31 تُنتج
  // إشارة 100% لشريحة ميتة، وهو ما كان يجعل الموزّع يختارها أولًا.
  const kotlin = readFileSync(`${root}../backend-server/src/main/kotlin/com/red/server/services/DinstarSignal.kt`, 'utf8');
  assert(kotlin.includes('UNKNOWN_BASIC = 99'), 'الخادم لا يعرّف القراءة 99 كانعدام إشارة');
  assert(kotlin.includes('2 * raw - 113'), 'معادلة dBm المعيارية غائبة عن الخادم');

  // نفحص الشيفرة التنفيذية فقط: التعليق الذي يشرح الخلل يذكر الصيغة
  // القديمة عمدًا، ومطابقته تجعل الفحص يفشل على توثيقه هو.
  const hardware = readFileSync(`${root}../backend-server/src/main/kotlin/com/red/server/services/DinstarHardwareService.kt`, 'utf8');
  const executable = hardware.split('\n')
    .filter((line) => !line.trim().startsWith('//') && !line.trim().startsWith('*'))
    .join('\n');
  assert(!/coerceIn\(0,\s*31\)/.test(executable), 'ما زال الخادم يقصر قراءة الإشارة على 31 — الخلل الأصلي');
  assert(hardware.includes('DinstarSignal.interpret'), 'الخادم لا يستدعي المفسّر المعياري');

  const dev = readFileSync(`${root}dev-server/server.cjs`, 'utf8');
  assert(dev.includes('interpretSignal'), 'خادم التطوير لا يفسّر الإشارة كالخادم الحقيقي');
  assert(/raw === 99/.test(dev), 'خادم التطوير لا يعامل 99 كانعدام إشارة');
  return 'الخادم وخادم التطوير على المعادلة نفسها';
});

await check('الأسطول يضم عدة أجهزة بطرازات وأعداد منافذ مختلفة', async () => {
  const fleet = (await api('GET', '/api/admin/dinstar/fleet')).data;
  assert(Array.isArray(fleet) && fleet.length >= 2, `عدد البوابات ${fleet.length} — يُتوقع جهازان على الأقل`);
  const models = new Set(fleet.map((g) => g.model));
  assert(models.has('UC2000-VE-8G') && models.has('UC2000-VE-8T'),
    `الطرازان المعتمدان غير مسجّلين: ${[...models].join(', ')}`);
  // عدد المنافذ يجب أن يتبع الطراز لا أن يكون 8 دائمًا
  for (const g of fleet) {
    const expected = g.model.includes('-4') ? 4 : 8;
    assert(g.portCount === expected, `${g.model} يبلّغ ${g.portCount} منفذًا بدل ${expected}`);
  }
  return `${fleet.length} أجهزة — ${[...models].join('، ')}`;
});

await check('شريحة مسجّلة بلا إشارة قابلة للقياس لا تُحسب جاهزة', async () => {
  const d = (await api('GET', '/api/admin/dinstar/fleet/ports')).data;
  const flat = d.gateways.flatMap((g) => g.ports);

  const dead = flat.filter((p) => p.status === 'REGISTERED' && p.signalRaw === 99);
  assert(dead.length > 0, 'لا توجد حالة اختبار للقراءة 99');
  for (const p of dead) {
    // هذا بالضبط ما كان مكسورًا: 99 ⇒ 31 ⇒ 100%
    assert(p.signal === null, `القراءة 99 أنتجت نسبة ${p.signal} بدل null`);
    assert(p.signalUsable === false, 'شريحة بلا قياس صُنّفت جاهزة');
    assert(p.signalLabel === 'NO_SIGNAL', `تصنيف غير متوقع: ${p.signalLabel}`);
  }
  assert(d.totals.usable < d.totals.registered,
    'المجاميع لا تفرّق بين المسجّل والجاهز');
  return `${d.totals.registered} مسجّلة منها ${d.totals.usable} جاهزة — ${dead.length} مستبعدة بالقراءة 99`;
});

await check('التوجيه يستبعد المنافذ الميتة ويفضّل نفس المشغل', async () => {
  // 77 = يمن موبايل. يجب أن يقع الاختيار على شريحة يمن موبايل إن توفرت.
  const res = (await api('POST', '/api/admin/dinstar/fleet/routing/select', { number: '771234567' })).data;
  assert(res.selected, 'لم يُختر أي منفذ');
  assert(res.targetOperator === 'YemenMobile', `تصنيف المشغل خاطئ: ${res.targetOperator}`);
  assert(res.selected.onNet === true, 'لم يفضّل التوجيه شريحة المشغل نفسه رغم توفرها');
  assert(res.selected.signalDbm <= -1, 'قوة الإشارة يجب أن تكون بالسالب (dBm)');

  const noSignal = res.rejected.filter((r) => r.why === 'REJECTED_NO_SIGNAL');
  assert(noSignal.length > 0, 'لم يُستبعد أي منفذ لانعدام الإشارة');
  assert(noSignal.some((r) => r.signalRaw === 99), 'المنفذ ذو القراءة 99 لم يُستبعد');
  return `اختير ${res.selected.gatewayHost}#${res.selected.portIndex} (${res.selected.signalDbm} dBm، نفس الشبكة) واستُبعد ${res.rejected.length}`;
});

await check('البوابة المعطّلة تخرج من التوجيه ومن استعلام المنافذ', async () => {
  const fleet = (await api('GET', '/api/admin/dinstar/fleet')).data;
  const disabled = fleet.filter((g) => !g.enabled);
  assert(disabled.length > 0, 'لا توجد بوابة معطّلة للاختبار');
  const ports = (await api('GET', '/api/admin/dinstar/fleet/ports')).data;
  const hosts = ports.gateways.map((g) => g.gateway.host);
  for (const g of disabled) {
    assert(!hosts.includes(g.host), `البوابة المعطّلة ${g.host} ما زالت تُستعلم`);
  }
  return `${disabled.length} معطّلة — مستبعدة من ${ports.totals.gateways} مفعّلة`;
});

await check('تسجيل بوابة يرفض العنوان العام والطراز غير المدعوم', async () => {
  // الفحص والاتصال مقصوران على شبكة إدارة خاصة — لا يجوز أن يقبل
  // الخادم توجيه مكالمات عبر عنوان على الإنترنت العام.
  // عناوين عامة متعددة حتى لا يعتمد الفحص على عنوان بعينه
  for (const host of ['8.8.8.8', '1.1.1.1', '203.0.113.7']) {
    const res = await api('POST', '/api/admin/dinstar/fleet', { host, model: 'UC2000-VE-8G' });
    assert(res.status === 400 && res.data.error === 'PRIVATE_ADDRESS_REQUIRED',
      `العنوان العام ${host} أعاد ${res.status} ${res.data.error || ''}`);
  }

  // عنوان خاص فريد حتى لا يصطدم الفحص ببوابة سجّلها تشغيل سابق
  const freeHost = `192.168.11.${160 + Math.floor(Math.random() * 40)}`;
  const badModel = await api('POST', '/api/admin/dinstar/fleet', { host: freeHost, model: 'DWG2000-16G' });
  assert(badModel.status === 400 && badModel.data.error === 'UNSUPPORTED_MODEL',
    `طراز غير مدعوم أعاد ${badModel.status} ${badModel.data.error || ''}`);
  return 'العنوان العام والطراز المجهول مرفوضان';
});

await check('دورة حياة بوابة: تسجيل ← تعطيل ← حذف', async () => {
  const host = `192.168.11.${100 + Math.floor(Math.random() * 50)}`;
  const created = await api('POST', '/api/admin/dinstar/fleet',
    { host, model: 'UC2000-VE-4T', pjsipEndpoint: 'dinstar-test', siteLabel: 'فحص آلي' });
  assert(created.status === 201, `التسجيل أعاد ${created.status}`);
  const id = created.data.id;

  const listed = (await api('GET', '/api/admin/dinstar/fleet')).data.find((g) => g.id === id);
  assert(listed, 'البوابة الجديدة لا تظهر في القائمة');
  assert(listed.portCount === 4, `الطراز الرباعي سُجّل بـ ${listed.portCount} منافذ`);

  const dup = await api('POST', '/api/admin/dinstar/fleet', { host, model: 'UC2000-VE-4T' });
  assert(dup.status === 400, 'قُبل تسجيل مكرر لنفس العنوان');

  const off = await api('POST', `/api/admin/dinstar/fleet/${id}/enabled`, { enabled: false });
  assert(off.status === 200 && off.data.enabled === false, 'التعطيل لم ينجح');

  const del = await api('DELETE', `/api/admin/dinstar/fleet/${id}`);
  assert(del.status === 200, `الحذف أعاد ${del.status}`);
  const gone = (await api('GET', '/api/admin/dinstar/fleet')).data.find((g) => g.id === id);
  assert(!gone, 'البوابة ما زالت موجودة بعد الحذف');
  return 'تسجيل ورفض تكرار وتعطيل وحذف — كلها فعلية';
});

await check('قرارات التوجيه تُسجَّل بلا كشف رقم الوجهة كاملًا', async () => {
  await api('POST', '/api/admin/dinstar/fleet/routing/select', { number: '967771234567' });
  const decisions = (await api('GET', '/api/admin/dinstar/fleet/routing/decisions')).data;
  assert(decisions.length > 0, 'لم يُسجَّل أي قرار توجيه');
  const body = JSON.stringify(decisions);
  // البادئة تكفي للتحليل؛ الرقم الكامل في سجل يقرأه المسؤول تتبّع لا مبرر له
  assert(!body.includes('771234567'), 'رقم الوجهة الكامل مكشوف في سجل التوجيه');
  assert(decisions[0].destinationPrefix?.length <= 2, 'البادئة المخزّنة أطول من رقمين');
  return `${decisions.length} قرارًا — البادئة فقط`;
});


await check('التعرّف على الجهاز يجمع إشارات مستقلة لا ردًّا واحدًا', async () => {
  // جهاز بعنوان MAC ضمن نطاق Dinstar المسجّل ⇒ ثقة كاملة
  const withMac = (await api('POST', '/api/admin/dinstar/fleet/probe', { host: '192.168.11.1' })).data;
  assert(withMac.reachable === true, 'البوابة المعروفة يجب أن تكون قابلة للوصول');
  assert(withMac.confidence === 100, `ثقة متوقعة 100 لا ${withMac.confidence}`);
  assert(withMac.signals.length >= 4, 'يجب رصد أربع إشارات على الأقل');
  assert(withMac.signals.some((x) => x.includes('F8:A0:3D')),
    'لم تُرصد بادئة OUI المسجّلة لـ Dinstar');
  assert(withMac.adoptable === true, 'جهاز بثقة كاملة يجب أن يكون قابلًا للضم');

  // جهاز بلا MAC: الثقة تنخفض لكنه يبقى قابلًا للضم — غياب MAC
  // وارد خلف NAT ولا يصح أن يمنع التعرّف
  const noMac = (await api('POST', '/api/admin/dinstar/fleet/probe', { host: '192.168.11.3' })).data;
  assert(noMac.confidence === 80, `ثقة متوقعة 80 بلا MAC لا ${noMac.confidence}`);
  assert(noMac.adoptable === true, 'غياب MAC وحده يجب ألا يمنع الضم');
  assert(!noMac.signals.some((x) => x.includes('MAC')), 'لا يصح رصد إشارة MAC وهو غائب');

  // عنوان لا جهاز عليه
  const absent = (await api('POST', '/api/admin/dinstar/fleet/probe', { host: '192.168.11.99' })).data;
  assert(absent.reachable === false, 'عنوان بلا جهاز يجب ألا يكون قابلًا للوصول');
  assert(absent.confidence === 0, 'عنوان بلا جهاز يجب أن تكون ثقته صفرًا');

  return `100 مع MAC · 80 بدونه · 0 لعنوان فارغ`;
});

await check('الرقم التسلسلي هو هوية البوابة لا عنوانها الشبكي', async () => {
  const fleet = (await api('GET', '/api/admin/dinstar/fleet')).data;
  assert(fleet.length >= 2, 'يلزم جهازان على الأقل');
  const serials = fleet.map((g) => g.serialNumber).filter(Boolean);
  assert(serials.length >= 2, 'البوابات يجب أن تُفصح عن أرقام تسلسلية');
  assert(new Set(serials).size === serials.length, 'الأرقام التسلسلية ليست فريدة');
  // العنوان يتبدّل مع DHCP؛ التسلسلي ثابت مدى حياة الجهاز
  const macs = fleet.map((g) => g.macAddress).filter(Boolean);
  assert(macs.every((m) => /^F8:A0:3D:/i.test(m)),
    'عنوان MAC خارج نطاق Dinstar المسجّل');
  return `${serials.length} رقمًا تسلسليًا فريدًا · ${macs.length} عنوان MAC ضمن نطاق Dinstar`;
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

await check('حدود SMS تطابق وثيقة Dinstar الرسمية لا أرقامًا مخترعة', async () => {
  // 128 مستلمًا و1500 بايت لـ send_sms. الرقم 32 يخصّ query_sms_result
  // وحده، والخلط بينهما كان يقطع الإرسال الجماعي عند 32 بلا سبب.
  const okRes = await api('POST', '/api/admin/dinstar/sms/send', {
    text: 'رسالة اختبار', param: [{ number: '777123456', user_id: 1 }], encoding: 'gsm-7bit',
  }, adminToken);
  assert(okRes.status === 200, `الإرسال السليم فشل: ${okRes.status}`);
  // 202 = قُبلت للتنفيذ لاحقًا وهي نجاح؛ اعتبارها فشلًا كان يسجّل كل
  // إرسال ناجح كخطأ في السجل.
  assert(okRes.data.error_code === 202, `يُتوقع 202 لا ${okRes.data.error_code}`);

  const many = await api('POST', '/api/admin/dinstar/sms/send', {
    text: 'hi', param: Array.from({ length: 129 }, (_, i) => ({ number: `7771${i}`, user_id: i })),
  }, adminToken);
  assert(many.status === 400, '129 مستلمًا يجب أن تُرفض');

  // القياس بالبايت لا بالحرف: 800 حرف عربي = 1600 بايت.
  const long = await api('POST', '/api/admin/dinstar/sms/send', {
    text: 'م'.repeat(800), param: [{ number: '777123456', user_id: 1 }],
  }, adminToken);
  assert(long.status === 400, '1600 بايت يجب أن تُرفض رغم أن الأحرف 800 فقط');
  assert(/1600/.test(JSON.stringify(long.data)), 'رسالة الخطأ يجب أن تذكر البايتات الفعلية');

  return '202 للقبول · رفض 129 مستلمًا · رفض 1600 بايت';
});

await check('الرسائل الواردة من GSM تصل إلى اللوحة', async () => {
  const inbox = (await api('GET', '/api/admin/dinstar/sms/incoming', undefined, adminToken)).data;
  const rows = inbox.sms || [];
  assert(rows.length > 0, 'لا رسائل واردة');
  for (const m of rows) {
    assert(typeof m.text === 'string' && m.text.length > 0, 'رسالة بلا نص');
    assert(typeof m.number === 'string', 'رسالة بلا رقم مُرسِل');
    assert(Number.isInteger(m.port), 'رسالة بلا منفذ مصدر');
  }
  return `${rows.length} رسالة واردة بنصّها ومنفذها`;
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

await check('إرسال SMS يختار بوابة من الأسطول ويرفض العناوين الخارجية', async () => {
  // كان `postJson` يثبّت البوابة النشطة، فكل SMS يخرج من جهاز واحد
  // مهما بلغ عدد المسجَّلين — تُهدَر شرائح البقية ويُحتسب الإرسال
  // كله خارج الشبكة على مشغّل واحد.
  const fleet = (await api('GET', '/api/admin/dinstar/fleet', undefined, adminToken)).data;
  const host = fleet[0]?.host;
  assert(host, 'لا بوابة مسجّلة');

  const payload = (extra) => ({ text: 'فحص', param: [{ number: '777123456', user_id: 1 }], ...extra });

  const viaFleet = await api('POST', '/api/admin/dinstar/sms/send', payload({ gatewayHost: host }), adminToken);
  assert(viaFleet.status === 200, `الإرسال عبر بوابة مسجّلة فشل: ${viaFleet.status}`);

  // العنوان يصل من طلب HTTP: بلا فحص يطلب الخادم أي وجهة يختارها المرسِل.
  for (const evil of ['8.8.8.8', '169.254.169.254']) {
    const r = await api('POST', '/api/admin/dinstar/sms/send', payload({ gatewayHost: evil }), adminToken);
    assert(r.status === 400, `${evil} يجب أن يُرفض (SSRF)، عاد ${r.status}`);
    assert(r.data.error === 'PRIVATE_ADDRESS_REQUIRED', `رمز غير متوقع: ${r.data.error}`);
  }

  // عنوان خاص لكنه ليس بوابة مسجّلة
  const unknown = await api('POST', '/api/admin/dinstar/sms/send', payload({ gatewayHost: '10.0.0.99' }), adminToken);
  assert(unknown.status === 404, `بوابة غير مسجّلة يجب أن تعيد 404، عادت ${unknown.status}`);

  return `الإرسال عبر ${host} · رفض العنوان العام وعنوان البيانات الوصفية · 404 لغير المسجّل`;
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

  return `403 للإداري · تصويت ${before}→${after} · ALREADY_VOTED للمكرر`;
});

console.log(`\n${'─'.repeat(60)}`);
if (fail === 0) {
  console.log(`🎉 نجحت كل الفحوص: ${pass}/${pass} — التطبيق والخادم واللوحة على قاعدة واحدة`);
  process.exit(0);
} else {
  console.log(`⚠️  ${pass} نجح · ${fail} فشل`);
  process.exit(1);
}
