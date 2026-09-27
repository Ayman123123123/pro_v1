'use client';

import { useEffect, useState } from 'react';
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

  const [rememberMe, setRememberMe] = useState(false);

  return (
    <div className="min-h-screen flex items-center justify-center bg-background relative overflow-hidden p-4">
      {/* Background Orbs */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-emerald-500/20 rounded-full blur-[100px] -z-10 animate-pulse" />
      <div className="absolute bottom-1/4 right-1/4 w-[30rem] h-[30rem] bg-amber-500/20 rounded-full blur-[120px] -z-10 animate-pulse delay-1000" />
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[40rem] h-[40rem] bg-blue-500/10 rounded-full blur-[120px] -z-10" />

      <Card className="w-full max-w-md border border-white/10 shadow-2xl bg-background/60 backdrop-blur-xl transition-all duration-500 animate-in fade-in zoom-in-95">
        <CardHeader className="text-center space-y-3 pb-6">
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-500 to-amber-500 shadow-lg mb-2">
            <Lock className="h-8 w-8 text-white" />
          </div>
          <CardTitle className="text-3xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-emerald-500 to-amber-500">
            يونس ماستر
          </CardTitle>
          <CardDescription className="text-base text-foreground/80 font-medium">
            لوحة الإدارة السيادية — تسجيل الدخول
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-5">
            {formError && (
              <div className="flex items-center gap-3 rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive animate-in slide-in-from-top-2">
                <AlertCircle className="h-5 w-5 shrink-0" />
                <span className="font-medium">{formError}</span>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="username" className="text-foreground/80 font-semibold">اسم المستخدم</Label>
              <div className="relative group">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground transition-colors group-focus-within:text-emerald-500" />
                <Input
                  id="username"
                  type="text"
                  placeholder="admin"
                  value={username}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUsername(e.target.value)}
                  className={cn('pl-10 h-12 bg-background/50 border-border/50 focus:border-emerald-500 transition-all duration-300', errors.username && 'border-destructive focus:border-destructive')}
                  disabled={isLoading}
                  autoComplete="username"
                />
              </div>
              {errors.username && <p className="text-sm text-destructive font-medium animate-in fade-in">{errors.username}</p>}
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <Label htmlFor="password" className="text-foreground/80 font-semibold">كلمة المرور</Label>
              </div>
              <div className="relative group">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
                  className={cn('pl-3 pr-10 h-12 bg-background/50 border-border/50 focus:border-emerald-500 transition-all duration-300', errors.password && 'border-destructive focus:border-destructive')}
                  disabled={isLoading}
                  autoComplete="current-password"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute right-1 top-1/2 -translate-y-1/2 h-10 w-10 text-muted-foreground hover:text-foreground"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={isLoading}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </Button>
              </div>
              {errors.password && <p className="text-sm text-destructive font-medium animate-in fade-in">{errors.password}</p>}
            </div>

            <div className="flex items-center justify-between">
              <label className="flex items-center gap-2 cursor-pointer">
                <input 
                  type="checkbox" 
                  checked={rememberMe} 
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 rounded border-border text-emerald-500 focus:ring-emerald-500/30 bg-background/50 cursor-pointer" 
                />
                <span className="text-sm font-medium text-foreground/80 hover:text-foreground transition-colors">تذكرني</span>
              </label>
            </div>

            <Button type="submit" className="w-full h-12 text-base font-bold bg-gradient-to-r from-emerald-600 to-amber-600 hover:from-emerald-500 hover:to-amber-500 transition-all duration-300 shadow-lg hover:shadow-emerald-500/25 border-0" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="h-5 w-5 mr-2 animate-spin" />
                  جارٍ الدخول...
                </>
              ) : (
                'تسجيل الدخول'
              )}
            </Button>
          </form>

          {import.meta.env.DEV && (
            <div className="mt-8 text-center text-sm text-muted-foreground/80 bg-muted/30 p-3 rounded-lg border border-border/50">
              <p className="font-medium">بيانات الدخول الافتراضية (تطوير فقط)</p>
              <p className="font-mono text-xs mt-1 text-foreground">admin / admin123</p>
            </div>
          )}

          <div className="mt-4 pt-4 border-t border-border/50">
            <ServerStatusBadge />
            <p className="mt-2 text-center text-[10px] text-muted-foreground/50 font-mono tracking-wider uppercase">build 2026-09-26·1</p>
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

/**
 * ✅ 2026-09-23: مؤشر حالة الخادم — تشخيص فوري على الشاشة نفسها.
 * أخضر: متصل بخادم حقيقي | كهرماني: وضع العرض التجريبي (بيانات محاكية) | أحمر: لا خادم.
 */
function ServerStatusBadge() {
  const [status, setStatus] = useState<'checking' | 'real' | 'mock' | 'down'>('checking');
  useEffect(() => {
    let alive = true;
    fetch('/health', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => { if (alive) setStatus(d && d.mock ? 'mock' : d ? 'real' : 'down'); })
      .catch(() => { if (alive) setStatus('down'); });
    return () => { alive = false; };
  }, []);
  const cfg = {
    checking: { text: 'جارٍ فحص الخادم…', cls: 'text-muted-foreground' },
    real: { text: '● متصل بالخادم الحقيقي', cls: 'text-emerald-600 dark:text-emerald-400' },
    mock: { text: '● وضع العرض — بيانات تجريبية (بدون باكد)', cls: 'text-amber-600 dark:text-amber-400' },
    down: { text: '● الخادم غير متاح — حدّث الصفحة (Ctrl+Shift+R)', cls: 'text-red-600 dark:text-red-400' },
  }[status];
  return <p className={cn('mt-3 text-xs font-medium', cfg.cls)}>{cfg.text}</p>;
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
