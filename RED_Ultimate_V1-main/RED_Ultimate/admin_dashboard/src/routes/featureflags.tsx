import { createFileRoute } from '@tanstack/react-router';
import FeatureFlags from '@/pages/FeatureFlags';

// ✅ 2026-09-22: صفحة FeatureFlags من الجيل الأول — أُدمجت في الراوتر الحديث
// (توحيد المعمارية: كل الميزات تحت هيكل TanStack Router الواحد)
export const Route = createFileRoute('/featureflags')({
  component: FeatureFlags,
});
