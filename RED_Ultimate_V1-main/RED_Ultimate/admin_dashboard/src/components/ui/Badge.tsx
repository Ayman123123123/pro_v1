import type { ReactNode } from 'react';
import { cn } from '@/utils';

interface BadgeProps {
  children: ReactNode;
  className?: string;
  variant?: 'green' | 'gold' | 'blue' | 'error' | string;
}

export function Badge({ children, className, variant = 'green' }: BadgeProps) {
  return <span className={cn('yn-badge', `yn-badge-${variant}`, className)}>{children}</span>;
}

export default Badge;
