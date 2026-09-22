import { useState, type ReactNode, type MouseEvent } from 'react';
import { cn } from '@/utils';

function Root({ children, className }: { children: ReactNode; className?: string }) {
  const [open, setOpen] = useState(false);
  return <div className={cn('yn-menu', className)} data-open={open} onClick={() => setOpen((v) => !v)}>{children}</div>;
}

function Trigger({ children, asChild }: { children: ReactNode; asChild?: boolean }) {
  void asChild;
  return <div className="yn-menu-trigger">{children}</div>;
}

function Content({ children, align }: { children: ReactNode; align?: string }) {
  void align;
  return <div className="yn-menu-content">{children}</div>;
}

function Item({ children, onClick }: { children: ReactNode; onClick?: (e: MouseEvent<HTMLDivElement>) => void }) {
  return <div className="yn-menu-item" onClick={onClick}>{children}</div>;
}

export const DropdownMenu = Object.assign(Root, { Trigger, Content, Item });
export default DropdownMenu;
