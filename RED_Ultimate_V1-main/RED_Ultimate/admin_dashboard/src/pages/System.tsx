'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useTranslation } from 'react-i18next';
import { Settings, Database, HardDrive, CloudUpload, GitBranch, Zap, ToggleLeft, ToggleRight, Plus, Edit, Trash2, Eye, RefreshCw, Shield } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { RequireAuth, DemoBanner, EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';

const INITIAL_FLAGS = [
  { key: 'new_ui', name: 'New UI Rollout', enabled: true, rollout: 25, targeting: 'Beta users', updated: '2024-01-15' },
  { key: 'dark_mode', name: 'Dark Mode', enabled: true, rollout: 100, targeting: 'All users', updated: '2024-01-10' },
  { key: 'voice_chat', name: 'Voice Chat', enabled: false, rollout: 0, targeting: 'Premium only', updated: '2024-01-05' },
];

export function SystemPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('flags');
  // ✅ 2026-09-24: ربط المفاتيح الوهمية بحالة محلية
  const [flags, setFlags] = useState(INITIAL_FLAGS);
  const [lastFlushed, setLastFlushed] = useState<string | null>(null);

  return (
    <RequireAuth>
    <div className="space-y-6">
      <DemoBanner />
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('system.title')}</h1>
          <p className="text-foreground/70">{t('system.subtitle')}</p>
        </div>
        <Button disabled title={t('common.comingSoon')} aria-label={`${t('common.create')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.create')}</Button>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="flags" className={TABS_TRIGGER_CLASS}><Zap className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.featureFlags')}</TabsTrigger>
          <TabsTrigger value="config" className={TABS_TRIGGER_CLASS}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.configuration')}</TabsTrigger>
          <TabsTrigger value="audit" className={TABS_TRIGGER_CLASS}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.auditLogs')}</TabsTrigger>
          <TabsTrigger value="backups" className={TABS_TRIGGER_CLASS}><CloudUpload className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.backups')}</TabsTrigger>
          <TabsTrigger value="migrations" className={TABS_TRIGGER_CLASS}><GitBranch className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.migrations')}</TabsTrigger>
          <TabsTrigger value="cache" className={TABS_TRIGGER_CLASS}><Database className="h-4 w-4 mr-2" aria-hidden="true" /> {t('system.cache')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="flags">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('system.featureFlags')}</CardTitle>
                <CardDescription>{t('systemPage.healthDesc')}</CardDescription>
              </div>
              <Button disabled title={t('common.comingSoon')} aria-label={`${t('common.create')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.create')}</Button>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {flags.map((flag) => (
                  <div key={flag.key} className="flex items-center justify-between p-4 border rounded-lg">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="font-medium text-foreground">{flag.name}</p>
                        <code className="text-xs bg-muted px-2 py-1 rounded">{flag.key}</code>
                      </div>
                      <p className="text-sm text-foreground/70">Rollout: {flag.rollout}% • Targeting: {flag.targeting}</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <Switch
                        checked={flag.enabled}
                        onCheckedChange={(v) => setFlags((prev) => prev.map((f) => (f.key === flag.key ? { ...f, enabled: v } : f)))}
                        aria-label={`Toggle flag ${flag.name}`}
                      />
                      {/* ✅ 2026-09-24: زرّان وهميان بلا onClick → تعطيل معلن */}
                      <Button variant="ghost" size="icon" aria-label={`Edit flag ${flag.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Edit className="h-4 w-4" aria-hidden="true" /></Button>
                      <Button variant="ghost" size="icon" aria-label={`Delete flag ${flag.name} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="config">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.configuration')}</CardTitle>
              <CardDescription>{t('systemPage.configDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Key</TableHead>
                    <TableHead>Value</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Version</TableHead>
                    <TableHead>Updated</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[
                    { key: 'app.name', value: 'UNES', type: 'string', version: 5, updated: '2024-01-15' },
                    { key: 'app.max_upload_mb', value: '100', type: 'number', version: 2, updated: '2024-01-10' },
                    { key: 'features.voice_enabled', value: 'false', type: 'boolean', version: 1, updated: '2024-01-05' },
                  ].map((config) => (
                    <TableRow key={config.key}>
                      <TableCell><code>{config.key}</code></TableCell>
                      <TableCell><code>{config.value}</code></TableCell>
                      <TableCell><Badge variant="outline">{config.type}</Badge></TableCell>
                      <TableCell>v{config.version}</TableCell>
                      <TableCell>{config.updated}</TableCell>
                      <TableCell>
                        {/* ✅ 2026-09-24: زرّان وهميان بلا onClick → تعطيل معلن */}
                        <Button variant="ghost" size="icon" aria-label={`View config ${config.key} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Eye className="h-4 w-4" aria-hidden="true" /></Button>
                        <Button variant="ghost" size="icon" aria-label={`Edit config ${config.key} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Edit className="h-4 w-4" aria-hidden="true" /></Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.auditLogs')}</CardTitle>
              <CardDescription>{t('systemPage.auditDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="backups">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.backups')}</CardTitle>
              <CardDescription>{t('systemPage.backupsDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Full Backup - 2024-01-15 02:00</p>
                    <p className="text-sm text-muted-foreground">Size: 2.4 GB • Status: Completed</p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" disabled title={t('common.comingSoon')} aria-label="Download backup (coming soon)"><CloudUpload className="h-4 w-4 mr-1" aria-hidden="true" /> Download</Button>
                    <Button variant="outline" size="sm" disabled title={t('common.comingSoon')} aria-label="Restore backup (coming soon)"><RefreshCw className="h-4 w-4 mr-1" aria-hidden="true" /> Restore</Button>
                  </div>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Incremental - 2024-01-15 14:00</p>
                    <p className="text-sm text-muted-foreground">Size: 150 MB • Status: Completed</p>
                  </div>
                    <Button variant="outline" size="sm" disabled title={t('common.comingSoon')} aria-label="Restore incremental backup (coming soon)"><RefreshCw className="h-4 w-4 mr-1" aria-hidden="true" /> Restore</Button>
                </div>
                <Button disabled title={t('common.comingSoon')} aria-label="Create backup now (coming soon)"><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> Create Backup Now</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="migrations">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.migrations')}</CardTitle>
              <CardDescription>{t('systemPage.migrationsDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Version</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Applied At</TableHead>
                    <TableHead>Duration</TableHead>
                    <TableHead>Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {[
                    { version: '2024011501', description: 'Add user preferences table', status: 'applied', applied: '2024-01-15 10:30', duration: '2.3s' },
                    { version: '2024011401', description: 'Add message reactions', status: 'applied', applied: '2024-01-14 15:00', duration: '1.1s' },
                    { version: '2024011601', description: 'Add analytics events table', status: 'pending', applied: '—', duration: '—' },
                  ].map((m) => (
                    <TableRow key={m.version}>
                      <TableCell><code>{m.version}</code></TableCell>
                      <TableCell>{m.description}</TableCell>
                      <TableCell><Badge variant={m.status === 'applied' ? 'success' : 'warning'}>{m.status}</Badge></TableCell>
                      <TableCell>{m.applied}</TableCell>
                      <TableCell>{m.duration}</TableCell>
                      <TableCell><Button variant="ghost" size="icon" aria-label={`View migration ${m.version} (${t('common.comingSoon')})`} disabled title={t('common.comingSoon')}><Eye className="h-4 w-4" aria-hidden="true" /></Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="cache">
          <Card>
            <CardHeader>
              <CardTitle>{t('system.cache')}</CardTitle>
              <CardDescription>{t('systemPage.cacheDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-3">
                <Card className="p-4"><p className="text-sm text-muted-foreground">Hit Rate</p><p className="text-3xl font-bold">94.2%</p></Card>
                <Card className="p-4"><p className="text-sm text-muted-foreground">Memory Used</p><p className="text-3xl font-bold">1.2 GB</p></Card>
                <Card className="p-4"><p className="text-sm text-muted-foreground">Keys</p><p className="text-3xl font-bold">1.5M</p></Card>
              </div>
              <Button variant="outline" className="mt-4" onClick={() => setLastFlushed(new Date().toLocaleTimeString())} aria-label="Flush cache"><RefreshCw className="h-4 w-4 mr-2" aria-hidden="true" /> Flush Cache</Button>
              {lastFlushed && (
                <p role="status" className="mt-2 text-sm font-medium text-foreground">{t('common.success')} • {lastFlushed}</p>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </RequireAuth>
  );
}
