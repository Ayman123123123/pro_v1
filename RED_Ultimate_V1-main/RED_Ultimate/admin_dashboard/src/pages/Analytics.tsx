'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useTranslation } from 'react-i18next';
import {
  BarChart3,
  LineChart,
  PieChart,
  Users,
  TrendingUp,
  Download,
  Filter,
  Calendar,
  Plus,
  Settings,
  Play,
  Pause,
  Clock,
  Send,
} from 'lucide-react';
import { cn } from '@/utils/cn';
import { RequireAuth, DemoBanner, EmptyState, TABS_LIST_CLASS, TABS_TRIGGER_CLASS, TABS_WRAP_CLASS } from './_shared';

export function AnalyticsPage() {
  const { t } = useTranslation();
  const [timeRange, setTimeRange] = useState<string>('7d');

  return (
    <RequireAuth>
    <div className="space-y-6">
      <DemoBanner />
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
          <p className="text-foreground/70">{t('analytics.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={timeRange} onValueChange={setTimeRange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder={t('analytics.selectRange')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="1h">{t('common.lastHour')}</SelectItem>
              <SelectItem value="24h">{t('common.last24Hours')}</SelectItem>
              <SelectItem value="7d">{t('common.last7Days')}</SelectItem>
              <SelectItem value="30d">{t('common.last30Days')}</SelectItem>
              <SelectItem value="90d">{t('common.last30Days')}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" disabled title={t('common.comingSoon')} aria-label={`${t('analytics.exportReport')} (${t('common.comingSoon')})`}><Download className="h-4 w-4 mr-2" aria-hidden="true" /> {t('common.export')}</Button>
          <Button disabled title={t('common.comingSoon')} aria-label={`${t('analytics.newReport')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.newReport')}</Button>
        </div>
      </div>

      {/* Report Builder */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Settings className="h-5 w-5" aria-hidden="true" />
            {t('analytics.reportBuilder')}
          </CardTitle>
          <CardDescription>{t('analytics.reportBuilderDesc')}</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-3">
            <div className="md:col-span-1 space-y-4 p-4 bg-muted/50 rounded-lg">
              <h4 className="font-medium text-foreground">{t('analytics.dimensions')}</h4>
              <div className="space-y-2">
                {['Date', 'User', 'Channel', 'Region', 'Device', 'Source'].map(d => (
                  <div key={d} className="p-2 border rounded text-foreground">{d}</div>
                ))}
              </div>
              <h4 className="font-medium mt-4 text-foreground">{t('analytics.metricsLabel')}</h4>
              <div className="space-y-2">
                {['Users', 'Messages', 'Calls', 'Revenue', 'Retention', 'Engagement'].map(m => (
                  <div key={m} className="p-2 border rounded text-foreground">{m}</div>
                ))}
              </div>
              <p className="text-xs font-medium text-foreground/70">{t('common.comingSoon')}: drag-and-drop</p>
            </div>
            <div className="md:col-span-2 border rounded-lg p-4 min-h-[400px]">
              <EmptyState message={t('common.noData')} />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Scheduled Reports */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5" aria-hidden="true" />
              {t('analytics.scheduledReports')}
            </CardTitle>
            <CardDescription>{t('analytics.scheduledDesc')}</CardDescription>
          </div>
          <Button disabled title={t('common.comingSoon')} aria-label={`${t('analytics.scheduleReport')} (${t('common.comingSoon')})`}><Plus className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.scheduleReport')}</Button>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {[
              { name: 'Daily Active Users', schedule: 'Daily 08:00', format: 'CSV', destination: 'Email', status: 'Active' },
              { name: 'Revenue Summary', schedule: 'Weekly Monday', format: 'PDF', destination: 'Slack', status: 'Active' },
              { name: 'Moderation Queue', schedule: 'Hourly', format: 'Excel', destination: 'Webhook', status: 'Paused' },
            ].map((report) => (
              <div key={report.name} className="flex items-center justify-between p-4 border rounded-lg">
                <div>
                  <p className="font-medium text-foreground">{report.name}</p>
                  <p className="text-sm text-foreground/70">{report.schedule} • {report.format} • {report.destination}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={report.status === 'Active' ? 'success' : 'secondary'}>{report.status}</Badge>
                  <Button variant="ghost" size="icon" aria-label={`${t('common.edit')} ${report.name}`} disabled title={t('common.comingSoon')}><Settings className="h-4 w-4" aria-hidden="true" /></Button>
                  <Button variant="ghost" size="icon" aria-label={`${t('common.apply')} ${report.name}`} disabled title={t('common.comingSoon')}><Play className="h-4 w-4" aria-hidden="true" /></Button>
                  <Button variant="ghost" size="icon" aria-label={`${t('common.pending')} ${report.name}`} disabled title={t('common.comingSoon')}><Pause className="h-4 w-4" aria-hidden="true" /></Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Cohort/Funnel/Retention Tabs */}
      <Tabs defaultValue="cohort" className="space-y-4">
        <div className={TABS_WRAP_CLASS}>
        <TabsList className={TABS_LIST_CLASS}>
          <TabsTrigger value="cohort" className={TABS_TRIGGER_CLASS}><Users className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.cohortAnalysis')}</TabsTrigger>
          <TabsTrigger value="funnel" className={TABS_TRIGGER_CLASS}><LineChart className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.funnelAnalysis')}</TabsTrigger>
          <TabsTrigger value="retention" className={TABS_TRIGGER_CLASS}><TrendingUp className="h-4 w-4 mr-2" aria-hidden="true" /> {t('analytics.retentionCurves')}</TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="cohort">
          <Card>
            <CardContent className="p-6">
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="funnel">
          <Card>
            <CardContent className="p-6">
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="retention">
          <Card>
            <CardContent className="p-6">
              <EmptyState message={t('common.comingSoon')} />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
    </RequireAuth>
  );
}