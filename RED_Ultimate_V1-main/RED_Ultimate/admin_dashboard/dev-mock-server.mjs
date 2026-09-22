#!/usr/bin/env node
/**
 * dev-mock-server.mjs — خادم API محاكي مستقل (اختياري)
 * -----------------------------------------------------
 * الطريقة الأساسية اليوم: الـ middleware المدمج في vite.config.ts — يعمل
 * تلقائياً حين لا يجد خادماً حقيقياً على 8088 (منطق مشترك: dev-mock-api.mjs).
 *
 * هذا الملف لمن يريد تشغيل المحاكي كعملية مستقلة (مثلاً خلف Nginx):
 *   npm run mock   ← يستمع على 8088
 * ⚠️ للتطوير فقط — مع Docker الحقيقي شغّل docker compose بدلاً منه (نفس المنفذ).
 */
import http from 'node:http';
import { mockApiMiddleware } from './dev-mock-api.mjs';

const PORT = Number(process.env.MOCK_PORT || 8088);
const handler = mockApiMiddleware();

const server = http.createServer(async (req, res) => {
  // المحاكي يخدم كل المسارات مباشرة (بلا بوابة حقيقية أمامه)
  await handler(req, res, () => {
    res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 'NOT_FOUND', message: 'مسار غير موجود في المحاكي' }));
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`✅ Mock API مستقل جاهز على http://127.0.0.1:${PORT} — الدخول: admin / admin123`);
});
