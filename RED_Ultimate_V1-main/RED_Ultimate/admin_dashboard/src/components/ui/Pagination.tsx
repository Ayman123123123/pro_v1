import { cn } from '@/utils';

interface PaginationProps {
  page: number;
  totalPages: number;
  onChange?: (page: number) => void;
  className?: string;
}

export function Pagination({ page, totalPages, onChange, className }: PaginationProps) {
  if (totalPages <= 1) return null;
  return (
    <div className={cn('yn-pagination', className)}>
      <button className="yn-btn yn-btn-ghost" disabled={page <= 0} onClick={() => onChange?.(page - 1)}>‹ Prev</button>
      <span className="yn-pagination-info">{page + 1} / {totalPages}</span>
      <button className="yn-btn yn-btn-ghost" disabled={page + 1 >= totalPages} onClick={() => onChange?.(page + 1)}>Next ›</button>
    </div>
  );
}

export default Pagination;
