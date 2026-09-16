import type { ReactNode } from 'react';
import { cn } from '@/utils';

interface CardProps {
  title?: string;
  subtitle?: string;
  children: ReactNode;
  className?: string;
  headerAction?: ReactNode;
}

export function Card({ title, subtitle, children, className, headerAction }: CardProps) {
  return (
    <div className={cn('yn-card yn-card-liquid yn-glass', className)}>
      {(title || subtitle) && (
        <div className="yn-card-header">
          <div>
            {title && <h3 className="yn-card-title">{title}</h3>}
            {subtitle && <p className="yn-card-subtitle">{subtitle}</p>}
          </div>
          {headerAction}
        </div>
      )}
      <div className="yn-card-content">
        {children}
      </div>
    </div>
  );
}