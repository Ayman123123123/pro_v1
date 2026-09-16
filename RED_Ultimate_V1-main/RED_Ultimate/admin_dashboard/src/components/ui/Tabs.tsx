import { useState, type ReactNode } from 'react';
import { cn } from '@/utils';

interface Tab { value: string; label: string; }
interface TabsProps {
  tabs: Tab[];
  value?: string;
  onChange?: (v: string) => void;
  children?: ReactNode;
  className?: string;
}

export function Tabs({ tabs, value, onChange, children, className }: TabsProps) {
  const [inner, setInner] = useState(tabs[0]?.value ?? '');
  const active = value ?? inner;
  return (
    <div className={cn('yn-tabs', className)}>
      <div className="yn-tabs-list">
        {tabs.map((t) => (
          <button key={t.value} className={cn('yn-tab', active === t.value && 'yn-tab-active')} onClick={() => { setInner(t.value); onChange?.(t.value); }}>
            {t.label}
          </button>
        ))}
      </div>
      {children}
    </div>
  );
}

export default Tabs;
