#!/usr/bin/env node
/**
 * حارس قواعد الواجهة — يمنع التراجع عن المكاسب المثبتة
 * ----------------------------------------------------------------
 * يفحص شيفرة لوحة الإدارة مقابل القواعد التي يفرضها المشروع فعليًا،
 * ويفشل ببنية غير صفرية عند أي مخالفة، تمامًا مثل check-api-contract.mjs.
 *
 * القواعد المطبَّقة:
 *  1) Local-first: ممنوع تحميل أي أصل (خط/سكربت/نمط) من نطاق خارجي.
 *     السبب: nginx.conf يفرض `default-src 'self'` و`font-src 'self' data:`،
 *     فأي مرجع خارجي يُحجب في الإنتاج ويكسر الهوية البصرية بصمت.
 *  2) ممنوع setInterval الخام للاستطلاع — يجب استخدام usePolling الذي
 *     يتوقف عند إخفاء التبويب وانقطاع الشبكة، حفاظًا على الخادم
 *     وعلى استعلامات بوابة DINSTAR الحقيقية.
 *  3) لا أسرار مكتوبة في الشيفرة (كلمات مرور/مفاتيح ثابتة).
 *  4) تجديد الرمز في api.ts يجب أن يبقى محروسًا بحارس تزامن.
 *     السبب: الخادم يدوّر رمز التجديد ويعتبر إعادة استعماله سرقةً
 *     فيُبطل كل جلسات الحساب. طلبان متزامنان يصطدمان بـ401 معًا
 *     يُجدّدان بالرمز نفسه ⇒ طرد فوري للمسؤول.
 *
 * التشغيل:  node scripts/check-frontend-guards.mjs
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const SRC = join(ROOT, 'src');

/** ملفات يُسمح لها بتجاوز قاعدة بعينها، مع سبب موثّق. */
const ALLOWLIST = {
  rawInterval: new Set(['hooks/usePolling.ts']), // التنفيذ الداخلي للـ hook نفسه
};

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.(ts|tsx|css)$/.test(full)) out.push(full);
  }
  return out;
}

const violations = [];
const files = walk(SRC);

for (const file of files) {
  const rel = relative(SRC, file).split('\\').join('/');
  const text = readFileSync(file, 'utf8');
  const lines = text.split('\n');

  lines.forEach((line, index) => {
    const at = `${rel}:${index + 1}`;

    // 1) أصول خارجية (نتجاهل التعليقات وروابط الوثائق داخل النصوص التوضيحية)
    const isComment = /^\s*(\*|\/\/|\/\*)/.test(line);
    if (!isComment) {
      const external = line.match(
        /(?:@import\s+url\(|src\s*:\s*url\(|from\s+['"]|<(?:script|link)[^>]+(?:src|href)\s*=\s*['"])\s*['"]?(https?:)?\/\/[^'")\s]+/i
      );
      if (external) {
        violations.push({
          rule: 'أصل خارجي محظور (Local-first + CSP self)',
          at,
          detail: external[0].trim().slice(0, 110),
        });
      }
    }

    // 2) setInterval خام
    if (/\bsetInterval\s*\(/.test(line) && !ALLOWLIST.rawInterval.has(rel)) {
      violations.push({
        rule: 'استخدم usePolling بدل setInterval الخام',
        at,
        detail: line.trim().slice(0, 110),
      });
    }

    // 3) أسرار ثابتة
    const secret = line.match(
      /\b(password|passwd|secret|api[_-]?key|private[_-]?key|token)\b\s*[:=]\s*['"][^'"]{8,}['"]/i
    );
    if (secret && !/placeholder|example|process\.env|import\.meta\.env/i.test(line)) {
      violations.push({ rule: 'سر مكتوب داخل الشيفرة', at, detail: secret[0].slice(0, 60) });
    }
  });
}

// 4) حارس تزامن التجديد في api.ts — قاعدة ملف واحد لا سطر واحد.
{
  const apiPath = join(SRC, 'api.ts');
  const api = readFileSync(apiPath, 'utf8');
  // لا نطلب اسمًا بعينه؛ نطلب وجود وعد مشترك يُعاد استعماله ثم يُصفَّر.
  const hasSharedPromise = /\brotating\b[\s\S]*?\bfinally\s*\(/.test(api);
  const retriesOn401 = /status\s*===\s*401/.test(api);
  if (retriesOn401 && !hasSharedPromise) {
    violations.push({
      rule: 'تجديد الرمز بلا حارس تزامن (يطرد المسؤول عند طلبات متوازية)',
      at: 'api.ts',
      detail: 'يلزم وعد تجديد مشترك يُصفَّر في finally بدل تجديد لكل طلب',
    });
  }
}

if (violations.length > 0) {
  console.error(`\n❌ حارس الواجهة رصد ${violations.length} مخالفة:\n`);
  for (const v of violations) {
    console.error(`  • [${v.rule}]`);
    console.error(`    ${v.at} → ${v.detail}\n`);
  }
  process.exit(1);
}

console.log(`✅ حارس الواجهة: ${files.length} ملفًا مفحوصًا — لا مخالفات (أصول محلية، استطلاع مُدار، بلا أسرار، تجديد محروس).`);
