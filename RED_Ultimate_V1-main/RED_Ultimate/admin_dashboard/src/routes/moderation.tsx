import { createFileRoute } from '@tanstack/react-router';
import { ModerationPage } from '@/pages/Moderation';

export const Route = createFileRoute('/moderation')({
  component: ModerationPage,
});