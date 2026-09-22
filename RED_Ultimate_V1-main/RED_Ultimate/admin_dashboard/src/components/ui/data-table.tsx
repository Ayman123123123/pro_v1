import type { ReactNode } from 'react';
import { cn } from '@/utils';

interface Column<T = any> {
  key: string;
  title: ReactNode;
  cell?: (row: T) => ReactNode;
  render?: (row: T) => ReactNode;
}

interface DataTableProps<T = any> {
  columns: Column<T>[];
  data: T[];
  isLoading?: boolean;
  rowKey?: string;
  emptyMessage?: string;
  className?: string;
}

export function DataTable<T = any>({ columns, data, isLoading, rowKey = 'id', emptyMessage = 'No data', className }: DataTableProps<T>) {
  if (isLoading) return <div className={cn('yn-table-loading', className)}>Loading…</div>;
  if (!data || data.length === 0) return <div className={cn('yn-table-empty', className)}>{emptyMessage}</div>;
  return (
    <div className={cn('yn-table-wrap', className)}>
      <table className="yn-table">
        <thead>
          <tr>{columns.map((c) => <th key={c.key}>{c.title}</th>)}</tr>
        </thead>
        <tbody>
          {data.map((row: any, i: number) => (
            <tr key={String(row?.[rowKey] ?? i)}>
              {columns.map((c) => <td key={c.key}>{c.cell ? c.cell(row) : c.render ? c.render(row) : String(row?.[c.key] ?? '')}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default DataTable;
