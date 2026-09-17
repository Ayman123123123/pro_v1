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

export function SettingsPage() {
  const { t } = useTranslation();
  const { theme, setTheme, resolvedTheme } = useTheme();
  const [activeTab, setActiveTab] = useState<'appearance' | 'notifications' | 'security' | 'data' | 'advanced'>('appearance');
  const [language, setLanguage] = useState('ar');
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [pushNotifications, setPushNotifications] = useState(true);
  const [weeklyDigest, setWeeklyDigest] = useState(false);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('settings.title')}</h1>
        <p className="text-muted-foreground">{t('settings.subtitle')}</p>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="appearance"><Palette className="h-4 w-4 mr-2" /> Appearance</TabsTrigger>
          <TabsTrigger value="notifications"><Bell className="h-4 w-4 mr-2" /> Notifications</TabsTrigger>
          <TabsTrigger value="security"><Shield className="h-4 w-4 mr-2" /> Security</TabsTrigger>
          <TabsTrigger value="data"><Database className="h-4 w-4 mr-2" /> Data</TabsTrigger>
          <TabsTrigger value="advanced"><Settings className="h-4 w-4 mr-2" /> Advanced</TabsTrigger>
        </TabsList>

        <TabsContent value="appearance">
          <div className="grid gap-6 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2"><Palette className="h-5 w-5" /> Theme</CardTitle>
                <CardDescription>Choose your preferred color scheme</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-3 gap-4">
                  {(['light', 'dark', 'system'] as const).map((t) => (
                    <button
                      key={t}
                      onClick={() => setTheme(t)}
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
                <CardTitle className="flex items-center gap-2"><Globe className="h-5 w-5" /> Language & Region</CardTitle>
                <CardDescription>Set your preferred language and locale</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>Language</Label>
                  <Select value={language} onValueChange={setLanguage}>
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
                <h4 className="font-medium">Email Notifications</h4>
                <div className="space-y-3 pl-4 border-l-2">
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">All notifications</p>
                      <p className="text-sm text-muted-foreground">Receive email for every notification</p>
                    </div>
                    <Switch checked={emailNotifications} onCheckedChange={setEmailNotifications} />
                  </label>
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">Weekly digest</p>
                      <p className="text-sm text-muted-foreground">Summary of weekly activity</p>
                    </div>
                    <Switch checked={weeklyDigest} onCheckedChange={setWeeklyDigest} />
                  </label>
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">Security alerts</p>
                      <p className="text-sm text-muted-foreground">Critical security events</p>
                    </div>
                    <Switch checked={true} onCheckedChange={() => {}} />
                  </label>
                </div>
              </div>
              <div className="space-y-4">
                <h4 className="font-medium">Push Notifications</h4>
                <div className="space-y-3 pl-4 border-l-2">
                  <label className="flex items-center justify-between">
                    <div>
                      <p className="font-medium">Enable push notifications</p>
                      <p className="text-sm text-muted-foreground">Receive real-time alerts in browser</p>
                    </div>
                    <Switch checked={pushNotifications} onCheckedChange={setPushNotifications} />
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
                  <Button variant="outline">Enable 2FA</Button>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Passkeys (WebAuthn)</p>
                    <p className="text-sm text-muted-foreground">Use hardware keys or biometrics</p>
                  </div>
                  <Button variant="outline">Add Passkey</Button>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Active Sessions</p>
                    <p className="text-sm text-muted-foreground">Manage your logged-in devices</p>
                  </div>
                  <Button variant="outline">View Sessions</Button>
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
                <Button variant="outline"><Download className="h-4 w-4 mr-2" /> Export My Data</Button>
                <Button variant="outline"><Upload className="h-4 w-4 mr-2" /> Import Data</Button>
              </div>
              <div className="p-4 border rounded-lg bg-destructive/10">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-destructive">Delete Account</p>
                    <p className="text-sm text-muted-foreground">Permanently delete your account and all data</p>
                  </div>
                  <Button variant="destructive">Delete Account</Button>
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
                  <Switch checked={false} onCheckedChange={() => {}} />
                </label>
                <label className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Beta Features</p>
                    <p className="text-sm text-muted-foreground">Access experimental features early</p>
                  </div>
                  <Switch checked={false} onCheckedChange={() => {}} />
                </label>
                <label className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">Telemetry</p>
                    <p className="text-sm text-muted-foreground">Send anonymous usage data to improve the product</p>
                  </div>
                  <Switch checked={true} onCheckedChange={() => {}} />
                </label>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

import { Label } from '@/components/ui/label';