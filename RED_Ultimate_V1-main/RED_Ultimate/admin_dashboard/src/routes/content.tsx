import { createFileRoute } from '@tanstack/react-router';
import ContentManagement from '@/pages/ContentManagement';

// ✅ 2026-09-22: صفحة ContentManagement من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/content')({
  component: ContentManagement,
});
