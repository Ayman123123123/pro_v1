import { createFileRoute } from '@tanstack/react-router';
import Backups from '@/pages/Backups';

// ✅ 2026-09-22: صفحة Backups من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/backups')({
  component: Backups,
});
