import { createFileRoute } from '@tanstack/react-router';
import CallHistory from '@/pages/CallHistory';

// ✅ 2026-09-22: صفحة CallHistory من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/calls')({
  component: CallHistory,
});
