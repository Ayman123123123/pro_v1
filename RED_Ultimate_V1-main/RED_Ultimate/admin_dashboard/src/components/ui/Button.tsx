import type { ReactNode, MouseEvent } from 'react';
import { cn } from '@/utils';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'icon';

interface ButtonProps {
  children: ReactNode;
  className?: string;
  variant?: Variant;
  size?: Size;
  onClick?: (e: MouseEvent<HTMLButtonElement>) => void;
  disabled?: boolean;
  type?: 'button' | 'submit' | 'reset';
}

const variantCls: Record<Variant, string> = {
  primary: 'yn-btn yn-btn-primary',
  secondary: 'yn-btn yn-btn-secondary',
  ghost: 'yn-btn yn-btn-ghost',
  danger: 'yn-btn yn-btn-danger',
};

export function Button({ children, className, variant = 'primary', size = 'md', onClick, disabled, type = 'button' }: ButtonProps) {
  return (
    <button type={type} disabled={disabled} onClick={onClick} className={cn(variantCls[variant], size === 'sm' && 'yn-btn-sm', size === 'icon' && 'yn-btn-icon', className)}>
      {children}
    </button>
  );
}

export default Button;
