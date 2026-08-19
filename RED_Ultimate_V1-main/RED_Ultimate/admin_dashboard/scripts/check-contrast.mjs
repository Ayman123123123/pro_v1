#!/usr/bin/env node
/**
 * حارس التباين اللوني — WCAG 2.1
 *
 * يقرأ متغيّرات `:root` من `src/styles.css` (لا قيمًا منسوخة هنا، حتى لا
 * يفحص الحارس نسخته الخاصة بدل الملف الفعلي) ويقيس نسبة التباين لكل زوج
 * نص/خلفية مستعمل فعلًا في الواجهة.
 *
 * سبب وجوده: كانت اللوحة تحوي عيبين مقيسين — النص الباهت على السطح عند
 * 2.91:1 والنص الأبيض على الأخضر عند 2.16:1، وكلاهما دون حدّ AA (4.5:1)
 * للنص العادي. أُصلحا، وهذا الحارس يمنع عودتهما بصمت.
 *
 * يقابله في الأندرويد: red-app/src/test/java/.../ui/theme/ColorContrastTest.kt
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const css = readFileSync(join(root, 'src/styles.css'), 'utf8');

/** يستخرج متغيّرات --yns-* من كتلة :root في الملف الفعلي. */
function readTokens(source) {
  const block = source.slice(source.indexOf(':root {'), source.indexOf('/* Spacing System'));
  const tokens = {};
  for (const m of block.matchAll(/--(yns-[\w-]+):\s*(#[0-9A-Fa-f]{6})/g)) tokens[m[1]] = m[2];
  return tokens;
}

const srgb = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);

function luminance(hex) {
  const h = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map((i) => srgb(parseInt(h.slice(i, i + 2), 16) / 255));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const T = readTokens(css);

// الحد الأدنى: 4.5 للنص العادي، 3.0 للنص الكبير/العناصر الرسومية (WCAG 1.4.3 / 1.4.11)
const PAIRS = [
  ['النص الأساسي على الخلفية', 'yns-text', 'yns-navy', 4.5],
  ['النص الأساسي على السطح', 'yns-text', 'yns-surface', 4.5],
  ['النص الثانوي على الخلفية', 'yns-text-secondary', 'yns-navy', 4.5],
  ['النص الثانوي على السطح', 'yns-text-secondary', 'yns-surface', 4.5],
  ['النص الباهت على الخلفية', 'yns-text-muted', 'yns-navy', 4.5],
  ['النص الباهت على السطح', 'yns-text-muted', 'yns-surface', 4.5],
  ['الأخضر على الخلفية', 'yns-green', 'yns-navy', 4.5],
  ['الذهب على الخلفية', 'yns-gold', 'yns-navy', 4.5],
  ['الأزرق على الخلفية', 'yns-blue', 'yns-navy', 4.5],
  ['الخطأ على الخلفية', 'yns-error', 'yns-navy', 4.5],
  ['نص الزر الأخضر', 'yns-dark', 'yns-green', 4.5],
  ['نص الزر الذهبي', 'yns-dark', 'yns-gold', 4.5],
  ['الحدّ على الخلفية', 'yns-border', 'yns-navy', 1.0],
];

console.log('\n🎨 حارس التباين اللوني (WCAG 2.1) — لوحة الإدارة\n');

let failed = 0;
const missing = [];
for (const [label, fg, bg, min] of PAIRS) {
  if (!T[fg] || !T[bg]) { missing.push(`${fg}/${bg}`); continue; }
  const r = ratio(T[fg], T[bg]);
  const ok = r >= min;
  if (!ok) failed++;
  const grade = r >= 7 ? 'AAA' : r >= 4.5 ? 'AA' : r >= 3 ? 'كبير فقط' : 'راسب';
  console.log(
    `  ${ok ? '✅' : '❌'} ${label.padEnd(26)} ${r.toFixed(2).padStart(5)}:1  ` +
    `(الحد ${min})  ${grade}  ${T[fg]}/${T[bg]}`,
  );
}

if (missing.length) {
  console.error(`\n❌ متغيّرات غير موجودة في :root — ${missing.join('، ')}`);
  console.error('   إن أُعيدت تسميتها فحدِّث هذا الحارس، ولا تحذف الفحص.\n');
  process.exit(1);
}

if (failed > 0) {
  console.error(`\n❌ ${failed} زوجًا دون الحد المطلوب.`);
  console.error('   عدِّل القيمة في :root حتى تبلغ الحد — لا تُخفِّض الحد.\n');
  process.exit(1);
}

console.log(`\n✅ تباين سليم: ${PAIRS.length} زوجًا، كلها تبلغ الحد أو تتجاوزه.\n`);
