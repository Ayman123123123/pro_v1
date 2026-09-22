'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useTranslation } from 'react-i18next';
import { Settings, Database, HardDrive, CloudUpload, GitBranch, Zap, ToggleLeft, ToggleRight, Plus, Edit, Trash2, Eye, RefreshCw } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

export function SystemPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('flags');

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('system.title')}</h1>
          <p className="text-muted-foreground">{t('system.subtitle')}</p>
        </div>
        <Button><Plus className="h-4 w-4 mr-2" /> Add</Button>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="flags"><Zap className="h-4 w-4 mr-2" /> Feature Flags</TabsTrigger>
          <TabsTrigger value="config"><Settings className="h-4 w-4 mr-2" /> Configuration</TabsTrigger>
          <TabsTrigger value="audit"><Shield className="h-4 w-4 mr-2" /> Audit Logs</TabsTrigger>
          <TabsTrigger value="backups"><CloudUpload className="h-4 w-4 mr-2" /> Backups</TabsTrigger>
          <TabsTrigger value="migrations"><GitBranch className="h-4 w-4 mr-2" /> Migrations</TabsTrigger>
          <TabsTrigger value="cache"><Database className="h-4 w-4 mr-2" /> Cache</TabsTrigger>
        </TabsList>

        <TabsContent value="flags">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>Feature Flags</CardTitle>
                <CardDescription>Percentage rollout, targeting rules, and experimentation</CardDescription>
              </div>
              <Button><Plus className="h-4 w-4 mr-2" /> New Flag</Button>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                {[
                  { key: 'new_ui', name: 'New UI Rollout', enabled: true, rollout: 25, targeting: 'Beta users', updated: '2024-01-15' },
                  { key: 'dark_mode', name: 'Dark Mode', enabled: true, rollout: 100, targeting: 'All users', updated: '2024-01-10' },
                  { key: 'voice_chat', name: 'Voice Chat', enabled: false, rollout: 0, targeting: 'Premium only', updated: '2024-01-05' },
                ].map((flag) => (
                  <div key={flag.key} className="flex items-center justify-between p-4 border rounded-lg">
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <p className="font-medium">{flag.name}</p>
                        <code className="text-xs bg-muted px-2 py-1 rounded">{flag.key}</code>
                      </div>
                      <p className="text-sm text-muted-foreground">Rollout: {flag.rollout}% • Targeting: {flag.targeting}</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <Switch checked={flag.enabled} onCheckedChange={() => {}} />
                      <Button variant="ghost" size="icon"><Edit className="h-4 w-4" /></Button>
                      <Button variant="ghost" size="icon"><Trash2 className="h-4 w-4" /></Button>
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
              <CardTitle>Configuration Management</CardTitle>
              <CardDescription>Key-value configuration with versioning</CardDescription>
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
                        <Button variant="ghost" size="icon"><Eye className="h-4 w-4" /></Button>
                        <Button variant="ghost" size="icon"><Edit className="h-4 w-4" /></Button>
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
              <CardTitle>Audit Logs</CardTitle>
              <CardDescription>Immutable, searchable, exportable audit trail</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-center text-muted-foreground py-12">Audit log viewer with search and export</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="backups">
          <Card>
            <CardHeader>
              <CardTitle>Backup & Restore</CardTitle>
              <CardDescription>Manage database backups and point-in-time recovery</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Full Backup - 2024-01-15 02:00</p>
                    <p className="text-sm text-muted-foreground">Size: 2.4 GB • Status: Completed</p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm"><CloudUpload className="h-4 w-4 mr-1" /> Download</Button>
                    <Button variant="outline" size="sm"><RefreshCw className="h-4 w-4 mr-1" /> Restore</Button>
                  </div>
                </div>
                <div className="flex items-center justify-between p-4 border rounded-lg">
                  <div>
                    <p className="font-medium">Incremental - 2024-01-15 14:00</p>
                    <p className="text-sm text-muted-foreground">Size: 150 MB • Status: Completed</p>
                  </div>
                  <Button variant="outline" size="sm"><RefreshCw className="h-4 w-4 mr-1" /> Restore</Button>
                </div>
                <Button onClick={() => {}}><Plus className="h-4 w-4 mr-2" /> Create Backup Now</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="migrations">
          <Card>
            <CardHeader>
              <CardTitle>Database Migrations</CardTitle>
              <CardDescription>Track and manage database schema migrations</CardDescription>
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
                      <TableCell><Button variant="ghost" size="icon"><Eye className="h-4 w-4" /></Button></TableCell>
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
              <CardTitle>Cache Management</CardTitle>
              <CardDescription>Redis cache monitoring and invalidation</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-3">
                <Card className="p-4"><p className="text-sm text-muted-foreground">Hit Rate</p><p className="text-3xl font-bold">94.2%</p></Card>
                <Card className="p-4"><p className="text-sm text-muted-foreground">Memory Used</p><p className="text-3xl font-bold">1.2 GB</p></Card>
                <Card className="p-4"><p className="text-sm text-muted-foreground">Keys</p><p className="text-3xl font-bold">1.5M</p></Card>
              </div>
              <Button variant="outline" className="mt-4"><RefreshCw className="h-4 w-4 mr-2" /> Flush Cache</Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

import { Shield } from 'lucide-react';
