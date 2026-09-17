import { useState } from 'react';
import { Key, Download, Save, Moon, Sun, Monitor } from 'lucide-react';
import { cn } from '@/utils';
import { useUIStore, useAuthStore } from '@/stores';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Form, FormField } from '@/components/ui/Form';
import { Tabs } from '@/components/ui/Tabs';
import { Select } from '@/components/ui/Select';

export function SettingsPage() {
  const { theme, setTheme } = useUIStore();
  const { user, updateUser } = useAuthStore();
  const [activeTab, setActiveTab] = useState('profile');
  const [saving, setSaving] = useState(false);
  
  const tabs = [
    { value: 'profile', label: 'Profile' },
    { value: 'appearance', label: 'Appearance' },
    { value: 'notifications', label: 'Notifications' },
    { value: 'security', label: 'Security' },
    { value: 'advanced', label: 'Advanced' },
  ];
  
  return (
    <div className="yn-page space-y-6 max-w-4xl">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">Settings</h1>
          <p className="text-yn-text-secondary">Manage your account and preferences</p>
        </div>
      </div>
      
      <Tabs value={activeTab} onChange={setActiveTab} tabs={tabs} />
      
      {activeTab === 'profile' && (
        <Card title="Profile Information" subtitle="Update your personal information">
          <Form onSubmit={async (data: any) => { setSaving(true); await new Promise(r => setTimeout(r, 1000)); updateUser(data); setSaving(false); }} initialValues={(user as any) || {}}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <FormField name="displayName" label="Display Name" placeholder="John Doe" required />
              <FormField name="username" label="Username" placeholder="johndoe" required disabled />
              <FormField name="email" label="Email" type="email" placeholder="john@example.com" />
              <FormField name="phone" label="Phone" placeholder="+1 (555) 000-0000" />
            </div>
            <FormField name="bio" label="Bio" type="textarea" placeholder="Tell us about yourself..." />
            <Button variant="primary" type="submit" disabled={saving}>
              <Save className="w-4 h-4 mr-2" />
              {saving ? 'Saving...' : 'Save Changes'}
            </Button>
          </Form>
        </Card>
      )}
      
      {activeTab === 'appearance' && (
        <div className="space-y-6">
          <Card title="Theme" subtitle="Choose your preferred color theme">
            <div className="grid grid-cols-3 gap-4">
              {['light', 'dark', 'system'].map((t) => (
                <button
                  key={t}
                  onClick={() => setTheme(t as any)}
                  className={cn(
                    'p-6 rounded-xl border-2 transition-all',
                    theme === t
                      ? 'border-yn-green bg-yn-green/5'
                      : 'border-yn-border hover:border-yn-green/30'
                  )}
                >
                  <div className="w-12 h-12 rounded-lg mx-auto mb-3 flex items-center justify-center"
                    style={{
                      background: t === 'light' ? '#F7F8FA' : t === 'dark' ? '#0A0F14' : 'linear-gradient(135deg, #0A0F14 0%, #F7F8FA 100%)',
                      border: t === 'system' ? '2px dashed #2C3A4A' : 'none',
                    }}
                  >
                    {t === 'light' && <Sun className="w-6 h-6 text-yn-gold" />}
                    {t === 'dark' && <Moon className="w-6 h-6 text-yn-gold" />}
                    {t === 'system' && <Monitor className="w-6 h-6 text-yn-text-secondary" />}
                  </div>
                  <p className="font-medium text-yn-text capitalize">{t}</p>
                  <p className="text-sm text-yn-text-secondary mt-1">
                    {t === 'light' && 'Light theme'}
                    {t === 'dark' && 'Dark theme'}
                    {t === 'system' && 'Follow system'}
                  </p>
                </button>
              ))}
            </div>
          </Card>
          
          <Card title="Language" subtitle="Choose your preferred language">
            <Select
              value="ar"
              onChange={() => {}}
              options={[
                { value: 'ar', label: 'العربية (Arabic)' },
                { value: 'en', label: 'English' },
              ]}
              placeholder="Select language"
            />
          </Card>
          
          <Card title="Density" subtitle="Adjust UI density">
            <div className="grid grid-cols-3 gap-4">
              {['comfortable', 'compact', 'spacious'].map((d) => (
                <button key={d} className="p-4 rounded-lg border border-yn-border hover:border-yn-green/30 transition-colors">
                  <p className="font-medium text-yn-text capitalize">{d}</p>
                  <p className="text-sm text-yn-text-secondary mt-1">
                    {d === 'comfortable' && 'Default spacing'}
                    {d === 'compact' && 'Reduced spacing'}
                    {d === 'spacious' && 'Increased spacing'}
                  </p>
                </button>
              ))}
            </div>
          </Card>
        </div>
      )}
      
      {activeTab === 'notifications' && (
        <Card title="Notification Preferences" subtitle="Configure how you receive notifications">
          <div className="space-y-4">
            {[
              { key: 'email', label: 'Email Notifications', description: 'Receive notifications via email' },
              { key: 'push', label: 'Push Notifications', description: 'Receive push notifications in browser' },
              { key: 'inApp', label: 'In-App Notifications', description: 'Show notifications in the dashboard' },
              { key: 'security', label: 'Security Alerts', description: 'Critical security notifications' },
              { key: 'reports', label: 'Report Ready', description: 'When scheduled reports are ready' },
              { key: 'system', label: 'System Updates', description: 'Maintenance and update notifications' },
            ].map((item) => (
              <div key={item.key} className="flex items-center justify-between p-4 bg-yn-navy/50 rounded-lg">
                <div>
                  <p className="font-medium text-yn-text">{item.label}</p>
                  <p className="text-sm text-yn-text-secondary">{item.description}</p>
                </div>
                <button className="relative w-12 h-7 rounded-full bg-yn-green transition-colors">
                  <span className="absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow-md" />
                </button>
              </div>
            ))}
          </div>
        </Card>
      )}
      
      {activeTab === 'security' && (
        <div className="space-y-6">
          <Card title="Two-Factor Authentication" subtitle="Add an extra layer of security to your account">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-yn-text">Authenticator App</p>
                <p className="text-sm text-yn-text-secondary">Use Google Authenticator, Authy, or similar</p>
              </div>
              <Button variant="primary" size="sm">
                <Key className="w-4 h-4 mr-2" />
                Enable
              </Button>
            </div>
          </Card>
          
          <Card title="Backup Codes" subtitle="Generate backup codes for account recovery">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-yn-text">Backup Codes</p>
                <p className="text-sm text-yn-text-secondary">8 codes remaining</p>
              </div>
              <Button variant="secondary" size="sm">
                <Download className="w-4 h-4 mr-2" />
                Regenerate
              </Button>
            </div>
          </Card>
          
          <Card title="Active Sessions" subtitle="Manage your active sessions">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-yn-text">5 active sessions</p>
                <p className="text-sm text-yn-text-secondary">Last activity: 2 minutes ago</p>
              </div>
              <Button variant="secondary" size="sm">
                View All
              </Button>
            </div>
          </Card>
        </div>
      )}
      
      {activeTab === 'advanced' && (
        <div className="space-y-6">
          <Card title="Data Export" subtitle="Export your account data">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-yn-text">Export All Data</p>
                <p className="text-sm text-yn-text-secondary">Download a copy of your account data</p>
              </div>
              <Button variant="secondary">
                <Download className="w-4 h-4 mr-2" />
                Export Data
              </Button>
            </div>
          </Card>
          
          <Card title="Danger Zone" subtitle="Irreversible actions">
            <div className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-yn-error/10 border border-yn-error/30 rounded-lg">
                <div>
                  <p className="font-medium text-yn-error">Delete Account</p>
                  <p className="text-sm text-yn-text-secondary">Permanently delete your account and all data</p>
                </div>
                <Button variant="danger" size="sm">Delete Account</Button>
              </div>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

export default SettingsPage;