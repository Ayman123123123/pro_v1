#!/usr/bin/env node
/**
 * فاحص عقد API بين لوحة الإدارة والخادم الخلفي
 * -------------------------------------------------
 * يقرأ مسارات apiFetch في admin_dashboard/src ويربطها بمخططات
 * Spring في backend-server/src/main/kotlin، ويكشف أي مسار تطلبه
 * الواجهة غير موجود في الخادم (أو بطريقة خاطئة).
 *
 * التشغيل:  node scripts/check-api-contract.mjs
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const DASHBOARD_SRC = join(ROOT, 'src');
const BACKEND_SRC = join(ROOT, '../backend-server/src/main/kotlin');

function walk(dir, ext, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...walk(full, ext));
    else if (full.endsWith(ext)) out.push(full);
  }
  return out;
}

// 1) Backend route table: verb -> path (expanded from class-level + method-level)
const routes = new Map(); // `${verb} ${path}` -> file
for (const file of walk(BACKEND_SRC, '.kt')) {
  const text = readFileSync(file, 'utf8');
  const classMatch = text.match(/@RequestMapping\(\s*"([^"]*)"\s*\)/);
  const base = classMatch ? classMatch[1] : '';
  const re = /@(Get|Post|Put|Patch|Delete|Request)Mapping\(\s*(?:"([^"]*)"|value\s*=\s*"([^"]*)"|consumes\s*=\s*\[[^\]]*\])|@(Get|Post|Put|Patch|Delete|Request)Mapping\b(?!\s*\()/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const verbName = m[1] || m[4];
    const verb = verbName === 'Get' ? 'GET' : verbName === 'Post' ? 'POST' : verbName === 'Put' ? 'PUT' : verbName === 'Patch' ? 'PATCH' : verbName === 'Delete' ? 'DELETE' : 'ANY';
    const path = (m[2] || m[3] || '');
    routes.set(`${verb} ${base}${path}`, relative(BACKEND_SRC, file));
  }
}

// 2) Normalize dashboard path templates like /api/x/${id}/y?z= into /api/x/{id}/y
function normalize(p) {
  return p
    .replace(/\$\{[^}]+\}/g, '{param}')
    .split('?')[0]
    .replace(/\/+/g, '/');
}

function findBackendRoute(verb, path) {
  const direct = routes.get(`${verb} ${path}`);
  if (direct) return direct;
  // try ANY
  const any = routes.get(`ANY ${path}`);
  if (any) return any;
  // try {param} wildcards
  const segments = path.split('/').filter(Boolean);
  outer:
  for (const [key, file] of routes) {
    const [v, p] = key.split(' ');
    if (v !== verb && v !== 'ANY') continue;
    const keySegs = p.split('/').filter(Boolean);
    if (keySegs.length !== segments.length) continue;
    for (let i = 0; i < segments.length; i++) {
      const ks = keySegs[i];
      if (ks.startsWith('{') || ks === segments[i]) continue;
      continue outer;
    }
    return file;
  }
  return null;
}

// 3) Collect dashboard calls
const calls = [];
for (const file of walk(DASHBOARD_SRC, '.jsx')) calls.push(...collect(file));
for (const file of walk(DASHBOARD_SRC, '.tsx')) calls.push(...collect(file));
for (const file of walk(DASHBOARD_SRC, '.ts')) calls.push(...collect(file));

function collect(file) {
  const text = readFileSync(file, 'utf8');
  const found = [];
  const re = /(?:apiFetch|fetch)\(\s*[`'"]([^`'"]+)[`'"]/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const raw = m[1];
    if (!raw.startsWith('/')) continue; // relative templates or objects
    // extract method if statically visible on the same statement
    const after = text.slice(m.index, m.index + 300);
    const methodMatch = after.match(/method\s*:\s*['"]([A-Z]+)['"]/);
    found.push({ file: relative(ROOT, file), raw, method: methodMatch ? methodMatch[1] : 'GET', normalized: normalize(raw) });
  }
  return found;
}

// 4) Report
let bad = 0;
console.log(`فاحص عقد API — ${calls.length} استدعاء واجهة مقابل ${routes.size} مسار خادم\n`);
for (const call of calls.sort((a, b) => a.raw.localeCompare(b.raw))) {
  const hit = findBackendRoute(call.method, call.normalized);
  if (hit) {
    console.log(`  ✅ ${call.method.padEnd(6)} ${call.raw}  (${call.file})`);
  } else {
    bad++;
    console.log(`  ❌ ${call.method.padEnd(6)} ${call.raw}  (${call.file})  — لا يوجد مسار مطابق في الخادم`);
  }
}
console.log(bad === 0 ? '\nالنتيجة: العقد سليم ✅' : `\nالنتيجة: ${bad} مسار مكسور ❌`);
process.exit(bad === 0 ? 0 : 1);
