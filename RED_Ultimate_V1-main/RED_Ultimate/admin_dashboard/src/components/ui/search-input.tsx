import { cn } from '@/utils';

interface Option { value: string; label: string; }

interface SearchInputProps {
  value: string;
  onChange?: (v: string) => void;
  placeholder?: string;
  className?: string;
}

export function SearchInput({ value, onChange, placeholder, className }: SearchInputProps) {
  return (
    <input
      className={cn('yn-input', className)}
      value={value}
      placeholder={placeholder}
      onChange={(e) => onChange?.(e.target.value)}
    />
  );
}

interface SelectProps {
  value: string;
  onChange?: (v: string) => void;
  options?: Option[];
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

export default SearchInput;
