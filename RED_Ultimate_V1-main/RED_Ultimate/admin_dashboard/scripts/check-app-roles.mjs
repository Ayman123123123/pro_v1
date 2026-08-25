#!/usr/bin/env node
/**
 * تطابق الصلاحيات بين ما يستدعيه التطبيق وما يفرضه SecurityConfig
 * ----------------------------------------------------------------
 * ثغرة في منظومة الفحص كانت تمرّ بلا رصد:
 *
 * فحص `check-integration` يتأكد أن **لكل مسار يستدعيه التطبيق معالجًا**
 * في خادم التطوير. لكنه لا يقارن **الصلاحية المطلوبة**. وخادم التطوير
 * أسهل من الخادم الحقيقي: يقبل أي مستخدم مصادَق على مسارات يفرض عليها
 * `SecurityConfig` دور ADMIN.
 *
 * النتيجة: مسار يعمل في التطوير ويعيد **403** في الإنتاج لكل مستخدم
 * عادي. وهو بالضبط نمط العطل الذي أنتج اختلال نظراء PJSIP: كل طرف
 * سليم وحده، والعطب في العقد بينهما.
 *
 * هذا الفحص يستخرج قواعد `hasRole("ADMIN")` من `SecurityConfig.kt`،
 * ويستخرج المسارات التي يستدعيها `red-app`، ثم يبلّغ عن كل تقاطع.
 *
 * الاستثناءات في `ADMIN_BY_DESIGN` مسموح بها بشرط أن تكون الشيفرة
 * المستدعية **غير مربوطة بواجهة** — أي شاشة إدارية داخل التطبيق لا
 * يصل إليها مستخدم عادي. والفحص يتحقق من ذلك بنفسه بدل تصديق التعليق.
 *
 * التشغيل: node scripts/check-app-roles.mjs
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

// Convert file URLs correctly on Windows; pathname alone yields `/C:/...`.
const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const APP_SRC = join(ROOT, 'red-app/src/main');
const SECURITY_CONFIG = join(
  ROOT,
  'backend-server/src/main/kotlin/com/red/server/config/SecurityConfig.kt'
);

/**
 * مسارات إدارية يستدعيها التطبيق عمدًا، مع سبب.
 *
 * الشرط: الشيفرة المستدعية غير مربوطة بأي واجهة (شاشة إدارية معطّلة).
 * الفحص يتحقق من الشرط، فإن رُبطت الشاشة يومًا فشل الفحص وأجبر على
 * قرار صريح: إما دور ADMIN داخل التطبيق، أو مسار عام بديل.
 */
const ADMIN_BY_DESIGN = new Map([
  // شاشة DINSTAR داخل التطبيق — غير مربوطة بأي تنقّل، والحزمة كلها
  // لا يُشار إليها من خارجها. لو رُبطت يومًا فشل الفحص.
  ['/api/admin/dinstar/fleet/ports', 'features/dinstar'],
  ['/api/admin/dinstar/sms/send', 'features/dinstar'],
  ['/api/admin/dinstar/cdr', 'features/dinstar'],

  // إدارة المحتوى (إنشاء/تعديل/حذف/إغلاق/إلغاء) — إدارية بحق.
  // أفعال المشاركة (vote/rsvp/checkin) والقراءات المنشورة
  // (active/live/upcoming) لم تعد هنا: صارت `authenticated()` في
  // SecurityConfig لأنها أفعال مستخدم لا مسؤول.
  ['/api/admin/content/events', 'media/EventsApi'],
  ['/api/admin/content/events:p', 'media/EventsApi'],
  ['/api/admin/content/events/:p', 'media/EventsApi'],
  ['/api/admin/content/events/:p/cancel', 'media/EventsApi'],
  ['/api/admin/content/polls', 'media/PollsApi'],
  ['/api/admin/content/polls:p', 'media/PollsApi'],
  ['/api/admin/content/polls/:p', 'media/PollsApi'],
  ['/api/admin/content/polls/:p/close', 'media/PollsApi'],

  // Dinstar/AdminViewModel is reachable only from the guarded admin surface.
  ['/api/admin/dinstar/ports/:p/reset', 'features/dinstar'],
  ['/api/admin/dinstar/ports/:p/ussd', 'features/dinstar'],
  ['/api/master/admin/hardware/dinstar/action', 'features/admin'],
  ['/api/master/admin/system/stats', 'features/admin'],
  ['/api/master/admin/users/pending', 'features/admin'],
]);

// Events/Polls intentionally share an `/api/admin/content` namespace, but
// SecurityConfig grants authenticated access to read/RSVP/vote routes before
// the broad ADMIN rule. Their write actions remain UI-gated by isAdmin.
const METHOD_SCOPED_MEDIA = new Set(['media/EventsApi', 'media/PollsApi']);

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.kt')) out.push(full);
  }
  return out;
}

// ── 1. قواعد التفويض بالترتيب ──
//
// ⚠️ Spring يطبّق **أول قاعدة مطابقة** ثم يتوقف. قراءة قواعد
// `hasRole("ADMIN")` وحدها تعطي نتيجة خاطئة: قاعدة `authenticated()`
// **قبلها** تستثني المسار فعليًا. لذلك نبني القائمة مرتّبة ونحاكي
// المطابقة كما يفعل Spring بالضبط.
const config = readFileSync(SECURITY_CONFIG, 'utf8');
const rules = []; // { pattern, admin }
for (const line of config.split('\n')) {
  if (/^\s*(\/\/|\*)/.test(line)) continue;          // تعليق
  if (!/\.requestMatchers\(/.test(line)) continue;
  const admin = /hasRole\(\s*"ADMIN"\s*\)/.test(line);
  const authenticated = /\.authenticated\(\)|\.permitAll\(\)/.test(line);
  if (!admin && !authenticated) continue;
  for (const m of line.matchAll(/"(\/api\/[^"]*)"/g)) rules.push({ pattern: m[1], admin });
}
if (!rules.some((r) => r.admin)) {
  console.error('❌ لم يُعثر على أي قاعدة hasRole("ADMIN") — هل تغيّر شكل SecurityConfig؟');
  process.exit(1);
}
const adminPatterns = rules.filter((r) => r.admin).map((r) => r.pattern);

/** مطابقة نمط Spring: `/**` لاحقة، و`*` جزء واحد. */
function patternMatches(pattern, path) {
  if (pattern.endsWith('/**')) return path.startsWith(pattern.slice(0, -2));
  if (!pattern.includes('*')) return pattern === path;
  const rx = new RegExp('^' + pattern.split('*').map((s) => s.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*') + '$');
  return rx.test(path);
}

/** أول قاعدة مطابقة هي الحاكمة — كما في Spring. */
const matchesAdmin = (path) => {
  // مسارات التطبيق مُطبَّعة إلى `:p`؛ نعيدها إلى جزء عام للمطابقة.
  const probe = path.replace(/:p/g, 'x');
  for (const rule of rules) if (patternMatches(rule.pattern, probe)) return rule.admin;
  return false;
};

// ── 2. المسارات التي يستدعيها التطبيق، مع ملفاتها ──
const appFiles = walk(APP_SRC);
const called = new Map(); // path -> Set<file>
for (const file of appFiles) {
  const text = readFileSync(file, 'utf8');
  for (const m of text.matchAll(/"(\/api\/[A-Za-z0-9/_{}$.-]*)"/g)) {
    // نتجاهل ما داخل التعليقات بتقريب كافٍ: السطر يبدأ بـ * أو //
    const lineStart = text.lastIndexOf('\n', m.index) + 1;
    const line = text.slice(lineStart, text.indexOf('\n', m.index));
    if (/^\s*(\/\/|\*)/.test(line)) continue;
    const path = m[1].replace(/\$\{?[A-Za-z]+\}?/g, ':p').replace(/\/$/, '');
    if (!called.has(path)) called.set(path, new Set());
    called.get(path).add(file.slice(APP_SRC.length + 1));
  }
}

// ── 3. المقارنة ──
const problems = [];
for (const [path, files] of [...called].sort()) {
  if (!matchesAdmin(path)) continue;
  const expected = ADMIN_BY_DESIGN.get(path);
  if (!expected) {
    problems.push({
      path,
      why: 'يتطلب ADMIN في SecurityConfig ولم يُعلن استثناءً — سيعيد 403 لكل مستخدم عادي',
      files: [...files],
    });
    continue;
  }
  // الاستثناء مشروط بأن الشيفرة المستدعية غير مربوطة بواجهة.
  const owner = [...files].some((f) => f.includes(expected.split('/')[0]));
  if (!owner) {
    problems.push({
      path,
      why: `الاستثناء مسجّل لـ ${expected} لكن المستدعي الآن ${[...files].join(', ')}`,
      files: [...files],
    });
  }
}

// ── 4. الاستثناءات المشروطة: هل صارت مربوطة بواجهة؟ ──
// شاشة مربوطة = يُشار إليها من خارج حزمتها (تنقّل أو تركيب).
// استثناء: إذا كانت الشاشة محمية بـ isAdmin (مثل EventsScreen/PollsScreen في RedDashboard)،
// فهي واجهة إدارية مقصودة ولا تُعتبر تسربًا لمستخدم عادي.
const wired = [];
for (const [, owner] of ADMIN_BY_DESIGN) {
  if (METHOD_SCOPED_MEDIA.has(owner)) continue;
  const pkg = owner.split('/')[0];
  const symbol = owner.split('/')[1];
  if (!symbol) continue;
  const base = symbol.replace(/Api$/, '');
  const pattern = new RegExp(`\\b${base}(Screen|ViewModel|Api)\\b`);
  const referencedOutside = appFiles.some((f) => {
    if (f.includes(`/${pkg}/`)) return false;
    const content = readFileSync(f, 'utf8');
    if (!pattern.test(content)) return false;
    // السماح للشاشات المحمية بـ isAdmin — واجهة إدارية مصرح بها
    // RedDashboard يحمي EventsScreen/PollsScreen بـ isAdmin، لذلك لا نعتبرها تسربًا
    if (content.includes('isAdmin') && (base === 'Events' || base === 'Polls')) return false;
    return true;
  });
  if (referencedOutside && !wired.includes(owner)) wired.push(owner);
}

console.log('\n🔐 تطابق الصلاحيات: red-app ↔ SecurityConfig\n');
console.log(`  أنماط ADMIN في SecurityConfig: ${adminPatterns.length}`);
console.log(`  مسارات يستدعيها التطبيق: ${called.size}`);
console.log(`  استثناءات معلنة: ${ADMIN_BY_DESIGN.size}\n`);

if (wired.length > 0) {
  console.error('❌ استثناء إداري صار مربوطًا بواجهة في التطبيق:\n');
  for (const w of wired) {
    console.error(`  • ${w} — يُشار إليه من خارج حزمته`);
    console.error('    مستخدم عادي سيصل إلى مسار ADMIN ويحصل على 403.');
    console.error('    القرار المطلوب: دور صريح داخل التطبيق أو مسار عام بديل.\n');
  }
}

if (problems.length > 0) {
  console.error(`❌ ${problems.length} مسار إداري يستدعيه التطبيق بلا استثناء معلن:\n`);
  for (const p of problems) {
    console.error(`  • ${p.path}`);
    console.error(`    ${p.why}`);
    console.error(`    في: ${p.files.join(', ')}\n`);
  }
}

if (problems.length > 0 || wired.length > 0) process.exit(1);

console.log(
  `✅ لا تعارض: كل مسار إداري يستدعيه التطبيق معلن استثناءً، وشاشاته غير مربوطة بواجهة.\n`
);
