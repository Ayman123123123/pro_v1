import { createFileRoute } from '@tanstack/react-router';
import AuditLog from '@/pages/AuditLog';

// ✅ 2026-09-22: صفحة AuditLog من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/audit')({
  component: AuditLog,
});
