import { cn } from '@/utils';

interface SelectProps {
  value: string;
  onChange?: (v: string) => void;
  options?: { value: string; label: string }[];
  placeholder?: string;
  className?: string;
}

export function Select({ value, onChange, options = [], placeholder, className }: SelectProps) {
  return (
    <select className={cn('yn-input', className)} value={value} onChange={(e) => onChange?.(e.target.value)}>
      {placeholder && <option value="">{placeholder}</option>}
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

export default Select;
