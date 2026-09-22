import { createFileRoute } from '@tanstack/react-router';
import MasterOverview from '@/pages/MasterOverview';

// ✅ 2026-09-22: صفحة MasterOverview من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/overview')({
  component: MasterOverview,
});
