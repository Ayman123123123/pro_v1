import { useEffect, useState } from 'react';
import { Save, TestTube, Download, Shield, ExternalLink } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { apiFetch } from '../../api';
import { Button } from '@/components/ui/Button';
import { Form, FormField } from '@/components/ui/Form';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { Tabs } from '@/components/ui/Tabs';

interface SSOConfig {
  oidc?: { enabled: boolean; issuer?: string; clientId?: string; scopes?: string[]; redirectUri?: string; logoutUri?: string; provider?: string; lastSync?: string };
  saml?: { enabled: boolean };
}

export function SSOPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('oidc');
  const [config, setConfig] = useState<SSOConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [oidcEnabled, setOidcEnabled] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError('');
      try {
        const res = await apiFetch('/api/admin/security/sso');
        const data = await res.json().catch(() => null);
        if (!res.ok) throw new Error((data as any)?.error || `HTTP ${res.status}`);
        if (!cancelled) {
          setConfig(data && typeof data === 'object' ? data : null);
          setOidcEnabled(Boolean((data as any)?.oidc?.enabled));
        }
      } catch (e: any) {
        if (!cancelled) {
          setConfig(null);
          setError(e?.message || 'تعذر جلب إعدادات SSO من الخادم');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => { cancelled = true; };
  }, []);

  const tabs = [
    { value: 'oidc', label: t('security.sso.oidc') },
    { value: 'saml', label: t('security.sso.saml') },
    { value: 'test', label: 'Test Connection' },
  ];

  const oidc = config?.oidc;

  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.sso.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.sso.subtitle')}</p>
          <p className="text-xs text-yn-text-muted mt-1">إعدادات الدخول الموحد تُجلب من الخادم — لا توجد قيم وهمية مخزنة محلياً.</p>
        </div>
      </div>

      <Tabs value={activeTab} onChange={setActiveTab} tabs={tabs} />

      {loading && <div className="yn-loading"><div className="yn-spinner" /></div>}

      {!loading && error && (
        <div className="yn-card yn-card-liquid yn-glass p-6 text-center">
          <Shield className="w-10 h-10 mx-auto mb-3 text-yn-text-muted/50" />
          <p className="font-medium text-yn-text mb-1">لا توجد إعدادات معروضة</p>
          <p className="text-sm text-yn-text-secondary">تُجلب من الخادم عبر <span className="font-mono">GET /api/admin/security/sso</span></p>
          <p className="text-xs text-yn-error mt-2">{error}</p>
        </div>
      )}

      {!loading && !error && !oidc && activeTab !== 'test' && (
        <div className="yn-card yn-card-liquid yn-glass p-6 text-center">
          <Shield className="w-10 h-10 mx-auto mb-3 text-yn-text-muted/50" />
          <p className="font-medium text-yn-text mb-1">لا يوجد إعداد SSO بعد</p>
          <p className="text-sm text-yn-text-secondary">تُجلب من الخادم — الحالة فارغة حتى يضيف المسؤول موفر هوية.</p>
        </div>
      )}

      {!loading && !error && activeTab === 'oidc' && oidc && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card title="OIDC Configuration" subtitle="OpenID Connect settings (Keycloak/Auth0)">
              <Form onSubmit={() => { /* يحفظ عبر POST /api/admin/security/sso/oidc */ }} initialValues={{ issuer: oidc.issuer || '', clientId: oidc.clientId || '', redirectUri: oidc.redirectUri || '', logoutUri: oidc.logoutUri || '' }}>
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <p className="font-medium text-yn-text">OIDC Provider</p>
                    <p className="text-sm text-yn-text-secondary">Enable or disable OIDC authentication</p>
                  </div>
                  <button
                    type="button"
                    className={cn('relative w-12 h-7 rounded-full transition-colors', oidcEnabled ? 'bg-yn-green' : 'bg-yn-border')}
                    onClick={() => setOidcEnabled((v) => !v)}
                  >
                    <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', oidcEnabled ? 'translate-x-6' : 'translate-x-0.5')} />
                  </button>
                </div>

                <FormField name="issuer" label="Issuer URL" placeholder="https://keycloak.example.com/realms/master" />
                <FormField name="clientId" label="Client ID" placeholder="admin-dashboard" />
                <FormField name="clientSecret" label="Client Secret" type="password" placeholder="••••••••••••••••" />
                <FormField name="scopes" label="Scopes" type="textarea" placeholder="openid, profile, email, roles" />
                <FormField name="redirectUri" label="Redirect URI" placeholder="https://admin.example.com/callback" />
                <FormField name="logoutUri" label="Logout URI" placeholder="https://admin.example.com/logout" />

                <div className="flex gap-3 pt-4 border-t border-yn-border">
                  <Button variant="primary" type="submit">
                    <Save className="w-4 h-4 mr-2" />
                    Save Configuration
                  </Button>
                  <Button variant="secondary">
                    <TestTube className="w-4 h-4 mr-2" />
                    Test Connection
                  </Button>
                </div>
              </Form>
            </Card>

            <Card title="Attribute Mapping" subtitle="Map OIDC claims to user attributes">
              <Form onSubmit={() => {}} initialValues={{}}>
                <div className="grid grid-cols-2 gap-4">
                  <FormField name="usernameClaim" label="Username Claim" placeholder="preferred_username" />
                  <FormField name="emailClaim" label="Email Claim" placeholder="email" />
                  <FormField name="displayNameClaim" label="Display Name Claim" placeholder="name" />
                  <FormField name="rolesClaim" label="Roles Claim" placeholder="realm_access.roles" />
                </div>
              </Form>
            </Card>
          </div>

          <div className="space-y-4">
            <Card title="Status" subtitle="Connection status — تُجلب من الخادم">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Status</span>
                  <Badge variant={oidc.enabled ? 'green' : 'default'}>
                    {oidc.enabled ? 'Connected' : 'Disconnected'}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Provider</span>
                  <span className="font-mono text-yn-text-secondary text-sm">{oidc.provider || '—'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Last Sync</span>
                  <span className="text-yn-text-secondary">{oidc.lastSync || '—'}</span>
                </div>
              </div>
            </Card>

            <Card title="Actions" subtitle="Quick actions">
              <div className="space-y-2">
                <Button variant="secondary" className="w-full justify-start">
                  <TestTube className="w-4 h-4 mr-2" />
                  Test Connection
                </Button>
                <Button variant="secondary" className="w-full justify-start">
                  <Download className="w-4 h-4 mr-2" />
                  Download Metadata
                </Button>
                <Button variant="secondary" className="w-full justify-start">
                  <ExternalLink className="w-4 h-4 mr-2" />
                  View Discovery Document
                </Button>
              </div>
            </Card>
          </div>
        </div>
      )}

      {!loading && !error && activeTab === 'saml' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Shield className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">SAML Configuration</h3>
            <p>تُجلب من الخادم — لا يوجد إعداد SAML بعد (حالة فارغة).</p>
          </div>
        </div>
      )}

      {activeTab === 'test' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="max-w-xl mx-auto">
            <h3 className="font-medium text-yn-text mb-4">Test SSO Connection</h3>
            <Form onSubmit={() => { /* test عبر POST /api/admin/security/sso/test */ }} initialValues={{ testType: 'oidc', url: '' }}>
              <FormField name="testType" label="Test Type" type="select" options={[
                { value: 'oidc', label: 'OIDC Discovery' },
                { value: 'saml', label: 'SAML Metadata' },
              ]} />
              <FormField name="url" label="Test URL" placeholder="https://keycloak.example.com/realms/master" />
              <Button variant="primary" type="submit" className="w-full">
                <TestTube className="w-4 h-4 mr-2" />
                Run Test
              </Button>
            </Form>
          </div>
        </div>
      )}
    </div>
  );
}

export default SSOPage;
