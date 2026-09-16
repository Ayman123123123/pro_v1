import { useState } from 'react';
import { UserCog, ToggleLeft, ToggleRight, Edit, Save, TestTube, Download, Shield, ExternalLink, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Form } from '@/components/ui/Form';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';

const mockOIDCConfig = {
  enabled: true,
  issuer: 'https://keycloak.example.com/realms/master',
  clientId: 'admin-dashboard',
  clientSecret: '••••••••••••••••',
  scopes: ['openid', 'profile', 'email', 'roles'],
  redirectUri: 'https://admin.example.com/callback',
  logoutUri: 'https://admin.example.com/logout',
};

const mockSAMLConfig = {
  enabled: false,
  entityId: 'https://admin.example.com/saml/metadata',
  ssoUrl: 'https://idp.example.com/sso',
  sloUrl: 'https://idp.example.com/slo',
  certificate: '••••••••••••••••',
  attributeMapping: {
    username: 'uid',
    email: 'mail',
    displayName: 'cn',
    roles: 'memberOf',
  },
};

export function SSOPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('oidc');
  const [dialogOpen, setDialogOpen] = useState(false);
  
  const tabs = [
    { id: 'oidc', label: t('security.sso.oidc'), icon: UserCog },
    { id: 'saml', label: t('security.sso.saml'), icon: Shield },
    { id: 'test', label: 'Test Connection', icon: TestTube },
  ];
  
  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.sso.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.sso.subtitle')}</p>
        </div>
      </div>
      
      <Tabs value={activeTab} onValueChange={setActiveTab} tabs={tabs} />
      
      {activeTab === 'oidc' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card title="OIDC Configuration" subtitle="OpenID Connect settings (Keycloak/Auth0)">
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-yn-text">OIDC Provider</p>
                    <p className="text-sm text-yn-text-secondary">Enable or disable OIDC authentication</p>
                  </div>
                  <button
                    className={cn('relative w-12 h-7 rounded-full transition-colors', mockOIDCConfig.enabled ? 'bg-yn-green' : 'bg-yn-border')}
                    onClick={() => { /* toggle */ }}
                  >
                    <span className={cn('absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform', mockOIDCConfig.enabled ? 'translate-x-6' : 'translate-x-0.5')} />
                  </button>
                </div>
                
                <Form.Field name="issuer" label="Issuer URL" placeholder="https://keycloak.example.com/realms/master" />
                <Form.Field name="clientId" label="Client ID" placeholder="admin-dashboard" />
                <Form.Field name="clientSecret" label="Client Secret" type="password" placeholder="••••••••••••••••" />
                <Form.Field name="scopes" label="Scopes" type="textarea" placeholder="openid, profile, email, roles" />
                <Form.Field name="redirectUri" label="Redirect URI" placeholder="https://admin.example.com/callback" />
                <Form.Field name="logoutUri" label="Logout URI" placeholder="https://admin.example.com/logout" />
                
                <div className="flex gap-3 pt-4 border-t border-yn-border">
                  <Button variant="primary">
                    <Save className="w-4 h-4 mr-2" />
                    Save Configuration
                  </Button>
                  <Button variant="secondary">
                    <TestTube className="w-4 h-4 mr-2" />
                    Test Connection
                  </Button>
                </div>
              </div>
            </Card>
            
            <Card title="Attribute Mapping" subtitle="Map OIDC claims to user attributes">
              <div className="grid grid-cols-2 gap-4">
                <Form.Field name="usernameClaim" label="Username Claim" placeholder="preferred_username" />
                <Form.Field name="emailClaim" label="Email Claim" placeholder="email" />
                <Form.Field name="displayNameClaim" label="Display Name Claim" placeholder="name" />
                <Form.Field name="rolesClaim" label="Roles Claim" placeholder="realm_access.roles" />
              </div>
            </Card>
          </div>
          
          <div className="space-y-4">
            <Card title="Status" subtitle="Connection status">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Status</span>
                  <Badge variant={mockOIDCConfig.enabled ? 'green' : 'default'} dot>
                    {mockOIDCConfig.enabled ? 'Connected' : 'Disconnected'}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Provider</span>
                  <span className="font-mono text-yn-text-secondary text-sm">Keycloak</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-yn-text-secondary">Last Sync</span>
                  <span className="text-yn-text-secondary">2 minutes ago</span>
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
      
      {activeTab === 'saml' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Shield className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">SAML Configuration</h3>
            <p>SAML 2.0 configuration coming soon</p>
          </div>
        </div>
      )}
      
      {activeTab === 'test' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="max-w-xl mx-auto">
            <h3 className="font-medium text-yn-text mb-4">Test SSO Connection</h3>
            <Form onSubmit={(data) => { /* test */ }}>
              <Form.Field name="testType" label="Test Type" type="select" options={[
                { value: 'oidc', label: 'OIDC Discovery' },
                { value: 'saml', label: 'SAML Metadata' },
              ]} />
              <Form.Field name="url" label="Test URL" placeholder="https://keycloak.example.com/realms/master" />
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