#!/usr/bin/env node
/**
 * تطابق أسماء نظراء PJSIP بين الأسطول وإعداد Asterisk
 * ----------------------------------------------------------------
 * لماذا هذا الفحص موجود:
 *
 * موزّع الأحمال يختار بوابة ويمرّر اسم نظيرها في `RED_GW`، ثم يبني
 * الـ dialplan سلسلة `PJSIP/${EXTEN}@${GW}`. فإن لم يوجد نظير بهذا
 * الاسم في `pjsip.conf` **تفشل المكالمة**، وإن وُجد نظير باسم مطابق
 * لكنه يشير إلى جهاز آخر **تخرج المكالمة من البوابة الخطأ**.
 *
 * وقد وقع الخطآن معًا: `docker-entrypoint.sh` كان يرقّم النظراء
 * بموضع العنوان في `DINSTAR_IPS` بدءًا من 0، بينما تخزّن قاعدة
 * البيانات `pjsip_endpoint` نصًّا حرًّا بدأ من 1. النتيجة:
 *
 *   192.168.11.1 → مخزَّن dinstar-gw-1 → فعليًا جهاز 192.168.11.2 ❌
 *   192.168.11.3 → مخزَّن dinstar-gw-3 → لا وجود له، تسقط المكالمة ❌
 *
 * ولم يرصده أي فحص: كل طرف سليم وحده، والخلل في العقد بينهما.
 *
 * الحل الجذري أن يُشتق الاسم من العنوان على الجانبين، فيتفقان بلا
 * ترتيب مشترك. وهذا الفحص يحرس ذلك الاتفاق.
 *
 * التشغيل: node scripts/check-asterisk-fleet.mjs
 * يتطلب: خادم التطوير على 8080، و`sh` لتوليد إعداد Asterisk.
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const BASE = process.env.RED_API_BASE || 'http://127.0.0.1:8080';
const ROOT = new URL('../..', import.meta.url).pathname;
const ENTRYPOINT = join(ROOT, 'pstn-asterisk/docker-entrypoint.sh');

let failures = 0;
const fail = (msg) => { console.error(`  ❌ ${msg}`); failures++; };
const pass = (msg) => console.log(`  ✅ ${msg}`);

async function api(path, token) {
  const res = await fetch(`${BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  return { status: res.status, data: await res.json().catch(() => ({})) };
}

console.log('\n🔗 تطابق نظراء PJSIP بين الأسطول وAsterisk\n');

// ── 1. الأسطول من الخادم ──
const login = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'younes_sovereign', password: 'RedSovereign#2026' }),
});
const token = (await login.json()).accessToken;
if (!token) { console.error('❌ تعذّر تسجيل الدخول — هل خادم التطوير يعمل؟'); process.exit(1); }

const fleet = (await api('/api/admin/dinstar/fleet', token)).data;
if (!Array.isArray(fleet) || fleet.length === 0) { console.error('❌ لا بوابات في الأسطول'); process.exit(1); }

// ── 2. توليد إعداد Asterisk من عناوين الأسطول نفسها ──
const hosts = fleet.map((g) => g.host);
const dir = mkdtempSync(join(tmpdir(), 'astfleet-'));
try {
  execFileSync('sh', [ENTRYPOINT], {
    env: {
      ...process.env,
      AMI_PASSWORD: 'checkonly',
      DINSTAR_IP: hosts[0],
      DINSTAR_IPS: hosts.join(','),
      ASTERISK_CONFIG_DIR: dir,
      RED_ASTERISK_CONFIG_ONLY: '1',
    },
    stdio: 'pipe',
  });

  const pjsip = readFileSync(join(dir, 'pjsip.conf'), 'utf8');
  const peers = new Set([...pjsip.matchAll(/^\[([A-Za-z0-9_-]+)\]/gm)].map((m) => m[1]));

  // ── 3. كل بوابة يجب أن يقابلها نظير بالاسم نفسه ──
  for (const gw of fleet) {
    const name = gw.pjsipEndpoint;
    if (!name) { fail(`${gw.host} بلا pjsipEndpoint — الـ dialplan سيعود إلى نظير التوافق`); continue; }
    if (!peers.has(name)) { fail(`${gw.host} → ${name} غير موجود في pjsip.conf — المكالمة ستفشل`); continue; }

    // الأهم: أن يشير النظير إلى العنوان نفسه لا إلى جهاز آخر.
    const identify = new RegExp(`\\[${name}\\]\\ntype=identify\\nendpoint=${name}\\nmatch=([0-9.]+)`);
    const matched = pjsip.match(identify)?.[1];
    if (matched !== gw.host) {
      fail(`${name} يشير إلى ${matched || 'لا شيء'} بينما البوابة على ${gw.host} — مكالمة من الجهاز الخطأ`);
      continue;
    }
    pass(`${gw.host} → ${name}`);
  }

  // ── 4. نظير التوافق ونظير المجهول ──
  if (!peers.has('dinstar-gateway')) {
    fail('نظير التوافق dinstar-gateway مفقود — الـ dialplan يعود إليه عند غياب RED_GW');
  } else pass('نظير التوافق dinstar-gateway موجود');

  if (!peers.has('anonymous')) {
    fail('نظير anonymous مفقود — مكالمات المجهولين تسقط في السياق الافتراضي');
  } else {
    const ctx = pjsip.match(/\[anonymous\][\s\S]*?context=([a-z-]+)/)?.[1];
    if (ctx !== 'from-untrusted') fail(`anonymous يشير إلى ${ctx} لا from-untrusted`);
    else pass('anonymous → from-untrusted (رفض صريح)');
  }

  // ── 5. الـ dialplan يرفض المجهول فعلًا ──
  const ext = readFileSync(join(ROOT, 'pstn-asterisk/extensions.conf'), 'utf8');
  const untrusted = ext.split('[from-untrusted]')[1]?.split(/^\[/m)[0] || '';
  if (!/Hangup\(21\)/.test(untrusted)) fail('from-untrusted لا يُنهي المكالمة بـ Hangup(21)');
  else pass('from-untrusted يرفض بـ Hangup(21)');
} finally {
  rmSync(dir, { recursive: true, force: true });
}

console.log();
if (failures > 0) {
  console.error(`❌ ${failures} خلل في تطابق النظراء — المكالمات ستخرج من بوابة خاطئة أو تفشل\n`);
  process.exit(1);
}
console.log(`✅ تطابق تام: ${fleet.length} بوابة، كل نظير يشير إلى عنوانه الصحيح\n`);
