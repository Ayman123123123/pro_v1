import { createFileRoute } from '@tanstack/react-router';
import Approvals from '@/pages/Approvals';

// ✅ 2026-09-22: صفحة Approvals من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/approvals')({
  component: Approvals,
});
