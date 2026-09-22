import { createFileRoute } from '@tanstack/react-router';
import Diagnostics from '@/pages/Diagnostics';

// ✅ 2026-09-22: صفحة Diagnostics من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/diagnostics')({
  component: Diagnostics,
});
