'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';
import { Shield, Flag, CheckCircle, XCircle, AlertTriangle, Search, Eye, MoreVertical, Settings, BarChart3, Send } from 'lucide-react';
import { cn } from '@/utils/cn';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { RequireAuth, EmptyState, LoadingState, ErrorState, formatSafeDate, formatSafeTime, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';
import { apiFetch } from '../api';

interface AdminReport {
  id: string;
  reporterRedId: string;
  reportedRedId: string | null;
  category: string;
  details: string | null;
  status: string;
  createdAt: string;
}

const STATUSES = ['OPEN', 'REVIEWING', 'RESOLVED', 'DISMISSED'] as const;

async function fetchReports(status: string): Promise<AdminReport[]> {
  const res = await apiFetch(`/api/admin/moderation/reports?status=${encodeURIComponent(status)}`);
  const data = await res.json().catch(() => []);
  if (!res.ok) {
    const err = (data && typeof data === 'object' ? (data as Record<string, unknown>).error : null) as string | null;
    throw new Error(err || `HTTP ${res.status}`);
  }
  return Array.isArray(data) ? (data as AdminReport[]) : [];
}

async function updateReport(reportId: string, status: string): Promise<void> {
  const res = await apiFetch(
    `/api/admin/moderation/reports/${encodeURIComponent(reportId)}?status=${encodeURIComponent(status)}`,
    { method: 'PATCH' }
  );
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    const err = (data && typeof data === 'object' ? (data as Record<string, unknown>).error : null) as string | null;
    throw new Error(err || `HTTP ${res.status}`);
  }
}

export function ModerationPage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('queue');
  const [search, setSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('OPEN');
  const [items, setItems] = useState<AdminReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(async (status = filterStatus) => {
    setLoading(true);
    setLoadError(null);
    try {
      setItems(await fetchReports(status));
    } catch (e) {
      setItems([]);
      setLoadError(e instanceof Error ? e.message : 'تعذّر تحميل البلاغات');
    } finally {
      setLoading(false);
    }
  }, [filterStatus]);

  useEffect(() => { load(); }, [load]);

  const handleAction = async (action: 'review' | 'resolve' | 'dismiss', item: AdminReport) => {
    const next = action === 'review' ? 'REVIEWING' : action === 'resolve' ? 'RESOLVED' : 'DISMISSED';
    setActingId(item.id);
    try {
      await updateReport(item.id, next);
      await load();
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'تعذّر تحديث البلاغ');
    } finally {
      setActingId(null);
    }
  };

  const filteredData = items.filter((item) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      (item.details ?? '').toLowerCase().includes(q) ||
      item.reporterRedId.toLowerCase().includes(q) ||
      (item.reportedRedId ?? '').toLowerCase().includes(q) ||
      item.category.toLowerCase().includes(q)
    );
  });

  return (
    <RequireAuth>
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('moderation.title')}</h1>
          <p className="text-foreground/70">{t('moderation.subtitle')}</p>
        </div>
        <Button disabled title={t('common.comingSoon')} aria-label={`${t('analytics.autoModerate')} (${t('common.comingSoon')})`}><Shield className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.autoModerate')}</Button>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="queue" className={TABS_TRIGGER_CLASS}><Flag className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.queue')}</TabsTrigger>
          <TabsTrigger value="rules" className={TABS_TRIGGER_CLASS}><Settings className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.rules')}</TabsTrigger>
          <TabsTrigger value="analytics" className={TABS_TRIGGER_CLASS}><BarChart3 className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.analytics')}</TabsTrigger>
          <TabsTrigger value="broadcast" className={TABS_TRIGGER_CLASS}><Send className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.broadcast')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="queue">
          <Card>
            <CardHeader>
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <CardTitle>{t('moderation.queue')}</CardTitle>
                  <CardDescription>{t('moderation.queueDesc')}</CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-foreground/70" aria-hidden="true" />
                    <Input placeholder={t('moderation.searchPlaceholder')} value={search} onChange={e => setSearch(e.target.value)} className="pl-10 w-[250px]" aria-label={t('moderation.searchPlaceholder')} />
                  </div>
                  <Select value={filterStatus} onValueChange={(v) => { setFilterStatus(v); load(v); }}>
                    <SelectTrigger className="w-[140px]" aria-label={t('moderation.statusFilter')}><SelectValue placeholder={t('moderation.statusFilter')} /></SelectTrigger>
                    <SelectContent>
                      {STATUSES.map((s) => (
                        <SelectItem key={s} value={s}>{s}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <LoadingState />
              ) : loadError ? (
                <ErrorState message={loadError} onRetry={() => load()} />
              ) : filteredData.length === 0 ? (
                <EmptyState message={t('common.noData')} />
              ) : (
              <div className="space-y-3">
                {filteredData.map((item) => (
                  <div key={item.id} className="border rounded-lg p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-2">
                          <Badge variant="outline">{item.category}</Badge>
                          <Badge
                            variant={item.status === 'OPEN' ? 'warning' : item.status === 'REVIEWING' ? 'info' : item.status === 'RESOLVED' ? 'success' : 'destructive'}
                          >
                            {item.status}
                          </Badge>
                        </div>
                        <p className="text-foreground/70 mb-2">{item.details || t('common.empty')}</p>
                        <div className="flex items-center gap-4 text-sm text-foreground/70">
                          <span>Reporter: {item.reporterRedId || '—'}</span>
                          <span>Reported: {item.reportedRedId ?? '—'}</span>
                          <span>{item.createdAt ? `${formatSafeDate(item.createdAt)} ${formatSafeTime(item.createdAt)}` : '—'}</span>
                        </div>
                      </div>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" aria-label={`Moderation actions for report ${item.id}`} disabled={actingId === item.id}>
                            <MoreVertical className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuLabel>Actions</DropdownMenuLabel>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem onClick={() => handleAction('review', item)} className="text-blue-600">
                            <Eye className="h-4 w-4 mr-2" /> Mark reviewing
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('resolve', item)} className="text-green-600">
                            <CheckCircle className="h-4 w-4 mr-2" /> Resolve
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction('dismiss', item)} className="text-red-600">
                            <XCircle className="h-4 w-4 mr-2" /> Dismiss
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </div>
                ))}
              </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="rules">
          <Card>
            <CardHeader>
              <CardTitle>{t('moderation.rules')}</CardTitle>
              <CardDescription>{t('moderation.rulesDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics">
          <Card>
            <CardHeader>
              <CardTitle>{t('moderation.analytics')}</CardTitle>
              <CardDescription>{t('moderation.analyticsDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="broadcast">
          <Card>
            <CardHeader>
              <CardTitle>{t('moderation.broadcast')}</CardTitle>
              <CardDescription>{t('moderation.broadcastDesc')}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className={cn('max-w-2xl space-y-2')}>
                <p className="flex items-center gap-2 text-sm text-foreground/70">
                  <AlertTriangle className="h-4 w-4" aria-hidden="true" />
                  {t('common.comingSoon')} — {t('moderation.noSendYet')}
                </p>
                <Button disabled title={t('common.comingSoon')} aria-label={`${t('moderation.sendBroadcast')} (${t('common.comingSoon')})`}><Send className="h-4 w-4 mr-2" aria-hidden="true" /> {t('moderation.sendBroadcast')}</Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </RequireAuth>
  );
}
