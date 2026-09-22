import { createFileRoute } from '@tanstack/react-router';
import PostsManagement from '@/pages/PostsManagement';

// ✅ 2026-09-22: صفحة PostsManagement من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/posts')({
  component: PostsManagement,
});
