import { cn } from '@/utils';

interface DateRangePickerProps {
  from?: string;
  to?: string;
  onChange?: (from: string, to: string) => void;
  className?: string;
}

export function DateRangePicker({ from = '', to = '', onChange, className }: DateRangePickerProps) {
  return (
    <div className={cn('yn-daterange', className)}>
      <input type="date" className="yn-input" value={from} onChange={(e) => onChange?.(e.target.value, to)} />
      <span>–</span>
      <input type="date" className="yn-input" value={to} onChange={(e) => onChange?.(from, e.target.value)} />
    </div>
  );
}

export default DateRangePicker;
