import { createFileRoute } from '@tanstack/react-router';
import GroupsManagement from '@/pages/GroupsManagement';

// ✅ 2026-09-22: صفحة GroupsManagement من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/groups')({
  component: GroupsManagement,
});
