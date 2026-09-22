'use client';

import { useState } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Lock, User, Eye, EyeOff, Loader2, AlertCircle } from 'lucide-react';
import { cn } from '@/utils/cn';
import { adminLogin } from '@/api';
import { toast } from '@/hooks/useToast';

/**
 * شاشة الدخول الموحدة — تعمل مع الهيكلين:
 *
 * 1) **Router mode** (النسخ الجديدة): <LoginPage /> بدون props —
 *    تنفذ adminLogin مباشرة ثم navigate({to:'/dashboard'})
 * 2) **Classic mode** (App.tsx): <LoginPage onLogin={...} isLoading={...} /> —
 *    الغلاف ينفذ التحقق ويخبرنا بالحالة
 *
 * ✅ FIX 2026-09-22: النسخة السابقة كانت تعمل مع Router فقط (useNavigate
 * يرمي خارج سياق الراوتر) — الآن مكوّنان داخليان بنفس الشكل البصري.
 */

interface SharedLoginProps {
  onSubmit: (username: string, password: string) => Promise<void>;
  isLoading: boolean;
  formError?: string;
  onFormError?: (msg?: string) => void;
}

function LoginCard({ onSubmit, isLoading, formError, onFormError }: SharedLoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const newErrors: Record<string, string> = {};
    if (!username.trim()) newErrors.username = 'اسم المستخدم مطلوب';
    if (!password) newErrors.password = 'كلمة المرور مطلوبة';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    onFormError?.(undefined);
    try {
      await onSubmit(username.trim(), password);
    } catch (err) {
      onFormError?.(err instanceof Error ? err.message : 'فشل تسجيل الدخول');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <Lock className="h-7 w-7 text-primary-foreground" />
          </div>
          <CardTitle className="text-2xl">يونس ماستر</CardTitle>
          <CardDescription>لوحة الإدارة السيادية — تسجيل الدخول</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            {formError && (
              <div className="flex items-center gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="username">اسم المستخدم</Label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="username"
                  type="text"
                  placeholder="admin"
                  value={username}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUsername(e.target.value)}
                  className={cn('pl-10', errors.username && 'border-destructive')}
                  disabled={isLoading}
                  autoComplete="username"
                />
              </div>
              {errors.username && <p className="text-sm text-destructive">{errors.username}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">كلمة المرور</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
                  className={cn('pl-3 pr-10', errors.password && 'border-destructive')}
                  disabled={isLoading}
                  autoComplete="current-password"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute right-2 top-1/2 -translate-y-1/2"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={isLoading}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </Button>
              </div>
              {errors.password && <p className="text-sm text-destructive">{errors.password}</p>}
            </div>

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                  جارٍ الدخول...
                </>
              ) : (
                'تسجيل الدخول'
              )}
            </Button>
          </form>

          <div className="mt-6 text-center text-sm text-muted-foreground">
            <p>بيانات الدخول الافتراضية</p>
            <p className="font-mono text-xs mt-1">admin / admin123</p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/** Router mode — النسخ الجديدة (TanStack Router) */
function LoginPageRouter() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  return (
    <LoginCard
      onSubmit={async (u, p) => {
        setLoading(true);
        try {
          await adminLogin(u, p);
          toast.success('تم تسجيل الدخول بنجاح');
          navigate({ to: '/dashboard' });
        } catch (err) {
          throw err; // يعرضها LoginCard
        } finally {
          setLoading(false);
        }
      }}
      isLoading={loading}
      formError={formError}
      onFormError={setFormError}
    />
  );
}

/** Classic mode — App.tsx (الغلاف ينفذ التحقق) */
function LoginPageClassic({ onLogin, isLoading }: { onLogin: (u: string, p: string) => Promise<void>; isLoading?: boolean }) {
  const [formError, setFormError] = useState<string | undefined>();
  return (
    <LoginCard
      onSubmit={onLogin}
      isLoading={isLoading ?? false}
      formError={formError}
      onFormError={setFormError}
    />
  );
}

/** المصدّر الموحد — يختار النمط حسب الـ props */
export function LoginPage(props: { onLogin?: (u: string, p: string) => Promise<void>; isLoading?: boolean }) {
  if (props.onLogin) {
    return <LoginPageClassic onLogin={props.onLogin} isLoading={props.isLoading} />;
  }
  return <LoginPageRouter />;
}

export default LoginPage;
