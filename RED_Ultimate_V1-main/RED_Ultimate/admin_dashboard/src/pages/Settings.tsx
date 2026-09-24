'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { useTranslation } from 'react-i18next';
import { Settings, Palette, Globe, Bell, Shield, Key, Database, Trash2, Download, Upload } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useTheme } from '@/components/providers/ThemeProvider';
import { Label } from '@/components/ui/label';
import { RequireAuth, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';

export function SettingsPage() {
  const { t, i18n } = useTranslation();
  const { theme, setTheme, resolvedTheme } = useTheme();
  const [activeTab, setActiveTab] = useState<string>('appearance');
  const [language, setLanguage] = useState(i18n.language?.startsWith('ar') ? 'ar' : 'en');
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [weeklyDigest, setWeeklyDigest] = useState(false);
  // ✅ 2026-09-24: ربط المفاتيح الوهمية بحالة محلية
  const [securityAlerts, setSecurityAlerts] = useState(true);
  const [developerMode, setDeveloperMode] = useState(false);
  const [betaFeatures, setBetaFeatures] = useState(false);
  const [telemetry, setTelemetry] = useState(true);

  const changeLanguage = (lng: string) => {
    setLanguage(lng);
    void i18n.changeLanguage(lng);
  };

  return (
    <RequireAuth>
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('settings.title')}</h1>
        <p className="text-foreground/70">{t('settings.subtitle')}</p>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="appearance" className={TABS_TRIGGER_CLASS}><Palette className="h-4 w-4 mr-2" aria-hidden="true" /> {t('settings.tabAppearance')}</TabsTrigger>
          <TabsTrigger value="notifications" className={TABS_TRIGGER_CLASS}><Bell className="h-4 w-4 mr-2" aria-hidden="true" /> {t('settings.tabNotifications')}</TabsTrigger>
          <TabsTrigger value="security" className={TABS_TRIGGER_CLASS}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('settings.tabSecurity')}</TabsTrigger>
          <TabsTrigger value="data" className={TABS_TRIGGER_CLASS}><Database className="h-4 w-4 mr-2" aria-hidden="true" /> {t('settings.tabData')}</TabsTrigger>
          <TabsTrigger value="advanced" className={TABS_TRIGGER_CLASS}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('settings.tabAdvanced')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="appearance">
          <div className="grid gap-6 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2"><Palette className="h-5 w-5" aria-hidden="true" /> {t('settings.themeTitle')}</CardTitle>
                <CardDescription>{t('settings.themeDesc')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-3 gap-4">
                  {(['light', 'dark', 'system'] as const).map((t) => (
                    <button
                      key={t}
                      type="button"
                      onClick={() => setTheme(t)}
                      aria-pressed={theme === t}
                      aria-label={`Theme ${t}`}
                      className={cn(
                        'p-4 border-2 rounded-lg transition-all',
                        theme === t
                          ? 'border-primary bg-primary/5'
                          : 'border-border hover:border-primary/50'
                      )}
                    >
                      <div className="text-center">
                        {t === 'light' && <span className="text-4xl">☀️</span>}
                        {t === 'dark' && <span className="text-4xl">🌙</span>}
                        {t === 'system' && <span className="text-4xl">💻</span>}
                        <p className="font-medium mt-2 capitalize">{t}</p>
                        <p className="text-xs text-muted-foreground">
                          {t === 'light' && 'Light mode'}
                          {t === 'dark' && 'Dark mode'}
                          {t === 'system' && 'Follow system'}
                        </p>
                      </div>
                    </button>
                  ))}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2"><Globe className="h-5 w-5" aria-hidden="true" /> {t('settings.langTitle')}</CardTitle>
                <CardDescription>{t('settings.langDesc')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>Language</Label>
                  <Select value={language} onValueChange={changeLanguage}>
                    <SelectTrigger><SelectValue placeholder="Select language" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ar">العربية (Arabic)</SelectItem>
                      <SelectItem value="en">English</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Date Format</Label>
                  <Select>
                    <SelectTrigger><SelectValue placeholder="Select format" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ar-EG">DD/MM/YYYY (Arabic)</SelectItem>
                      <SelectItem value="en-US">MM/DD/YYYY (US)</SelectItem>
                      <SelectItem value="ISO">YYYY-MM-DD (ISO)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Timezone</Label>
                  <Select>
                    <SelectTrigger><SelectValue placeholder="Select timezone" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Africa/Cairo">Cairo (UTC+2)</SelectItem>
                      <SelectItem value="UTC">UTC</SelectItem>
                      <SelectItem value="America/New_York">New York (UTC-5)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>Notification Preferences</CardTitle>
              <CardDescription>Configure how you receive notifications</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <h4 className="font-medium text-foreground">Email Notifications</h4>
                <div className="space-y-3 pl-4 border-l-2">
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">All notifications</p>
                      <p className="text-sm text-foreground/70">Receive email for every notification</p>
                    </div>
                    <Switch checked={emailNotifications} onCheckedChange={setEmailNotifications} aria-label="Toggle all email notifications" />
                  </label>
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">Weekly digest</p>
                      <p className="text-sm text-foreground/70">Summary of weekly activity</p>
                    </div>
                    <Switch checked={weeklyDigest} onCheckedChange={setWeeklyDigest} aria-label="Toggle weekly digest emails" />
                  </label>
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">Security alerts</p>
                      <p className="text-sm text-foreground/70">Critical security events</p>
                    </div>
                    <Switch checked={securityAlerts} onCheckedChange={setSecurityAlerts} aria-label="Toggle security alert emails" />
                  </label>
                </div>
              </div>
              <div className="space-y-4">
                <h4 className="font-medium text-foreground">Push Notifications</h4>
                <div className="space-y-3 pl-4 border-l-2">
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">Enable push notifications</p>
                      <p className="text-sm text-foreground/70">Receive real-time alerts in browser</p>
                    </div>
                    <Switch checked={pushNotifications} onCheckedChange={setPushNotifications} aria-label="Toggle push notifications" />
                  </label>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="security">
          <Card>
            <CardHeader>
              <CardTitle>Security Settings</CardTitle>
              <CardDescription>Manage your account security</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Two-Factor Authentication</p>
                    <p className="text-sm text-muted-foreground">Add an extra layer of security</p>
                  </div>
                  <Button variant="outline" disabled title={t('common.comingSoon')} aria-label="Enable 2FA (coming soon)">Enable 2FA</Button>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Passkeys (WebAuthn)</p>
                    <p className="text-sm text-muted-foreground">Use hardware keys or biometrics</p>
                  </div>
                  <Button variant="outline" disabled title={t('common.comingSoon')} aria-label="Add passkey (coming soon)">Add Passkey</Button>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Active Sessions</p>
                    <p className="text-sm text-muted-foreground">Manage your logged-in devices</p>
                  </div>
                  <Button variant="outline" disabled title={t('common.comingSoon')} aria-label="View sessions (coming soon)">View Sessions</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="data">
          <Card>
            <CardHeader>
              <CardTitle>Data & Privacy</CardTitle>
              <CardDescription>Manage your data and privacy settings</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <Button variant="outline" disabled title={t('common.comingSoon')} aria-label="Export my data (coming soon)"><Download className="h-4 w-4 mr-2" aria-hidden="true" /> Export My Data</Button>
                <Button variant="outline" disabled title={t('common.comingSoon')} aria-label="Import data (coming soon)"><Upload className="h-4 w-4 mr-2" aria-hidden="true" /> Import Data</Button>
              </div>
              <div className="p-4 border rounded-lg bg-destructive/10">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-destructive">Delete Account</p>
                    <p className="text-sm text-muted-foreground">Permanently delete your account and all data</p>
                  </div>
                  <Button variant="destructive" disabled title={t('common.comingSoon')} aria-label="Delete account (coming soon)">Delete Account</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="advanced">
          <Card>
            <CardHeader>
              <CardTitle>Advanced Settings</CardTitle>
              <CardDescription>Developer and experimental options</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <label className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Developer Mode</p>
                    <p className="text-sm text-muted-foreground">Enable debug features and verbose logging</p>
                  </div>
                  <Switch checked={developerMode} onCheckedChange={setDeveloperMode} aria-label="Toggle developer mode" />
                </label>
                <label className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Beta Features</p>
                    <p className="text-sm text-muted-foreground">Access experimental features early</p>
                  </div>
                  <Switch checked={betaFeatures} onCheckedChange={setBetaFeatures} aria-label="Toggle beta features" />
                </label>
                <label className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Telemetry</p>
                    <p className="text-sm text-muted-foreground">Send anonymous usage data to improve the product</p>
                  </div>
                  <Switch checked={telemetry} onCheckedChange={setTelemetry} aria-label="Toggle telemetry" />
                </label>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </RequireAuth>
  );
}

