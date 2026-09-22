import { createFileRoute } from '@tanstack/react-router';
import Announcements from '@/pages/Announcements';

// ✅ 2026-09-22: صفحة Announcements من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/announcements')({
  component: Announcements,
});
