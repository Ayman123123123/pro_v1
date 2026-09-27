import { useEffect, useState } from 'react';
import { Fingerprint, Key, Smartphone, Save, ShieldCheck, RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { apiFetch } from '../../api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';

/**
 * سياسة MFA — على العقد الحقيقي للخادم (2026-09-27).
 *
 * GET  /api/admin/mfa/policy  → { enforceForAdmins, enforceForAll, allowedMethods, totpWindow }
 * PUT  /api/admin/mfa/policy  → يتحقق ويرفض الطرق غير المسموحة والنافذة خارج 0..10.
 *
 * كانت الصفحة كتلة وهمية ثابتة بأزرار toggle فارغة — الآن تقرأ وتكتب السياسة فعلياً.
 */

const ALL_METHODS = ['TOTP', 'WEB_AUTHN', 'BACKUP_CODES', 'SMS', 'EMAIL'] as const;
type MfaMethod = (typeof ALL_METHODS)[number];

interface MfaPolicy {
  enforceForAdmins: boolean;
  enforceForAll: boolean;
  allowedMethods: MfaMethod[];
  totpWindow: number;
}

const DEFAULTS: MfaPolicy = {
  enforceForAdmins: true,
  enforceForAll: false,
  allowedMethods: ['TOTP', 'WEB_AUTHN', 'BACKUP_CODES'],
  totpWindow: 1,
};

const METHOD_DESC: Record<MfaMethod, string> = {
  TOTP: 'Time-based One-Time Password (Google Authenticator, etc.)',
  WEB_AUTHN: 'WebAuthn / Passkeys (Touch ID, Face ID, Security Keys)',
  BACKUP_CODES: 'One-time backup codes for account recovery',
  SMS: 'SMS-based verification codes',
  EMAIL: 'Email-based verification codes',
};

export function MFAPage() {
  const { t } = useTranslation();
  const [policy, setPolicy] = useState<MfaPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [savedAt, setSavedAt] = useState('');

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const res = await apiFetch('/api/admin/mfa/policy');
        const data = await res.json().catch(() => null);
        if (!res.ok) throw new Error((data as any)?.error || `HTTP ${res.status}`);
        if (!cancelled) setPolicy({ ...DEFAULTS, ...(data as object) } as MfaPolicy);
      } catch (e: any) {
        if (!cancelled) {
          setPolicy(null);
          setError(e?.message || 'تعذر جلب سياسة MFA من الخادم');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  async function save(next: MfaPolicy) {
    setSaving(true);
    setSaveError('');
    try {
      const res = await apiFetch('/api/admin/mfa/policy', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(next),
      });
      const data = await res.json().catch(() => null);
      if (!res.ok) throw new Error((data as any)?.error || `HTTP ${res.status}`);
      setPolicy({ ...DEFAULTS, ...(data as object) } as MfaPolicy);
      setSavedAt(new Date().toLocaleTimeString());
    } catch (e: any) {
      setSaveError(e?.message || 'فشل حفظ السياسة');
    } finally {
      setSaving(false);
    }
  }

  function toggleMethod(m: MfaMethod) {
    if (!policy) return;
    const has = policy.allowedMethods.includes(m);
    const next = has
      ? policy.allowedMethods.filter((x) => x !== m)
      : [...policy.allowedMethods, m];
    if (next.length === 0) return; // الخادم يرفع AT_LEAST_ONE_METHOD_REQUIRED — نمنعها محلياً
    setPolicy({ ...policy, allowedMethods: next });
  }

  if (loading) return <div className="yn-loading"><div className="yn-spinner" /></div>;

  if (!policy) {
    return (
      <div className="yn-page space-y-6">
        <Header t={t} />
        <div className="yn-card yn-card-liquid yn-glass p-6 text-center">
          <ShieldCheck className="w-10 h-10 mx-auto mb-3 text-yn-text-muted/50" />
          <p className="font-medium text-yn-text mb-1">لا توجد سياسة معروضة</p>
          <p className="text-sm text-yn-text-secondary">تُجلب من الخادم عبر <span className="font-mono">GET /api/admin/mfa/policy</span></p>
          <p className="text-xs text-yn-error mt-2">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="yn-page space-y-6">
      <Header t={t} actions={
        <>
          {savedAt && <span className="text-xs text-yn-text-muted">حُفظ {savedAt}</span>}
          <Button variant="primary" size="sm" disabled={saving} onClick={() => save(policy)}>
            {saving ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
            Save Policy
          </Button>
        </>
      } />

      {saveError && (
        <div className="yn-card yn-glass p-3 text-sm text-yn-error border border-yn-error/30">
          فشل الحفظ: <span className="font-mono">{saveError}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="MFA Enforcement" subtitle="Configure when MFA is required">
          <div className="space-y-4">
            <Toggle
              title="Enforce for Admins"
              subtitle="Require MFA for all admin accounts"
              value={policy.enforceForAdmins}
              onChange={(v) => setPolicy({ ...policy, enforceForAdmins: v })}
            />
            <Toggle
              title="Enforce for All Users"
              subtitle="Require MFA for all user accounts"
              value={policy.enforceForAll}
              onChange={(v) => setPolicy({ ...policy, enforceForAll: v })}
            />
            <div className="pt-4 border-t border-yn-border">
              <p className="font-medium text-yn-text mb-3">TOTP Time Window (0-10)</p>
              <input
                type="number"
                min={0}
                max={10}
                value={policy.totpWindow}
                onChange={(e) => {
                  const v = parseInt(e.target.value, 10);
                  setPolicy({ ...policy, totpWindow: Number.isNaN(v) ? 0 : Math.max(0, Math.min(10, v)) });
                }}
                className="yn-input w-24"
              />
            </div>
          </div>
        </Card>

        <Card title="Allowed Methods" subtitle="Configure which MFA methods are available">
          <div className="space-y-4">
            {ALL_METHODS.map((method) => {
              const enabled = policy.allowedMethods.includes(method);
              const onlyOne = policy.allowedMethods.length === 1 && enabled;
              return (
                <div key={method} className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-yn-green/15 flex items-center justify-center">
                      {method === 'TOTP' && <Fingerprint className="w-4 h-4 text-yn-green" />}
                      {method === 'WEB_AUTHN' && <Smartphone className="w-4 h-4 text-yn-green" />}
                      {method === 'BACKUP_CODES' && <Key className="w-4 h-4 text-yn-green" />}
                    </div>
                    <div>
                      <p className="font-medium text-yn-text">{method}</p>
                      <p className="text-sm text-yn-text-secondary">{METHOD_DESC[method]}</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    disabled={onlyOne}
                    title={onlyOne ? 'يجب الإبقاء على طريقة واحدة على الأقل' : undefined}
                    className={cn('relative w-12 h-7 rounded-full transition-colors', enabled ? 'bg-yn-green' : 'bg-yn-border', onlyOne && 'opacity-50 cursor-not-allowed')}
                    onClick={() => toggleMethod(method)}
                  >
                    <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', enabled ? 'translate-x-6' : 'translate-x-0.5')} />
                  </button>
                </div>
           
              );
            })}
          </div>
        </Card>

        <Card title="Current Policy Summary" subtitle="ما سيُحفظ على الخادم">
          <div className="space-y-3">
            <SummaryRow label="Admins" value={<Badge variant={policy.enforceForAdmins ? 'green' : 'default'}>{policy.enforceForAdmins ? 'Enforced' : 'Optional'}</Badge>} />
            <SummaryRow label="All Users" value={<Badge variant={policy.enforceForAll ? 'green' : 'default'}>{policy.enforceForAll ? 'Enforced' : 'Optional'}</Badge>} />
            <SummaryRow label="Methods" value={<span className="font-mono text-sm text-yn-text">{policy.allowedMethods.join(', ')}</span>} />
            <SummaryRow label="TOTP Window" value={<span className="font-mono text-sm text-yn-text">±{policy.totpWindow}</span>} />
          </div>
        </Card>
      </div>
    </div>
  );
}

function Header({ t, actions }: { t: (k: string) => string; actions?: React.ReactNode }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div>
        <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.mfa.title')}</h1>
        <p className="text-yn-text-secondary">{t('security.mfa.subtitle')}</p>
      </div>
      <div className="flex items-center gap-3">{actions}</div>
    </div>
  );
}

function Toggle({ title, subtitle, value, onChange }: { title: string; subtitle: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between">
      <div>
        <p className="font-medium text-yn-text">{title}</p>
        <p className="text-sm text-yn-text-secondary">{subtitle}</p>
      </div>
      <button
        type="button"
        className={cn('relative w-12 h-7 rounded-full transition-colors', value ? 'bg-yn-green' : 'bg-yn-border')}
        onClick={() => onChange(!value)}
      >
        <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', value ? 'translate-x-6' : 'translate-x-0.5')} />
      </button>
    </div>
  );
}

function SummaryRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-yn-text-secondary">{label}</span>
      {value}
    </div>
  );
}

export default MFAPage;
