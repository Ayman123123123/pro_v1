'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Switch } from '@/components/ui/switch';
import { useTranslation } from 'react-i18next';
import { Shield, Users, Key, Fingerprint, Database, Wifi, Plus, Edit, Trash2, Eye, RefreshCw, RotateCcw } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { RequireAuth, DemoBanner, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';

export function SecurityPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('roles');
  // ✅ 2026-09-24: ربط المفاتيح الوهمية بحالة محلية بدل onCheckedChange فارغ
  const [oidcEnabled, setOidcEnabled] = useState(true);
  const [samlEnabled, setSamlEnabled] = useState(false);
  const [totpEnabled, setTotpEnabled] = useState(true);
  const [webauthnEnabled, setWebauthnEnabled] = useState(true);

  return (
    <RequireAuth>
    <div className="space-y-6">
      <DemoBanner />
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('security.title')}</h1>
          <p className="text-foreground/70">{t('security.subtitle')}</p>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="roles" className={TABS_TRIGGER_CLASS}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.roles')}</TabsTrigger>
          <TabsTrigger value="sso" className={TABS_TRIGGER_CLASS}><Wifi className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.sso')}</TabsTrigger>
          <TabsTrigger value="mfa" className={TABS_TRIGGER_CLASS}><Fingerprint className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.mfa')}</TabsTrigger>
          <TabsTrigger value="sessions" className={TABS_TRIGGER_CLASS}><Database className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.sessions')}</TabsTrigger>
          <TabsTrigger value="api-keys" className={TABS_TRIGGER_CLASS}><Key className="h-4 w-4 mr-2" aria-hidden="true" /> {t('security.apiKeys')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="roles">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('security.roles')}</CardTitle>
                <CardDescription>{t('security.rolesDesc')}</CardDescription>
              </div>
              <Button disabled title={t('common.comingSoon')} aria-label={`${t('common.create')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.create')}</Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Role</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Users</TableHead>
                    <TableHead>Permissions</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[
                    { name: 'SUPER_ADMIN', desc: 'Full system access', users: 2, perms: 45, status: 'active' },
                    { name: 'ADMIN', desc: 'Administrative access', users: 5, perms: 32, status: 'active' },
                    { name: 'MODERATOR', desc: 'Content moderation', users: 12, perms: 18, status: 'active' },
                    { name: 'SUPPORT', desc: 'Customer support', users: 8, perms: 12, status: 'active' },
                    { name: 'VIEWER', desc: 'Read-only access', users: 25, perms: 5, status: 'active' },
                  ].map((role) => (
                    <TableRow key={role.name}>
                      <TableCell><Badge variant="outline">{role.name}</Badge></TableCell>
                      <TableCell>{role.desc}</TableCell>
                      <TableCell>{role.users}</TableCell>
                      <TableCell>{role.perms}</TableCell>
                      <TableCell><Badge variant="success">{role.status}</Badge></TableCell>
                      <TableCell>
                        {/* ✅ 2026-09-24: أزرار وهمية بلا onClick → تعطيل معلن */}
                        <Button variant="ghost" size="icon" aria-label={`${t('common.search')} ${role.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Eye className="h-4 w-4" aria-hidden="true" /></Button>
                        <Button variant="ghost" size="icon" aria-label={`${t('common.edit')} ${role.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Edit className="h-4 w-4" aria-hidden="true" /></Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="sso">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.sso')}</CardTitle>
              <CardDescription>{t('security.ssoDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="p-4 border rounded-lg">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">OIDC Provider</p>
                      <p className="text-sm text-foreground/70">Google Workspace • {t('common.enabled')}</p>
                    </div>
                    <Switch checked={oidcEnabled} onCheckedChange={setOidcEnabled} aria-label="Toggle OIDC provider" />
                  </div>
                </div>
                <div className="p-4 border rounded-lg">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-foreground">SAML Provider</p>
                      <p className="text-sm text-foreground/70">Okta • {t('common.disabled')}</p>
                    </div>
                    <Switch checked={samlEnabled} onCheckedChange={setSamlEnabled} aria-label="Toggle SAML provider" />
                  </div>
                </div>
                <Button variant="outline" disabled title={t('common.comingSoon')} aria-label={`${t('common.create')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.create')}</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="mfa">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.mfa')}</CardTitle>
              <CardDescription>{t('security.mfaDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-2">
                <Card className="p-6">
                  <Fingerprint className="h-8 w-8 text-primary mb-2" aria-hidden="true" />
                  <h4 className="font-medium mb-2 text-foreground">TOTP (Authenticator Apps)</h4>
                  <p className="text-sm text-foreground/70 mb-4">Time-based one-time passwords via Google Authenticator, Authy, etc.</p>
                  <div className="flex items-center gap-2">
                    <Switch checked={totpEnabled} onCheckedChange={setTotpEnabled} aria-label="Toggle TOTP authentication" />
                    <span className="text-sm font-medium text-foreground">{totpEnabled ? t('common.enabled') : t('common.disabled')}</span>
                  </div>
                </Card>
                <Card className="p-6">
                  <Key className="h-8 w-8 text-primary mb-2" aria-hidden="true" />
                  <h4 className="font-medium mb-2 text-foreground">WebAuthn (Passkeys)</h4>
                  <p className="text-sm text-foreground/70 mb-4">Hardware security keys and biometric authentication</p>
                  <div className="flex items-center gap-2">
                    <Switch checked={webauthnEnabled} onCheckedChange={setWebauthnEnabled} aria-label="Toggle WebAuthn passkeys" />
                    <span className="text-sm font-medium text-foreground">{webauthnEnabled ? t('common.enabled') : t('common.disabled')}</span>
                  </div>
                </Card>
              </div>
              <div className="mt-6 grid gap-4 md:grid-cols-3">
                <Card className="p-4"><p className="text-sm text-foreground/70">MFA Enrollment</p><p className="text-3xl font-bold text-foreground">67%</p></Card>
                <Card className="p-4"><p className="text-sm text-foreground/70">WebAuthn Users</p><p className="text-3xl font-bold text-foreground">1,234</p></Card>
                <Card className="p-4"><p className="text-sm text-foreground/70">Recovery Codes Used</p><p className="text-3xl font-bold text-foreground">23</p></Card>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="sessions">
          <Card>
            <CardHeader>
              <CardTitle>{t('security.sessions')}</CardTitle>
              <CardDescription>{t('security.sessionsDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>User</TableHead>
                    <TableHead>Device</TableHead>
                    <TableHead>IP Address</TableHead>
                    <TableHead>Location</TableHead>
                    <TableHead>Last Active</TableHead>
                    <TableHead>Trusted</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[
                    { user: 'admin', device: 'Chrome on macOS', ip: '192.168.1.1', location: 'San Francisco, US', active: '2 min ago', trusted: true },
                    { user: 'mod1', device: 'Firefox on Windows', ip: '10.0.0.5', location: 'New York, US', active: '1 hour ago', trusted: false },
                  ].map((s, i) => (
                    <TableRow key={i}>
                      <TableCell>{s.user}</TableCell>
                      <TableCell>{s.device}</TableCell>
                      <TableCell>{s.ip}</TableCell>
                      <TableCell>{s.location}</TableCell>
                      <TableCell>{s.active}</TableCell>
                      <TableCell><Badge variant={s.trusted ? 'success' : 'outline'}>{s.trusted ? 'Trusted' : 'Untrusted'}</Badge></TableCell>
                      {/* ✅ 2026-09-24: زر وهمي بلا onClick → تعطيل معلن */}
                      <TableCell><Button variant="ghost" size="icon" className="text-red-500" aria-label={`Revoke session of ${s.user} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><RotateCcw className="h-4 w-4" aria-hidden="true" /></Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="api-keys">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('security.apiKeys')}</CardTitle>
                <CardDescription>{t('security.apiKeysDesc')}</CardDescription>
              </div>
              <Button disabled title={t('common.comingSoon')} aria-label={`${t('common.create')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.create')}</Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Key Prefix</TableHead>
                    <TableHead>Scopes</TableHead>
                    <TableHead>Created</TableHead>
                    <TableHead>Last Used</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[
                    { name: 'Mobile App', prefix: 'sk_live_abc...', scopes: 'read:users write:messages', created: '2024-01-10', used: '2 min ago', status: 'active' },
                    { name: 'Analytics Pipeline', prefix: 'sk_live_xyz...', scopes: 'read:analytics', created: '2024-01-05', used: '1 hour ago', status: 'active' },
                    { name: 'Legacy Integration', prefix: 'sk_live_old...', scopes: 'read:all', created: '2023-12-01', used: 'Never', status: 'revoked' },
                  ].map((k, i) => (
                    <TableRow key={i}>
                      <TableCell>{k.name}</TableCell>
                      <TableCell><code>{k.prefix}</code></TableCell>
                      <TableCell><Badge variant="outline">{k.scopes}</Badge></TableCell>
                      <TableCell>{k.created}</TableCell>
                      <TableCell>{k.used}</TableCell>
                      <TableCell><Badge variant={k.status === 'active' ? 'success' : 'destructive'}>{k.status}</Badge></TableCell>
                      <TableCell>
                        {/* ✅ 2026-09-24: أزرار وهمية بلا onClick → تعطيل معلن */}
                        <Button variant="ghost" size="icon" aria-label={`View key ${k.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Eye className="h-4 w-4" aria-hidden="true" /></Button>
                        <Button variant="ghost" size="icon" aria-label={`Rotate key ${k.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><RotateCcw className="h-4 w-4" aria-hidden="true" /></Button>
                        <Button variant="ghost" size="icon" className="text-red-500" aria-label={`Revoke key ${k.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </RequireAuth>
  );
}

