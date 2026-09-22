import { cn } from '@/utils';
import { createContext, useContext, useState, type ReactNode, type FormEvent } from 'react';

interface FormCtx { values: Record<string, unknown>; set: (name: string, v: unknown) => void; }
const Ctx = createContext<FormCtx>({ values: {}, set: () => undefined });

interface FormProps {
  onSubmit: (data: Record<string, unknown>) => void;
  initialValues?: Record<string, unknown>;
  schema?: unknown;
  children: ReactNode;
  className?: string;
}

export function Form({ onSubmit, initialValues = {}, children, className }: FormProps) {
  const [values, setValues] = useState<Record<string, unknown>>(initialValues);
  const set = (name: string, v: unknown) => setValues((s) => ({ ...s, [name]: v }));
  const submit = (e: FormEvent) => { e.preventDefault(); onSubmit(values); };
  return (
    <Ctx.Provider value={{ values, set }}>
      <form onSubmit={submit} className={cn('space-y-4', className)}>
        {children}
        <div className="flex items-center justify-end gap-2 pt-4 border-t border-yn-border">
          <button type="button" onClick={() => setValues(initialValues)} className="yn-btn yn-btn-secondary">Cancel</button>
          <button type="submit" className="yn-btn yn-btn-primary">Save</button>
        </div>
      </form>
    </Ctx.Provider>
  );
}

interface FormFieldProps {
  name: string;
  label: string;
  type?: 'text' | 'email' | 'password' | 'number' | 'textarea' | 'select' | 'checkbox' | 'radio';
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  options?: { value: string; label: string }[];
  className?: string;
  error?: string;
}

function Field({ name, label, type = 'text', placeholder, required, disabled, options, className, error }: FormFieldProps) {
  const { values, set } = useContext(Ctx);
  const val = values[name] ?? '';
  return (
    <div className={cn('space-y-1', className)}>
      <label className="yn-label">{label}{required ? ' *' : ''}</label>
      {type === 'textarea' ? (
        <textarea value={String(val)} placeholder={placeholder} disabled={disabled} onChange={(e) => set(name, e.target.value)} className={cn('yn-textarea', error && 'border-yn-error')} />
      ) : type === 'select' ? (
        <select value={String(val)} disabled={disabled} onChange={(e) => set(name, e.target.value)} className={cn('yn-select', error && 'border-yn-error')}>
          {(options ?? []).map((opt) => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
        </select>
      ) : type === 'checkbox' ? (
        <input type="checkbox" checked={String(val) === 'true' || val === true} disabled={disabled} onChange={(e) => set(name, e.target.checked)} />
      ) : (
        <input type={type} value={String(val)} placeholder={placeholder} disabled={disabled} onChange={(e) => set(name, e.target.value)} className={cn('yn-input', error && 'border-yn-error')} />
      )}
      {error && <p className="text-xs text-yn-error">{error}</p>}
    </div>
  );
}

export const FormField = Field;
(Form as unknown as Record<string, unknown>).Field = Field;
export default Form;
