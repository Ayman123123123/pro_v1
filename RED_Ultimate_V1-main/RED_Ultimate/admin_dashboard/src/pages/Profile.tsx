'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Input } from '@/components/ui/input';
import { useTranslation } from 'react-i18next';
import { User, Settings, Activity, Smartphone, Monitor, LogOut, Edit, Key, Shield } from 'lucide-react';
import { cn } from '@/utils/cn';
import { useAuth } from '@/components/providers/AuthProvider';
import { useNavigate } from '@tanstack/react-router';
import { RequireAuth, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';

export function ProfilePage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<string>('profile');
  const [savedAt, setSavedAt] = useState<string | null>(null);

  return (
    <RequireAuth>
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('settings.profile')}</h1>
        <p className="text-muted-foreground">Manage your account and security</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-4">
        {/* Sidebar Profile */}
        <Card className="lg:col-span-1">
          <CardContent className="p-6 text-center">
            <Avatar className="h-24 w-24 mx-auto mb-4">
              <AvatarImage src={user?.avatarUrl} alt={user?.displayName || user?.username} />
              <AvatarFallback className="text-3xl">
                {(user?.displayName || user?.username || 'U').charAt(0).toUpperCase()}
              </AvatarFallback>
            </Avatar>
            <h2 className="text-xl font-bold">{user?.displayName || user?.username}</h2>
            <p className="text-muted-foreground">@{user?.username}</p>
            <Badge variant="outline" className="mt-2">{user?.role || 'USER'}</Badge>
            <div className="mt-4 pt-4 border-t space-y-2">
              <Button variant="outline" className="w-full" onClick={() => setActiveTab('profile')}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> Edit Profile</Button>
              <Button variant="outline" className="w-full" disabled title={t('common.comingSoon')} aria-label="Change password (coming soon)"><Key className="h-4 w-4 mr-2" aria-hidden="true" /> Change Password</Button>
              <Button variant="outline" className="w-full" onClick={() => navigate({ to: '/settings' })}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> Security Settings</Button>
            </div>
          </CardContent>
        </Card>

        {/* Main Content */}
        <div className="lg:col-span-3 space-y-6">
          <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
            <div className={TABS_WRAP_CLASS}>
            <TabsList className={TABS_LIST_CLASS}>
              <TabsTrigger value="profile" className={TABS_TRIGGER_CLASS}><User className="h-4 w-4 mr-2" aria-hidden="true" /> Profile</TabsTrigger>
              <TabsTrigger value="devices" className={TABS_TRIGGER_CLASS}><Smartphone className="h-4 w-4 mr-2" aria-hidden="true" /> Devices</TabsTrigger>
              <TabsTrigger value="sessions" className={TABS_TRIGGER_CLASS}><Monitor className="h-4 w-4 mr-2" aria-hidden="true" /> Sessions</TabsTrigger>
              <TabsTrigger value="activity" className={TABS_TRIGGER_CLASS}><Activity className="h-4 w-4 mr-2" aria-hidden="true" /> Activity Log</TabsTrigger>
            </TabsList>
            </div>

            <TabsContent value="profile">
              <Card>
                <CardHeader>
                  <CardTitle>Profile Information</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4 max-w-md">
                  <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2">
                      <Label>Display Name</Label>
                      <Input defaultValue={user?.displayName} />
                    </div>
                    <div className="space-y-2">
                      <Label>Username</Label>
                      <Input defaultValue={user?.username} disabled />
                    </div>
                    <div className="space-y-2">
                      <Label>Email</Label>
                      <Input type="email" defaultValue={user?.email} />
                    </div>
                    <div className="space-y-2">
                      <Label>Phone</Label>
                      <Input defaultValue={user?.phone} />
                    </div>
                  </div>
                  <Button onClick={() => setSavedAt(new Date().toLocaleTimeString())}><Edit className="h-4 w-4 mr-2" aria-hidden="true" /> Save Changes</Button>
                  {savedAt && (
                    <p role="status" className="text-sm font-medium text-foreground">{t('common.success')} • {savedAt}</p>
                  )}
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="devices">
              <Card>
                <CardHeader>
                  <CardTitle>Trusted Devices</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[
                      { name: 'MacBook Pro', browser: 'Chrome 120', os: 'macOS 14.2', lastActive: 'Now', trusted: true, current: true },
                      { name: 'iPhone 15', browser: 'Safari', os: 'iOS 17.2', lastActive: '2 hours ago', trusted: true, current: false },
                      { name: 'Windows PC', browser: 'Firefox 121', os: 'Windows 11', lastActive: '3 days ago', trusted: false, current: false },
                    ].map((d, i) => (
                      <div key={i} className="flex items-center justify-between p-4 border rounded-lg">
                        <div className="flex items-center gap-4">
                          <div className="p-3 bg-primary/10 rounded-lg">
                            <Monitor className="h-6 w-6 text-primary" />
                          </div>
                          <div>
                            <p className="font-medium">{d.name} {d.current && <Badge variant="secondary" className="ml-2">Current</Badge>}</p>
                            <p className="text-sm text-muted-foreground">{d.browser} on {d.os}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-4">
                          <span className="text-sm text-muted-foreground">Last active: {d.lastActive}</span>
                          <Badge variant={d.trusted ? 'success' : 'outline'}>
                            {d.trusted ? 'Trusted' : 'Untrusted'}
                          </Badge>
                          <Button variant="ghost" size="icon" className="text-red-500" aria-label={`Sign out device ${d.name}`}><LogOut className="h-4 w-4" aria-hidden="true" /></Button>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="sessions">
              <Card>
                <CardHeader>
                  <CardTitle>Active Sessions</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    {[
                      { device: 'Chrome on macOS', ip: '192.168.1.100', location: 'Cairo, Egypt', time: 'Active now', current: true },
                      { device: 'Safari on iOS', ip: '192.168.1.100', location: 'Cairo, Egypt', time: '2 hours ago', current: false },
                      { device: 'Firefox on Windows', ip: '10.0.0.5', location: 'Alexandria, Egypt', time: '1 day ago', current: false },
                    ].map((s, i) => (
                      <div key={i} className="flex items-center justify-between p-4 border rounded-lg">
                        <div className="flex items-center gap-4">
                          <Monitor className="h-6 w-6 text-muted-foreground" />
                          <div>
                            <p className="font-medium">{s.device} {s.current && <Badge variant="secondary" className="ml-2">Current</Badge>}</p>
                            <p className="text-sm text-muted-foreground">{s.ip} • {s.location}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-sm text-muted-foreground">{s.time}</span>
                          {!s.current && <Button variant="ghost" size="icon" className="text-red-500" aria-label={`End session ${s.device}`}><LogOut className="h-4 w-4" aria-hidden="true" /></Button>}
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="activity">
              <Card>
                <CardHeader>
                  <CardTitle>Activity Log</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    {[
                      { action: 'Login', detail: 'Successful login from Chrome on macOS', time: '2 min ago', ip: '192.168.1.100' },
                      { action: 'Settings Updated', detail: 'Changed theme to dark mode', time: '5 min ago', ip: '192.168.1.100' },
                      { action: 'Password Changed', detail: 'Password updated successfully', time: '1 hour ago', ip: '192.168.1.100' },
                      { action: '2FA Enabled', detail: 'TOTP authentication enabled', time: '2 days ago', ip: '192.168.1.100' },
                    ].map((a, i) => (
                      <div key={i} className="flex items-center justify-between p-4 border rounded-lg">
                        <div className="flex items-center gap-4">
                          <Activity className="h-6 w-6 text-primary" />
                          <div>
                            <p className="font-medium">{a.action}</p>
                            <p className="text-sm text-muted-foreground">{a.detail}</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="text-sm text-muted-foreground">{a.time}</p>
                          <p className="text-xs text-muted-foreground">{a.ip}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
    </RequireAuth>
  );
}

import { useState } from 'react';
import { Label } from '@/components/ui/label';