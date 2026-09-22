'use client';

import * as React from 'react';
import { cn } from '@/utils/cn';

// ✅ FIX 2026-09-22: ElementRef<typeof Fragment> كان يرجع FragmentInstance
// غير متوافق مع ref عنصر div → HTMLDivElement مباشرة
const Separator = React.forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<'div'> & { orientation?: 'horizontal' | 'vertical'; decorative?: boolean }
>(({ className, orientation = 'horizontal', decorative = true, ...props }, ref) => (
  <div
    ref={ref}
    role={decorative ? 'none' : 'separator'}
    aria-orientation={decorative ? undefined : orientation}
    className={cn(
      'shrink-0 bg-border',
      orientation === 'horizontal' ? 'h-[1px] w-full' : 'h-full w-[1px]',
      className
    )}
    {...props}
  />
));
Separator.displayName = 'Separator';

export { Separator };