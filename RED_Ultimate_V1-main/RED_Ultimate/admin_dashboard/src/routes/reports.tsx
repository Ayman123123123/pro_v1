import { createFileRoute } from '@tanstack/react-router';
import Reports from '@/pages/Reports';

// ✅ 2026-09-22: صفحة Reports من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/reports')({
  component: Reports,
});
