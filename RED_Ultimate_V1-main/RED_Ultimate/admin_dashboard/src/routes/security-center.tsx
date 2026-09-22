import { createFileRoute } from '@tanstack/react-router';
import SecurityCenter from '@/pages/SecurityCenter';

// ✅ 2026-09-22: صفحة SecurityCenter من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/security-center')({
  component: SecurityCenter,
});
