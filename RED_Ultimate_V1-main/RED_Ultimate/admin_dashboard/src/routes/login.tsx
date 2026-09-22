import { createFileRoute, redirect } from '@tanstack/react-router';
import { LoginPage } from '@/pages/Login';
import { authStore } from '@/api';

// ✅ 2026-09-22: حارس عكسي — الموثَّق يُوجَّه مباشرة إلى اللوحة
export const Route = createFileRoute('/login')({
  beforeLoad: () => {
    if (authStore.isAuthenticated()) {
      throw redirect({ to: '/dashboard' });
    }
  },
  component: LoginPage,
});
