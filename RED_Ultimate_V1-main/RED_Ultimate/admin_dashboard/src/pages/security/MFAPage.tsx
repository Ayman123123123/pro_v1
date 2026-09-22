import { useState } from 'react';
import { Fingerprint, Key, Smartphone, Download, Save } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { Button } from '@/components/ui/button';
import { Form, FormField } from '@/components/ui/form';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Tabs } from '@/components/ui/tabs';

const mockMFAConfig = {
  enforceForAdmins: true,
  enforceForAll: false,
  allowedMethods: ['TOTP', 'WEB_AUTHN', 'BACKUP_CODES'],
  backupCodesCount: 10,
  totpWindow: 1,
  webauthnRelyingParty: 'RED Admin Dashboard',
};

export function MFAPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('settings');
  
  const tabs = [
    { value: 'settings', label: 'MFA Settings' },
    { value: 'methods', label: 'Auth Methods' },
    { value: 'enrollment', label: 'Enrollment' },
  ];
  
  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.mfa.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.mfa.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export Report
          </Button>
          <Button variant="primary" size="sm">
            <Save className="w-4 h-4 mr-2" />
            Save Changes
          </Button>
        </div>
      </div>
      
      <Tabs value={activeTab} onChange={setActiveTab} tabs={tabs} />
      
      {activeTab === 'settings' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card title="MFA Enforcement" subtitle="Configure when MFA is required">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-yn-text">Enforce for Admins</p>
                  <p className="text-sm text-yn-text-secondary">Require MFA for all admin accounts</p>
                </div>
                <button
                  className={cn('relative w-12 h-7 rounded-full transition-colors', mockMFAConfig.enforceForAdmins ? 'bg-yn-green' : 'bg-yn-border')}
                  onClick={() => { /* toggle */ }}
                >
                  <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', mockMFAConfig.enforceForAdmins ? 'translate-x-6' : 'translate-x-0.5')} />
                </button>
              </div>
              
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-yn-text">Enforce for All Users</p>
                  <p className="text-sm text-yn-text-secondary">Require MFA for all user accounts</p>
                </div>
                <button
                  className={cn('relative w-12 h-7 rounded-full transition-colors', mockMFAConfig.enforceForAll ? 'bg-yn-green' : 'bg-yn-border')}
                  onClick={() => { /* toggle */ }}
                >
                  <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', mockMFAConfig.enforceForAll ? 'translate-x-6' : 'translate-x-0.5')} />
                </button>
              </div>
              
              <div className="pt-4 border-t border-yn-border">
                <p className="font-medium text-yn-text mb-3">TOTP Time Window</p>
                <div className="flex items-center gap-4">
                  <input
                    type="number"
                    defaultValue={mockMFAConfig.totpWindow}
                    min={0}
                    max={10}
                    className="yn-input w-24"
                  />
                  <span className="text-yn-text-secondary">time steps (±)</span>
                </div>
              </div>
            </div>
          </Card>
          
          <Card title="Allowed Methods" subtitle="Configure which MFA methods are available">
            <div className="space-y-4">
              {['TOTP', 'WEB_AUTHN', 'BACKUP_CODES', 'SMS', 'EMAIL'].map((method) => (
                <div key={method} className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-lg bg-yn-green/15 flex items-center justify-center">
                      {method === 'TOTP' && <Fingerprint className="w-4 h-4 text-yn-green" />}
                      {method === 'WEB_AUTHN' && <Smartphone className="w-4 h-4 text-yn-green" />}
                      {method === 'BACKUP_CODES' && <Key className="w-4 h-4 text-yn-green" />}
                    </div>
                    <div>
                      <p className="font-medium text-yn-text">{method}</p>
                      <p className="text-sm text-yn-text-secondary">
                        {method === 'TOTP' && 'Time-based One-Time Password (Google Authenticator, etc.)'}
                        {method === 'WEB_AUTHN' && 'WebAuthn / Passkeys (Touch ID, Face ID, Security Keys)'}
                        {method === 'BACKUP_CODES' && 'One-time backup codes for account recovery'}
                        {method === 'SMS' && 'SMS-based verification codes'}
                        {method === 'EMAIL' && 'Email-based verification codes'}
                      </p>
                    </div>
                  </div>
                  <button
                    className={cn('relative w-12 h-7 rounded-full transition-colors', mockMFAConfig.allowedMethods.includes(method) ? 'bg-yn-green' : 'bg-yn-border')}
                    onClick={() => { /* toggle */ }}
                  >
                    <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', mockMFAConfig.allowedMethods.includes(method) ? 'translate-x-6' : 'translate-x-0.5')} />
                  </button>
                </div>
              ))}
            </div>
          </Card>
          
          <Card title="Backup Codes" subtitle="Configure backup code generation">
            <div className="space-y-4">
              <div className="flex items-center gap-4">
                <label className="yn-label">Number of Backup Codes</label>
                <input
                  type="number"
                  defaultValue={mockMFAConfig.backupCodesCount}
                  min={5}
                  max={20}
                  className="yn-input w-32"
                />
              </div>
              <p className="text-sm text-yn-text-secondary">
                Users will receive this many one-time backup codes when they enroll in MFA.
              </p>
            </div>
          </Card>
          
          <Card title="WebAuthn Settings" subtitle="Configure WebAuthn relying party">
            <Form onSubmit={() => {}} initialValues={{}}>
              <FormField name="rpName" label="Relying Party Name" placeholder="RED Admin Dashboard" />
              <FormField name="rpId" label="Relying Party ID" placeholder="admin.example.com" />
              <FormField name="origin" label="Origin" placeholder="https://admin.example.com" />
            </Form>
          </Card>
        </div>
      )}
      
      {activeTab === 'methods' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <MethodCard
            title="TOTP"
            description="Time-based One-Time Password using authenticator apps"
            icon={Fingerprint}
            enabled={mockMFAConfig.allowedMethods.includes('TOTP')}
            stats={{ enrolled: 1250, active: 1180 }}
          />
          <MethodCard
            title="WebAuthn / Passkeys"
            description="Biometric authentication and security keys"
            icon={Smartphone}
            enabled={mockMFAConfig.allowedMethods.includes('WEB_AUTHN')}
            stats={{ enrolled: 890, active: 850 }}
          />
          <MethodCard
            title="Backup Codes"
            description="One-time recovery codes for account access"
            icon={Key}
            enabled={mockMFAConfig.allowedMethods.includes('BACKUP_CODES')}
            stats={{ enrolled: 2100, active: 2100 }}
          />
        </div>
      )}
      
      {activeTab === 'enrollment' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Fingerprint className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">MFA Enrollment Management</h3>
            <p>User enrollment flows, recovery, and administration coming soon</p>
          </div>
        </div>
      )}
    </div>
  );
}

function MethodCard({ title, description, icon: Icon, enabled, stats }: { title: string; description: string; icon: React.ComponentType<{ className?: string }>; enabled: boolean; stats: { enrolled: number; active: number } }) {
  return (
    <div className={cn('yn-card yn-card-liquid yn-glass p-6', enabled ? 'border-yn-green/30' : '')}>
      <div className="flex items-start justify-between mb-4">
        <div className="w-12 h-12 rounded-xl bg-yn-green/15 flex items-center justify-center">
          <Icon className="w-6 h-6 text-yn-green" />
        </div>
        <Badge variant={enabled ? 'green' : 'default'}>{enabled ? 'Enabled' : 'Disabled'}</Badge>
      </div>
      <h3 className="font-medium text-yn-text mb-1">{title}</h3>
      <p className="text-sm text-yn-text-secondary mb-4">{description}</p>
      
      <div className="grid grid-cols-2 gap-4 pt-4 border-t border-yn-border">
        <div>
          <p className="text-2xl font-bold text-yn-text">{stats.enrolled.toLocaleString()}</p>
          <p className="text-sm text-yn-text-secondary">Enrolled</p>
        </div>
        <div>
          <p className="text-2xl font-bold text-yn-text">{stats.active.toLocaleString()}</p>
          <p className="text-sm text-yn-text-secondary">Active</p>
        </div>
      </div>
      
      <div className="mt-4 flex gap-2">
        <Button variant={enabled ? 'secondary' : 'primary'} size="sm" className="flex-1">
          {enabled ? 'Disable' : 'Enable'}
        </Button>
        <Button variant="ghost" size="sm">
          Configure
        </Button>
      </div>
    </div>
  );
}

export default MFAPage;