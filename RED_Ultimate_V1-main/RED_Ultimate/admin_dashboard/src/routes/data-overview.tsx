import { createFileRoute } from '@tanstack/react-router';
import DataOverview from '@/pages/DataOverview';

// ✅ 2026-09-22: صفحة DataOverview من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/data-overview')({
  component: DataOverview,
});
