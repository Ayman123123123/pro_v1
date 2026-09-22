'use client';

// ✅ FIX 2026-09-22: cmdk@1.1.1 exports CommandRoot/Input/... directly (not pkg/Input)
import {
  CommandRoot,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandSeparator,
} from 'cmdk';
import { cn } from '@/utils/cn';
import type { HTMLAttributes } from 'react';

const Command = CommandRoot;
/** Shortcut غير موجود في cmdk@1.1.1 — fallback بسيط */
function CommandShortcut({ className, ...props }: HTMLAttributes<HTMLSpanElement>) {
  return (
    <span className={cn('ml-auto text-xs tracking-widest text-muted-foreground', className)} {...props} />
  );
}

export { Command, CommandInput, CommandList, CommandEmpty, CommandGroup, CommandItem, CommandSeparator, CommandShortcut };