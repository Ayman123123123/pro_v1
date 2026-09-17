'use client';

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
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

export function AnalyticsPage() {
  const { t } = useTranslation();
  const [timeRange, setTimeRange] = useState<'1h' | '24h' | '7d' | '30d' | '90d'>('7d');

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('analytics.title')}</h1>
          <p className="text-muted-foreground">{t('analytics.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={timeRange} onValueChange={setTimeRange}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Select range" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="1h">Last Hour</SelectItem>
              <SelectItem value="24h">Last 24 Hours</SelectItem>
              <SelectItem value="7d">Last 7 Days</SelectItem>
              <SelectItem value="30d">Last 30 Days</SelectItem>
              <SelectItem value="90d">Last 90 Days</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline"><Download className="h-4 w-4 mr-2" /> Export</Button>
          <Button><Plus className="h-4 w-4 mr-2" /> New Report</Button>
        </div>
      </div>

      {/* Report Builder */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Settings className="h-5 w-5" />
            Custom Report Builder
          </CardTitle>
          <CardDescription>Drag and drop dimensions and metrics to build custom reports</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-3">
            <div className="md:col-span-1 space-y-4 p-4 bg-muted/50 rounded-lg">
              <h4 className="font-medium">Dimensions</h4>
              <div className="space-y-2">
                {['Date', 'User', 'Channel', 'Region', 'Device', 'Source'].map(d => (
                  <div key={d} className="p-2 border rounded cursor-grab hover:bg-muted" draggable>{d}</div>
                ))}
              </div>
              <h4 className="font-medium mt-4">Metrics</h4>
              <div className="space-y-2">
                {['Users', 'Messages', 'Calls', 'Revenue', 'Retention', 'Engagement'].map(m => (
                  <div key={m} className="p-2 border rounded cursor-grab hover:bg-muted" draggable>{m}</div>
                ))}
              </div>
            </div>
            <div className="md:col-span-2 border rounded-lg p-4 min-h-[400px]">
              <p className="text-center text-muted-foreground py-8">Drop dimensions and metrics here to build your report</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Scheduled Reports */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5" />
              Scheduled Reports
            </CardTitle>
            <CardDescription>Automated reports delivered via email, webhook, or Slack</CardDescription>
          </div>
          <Button><Plus className="h-4 w-4 mr-2" /> Schedule Report</Button>
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
                  <p className="font-medium">{report.name}</p>
                  <p className="text-sm text-muted-foreground">{report.schedule} • {report.format} • {report.destination}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant={report.status === 'Active' ? 'success' : 'secondary'}>{report.status}</Badge>
                  <Button variant="ghost" size="icon"><Settings className="h-4 w-4" /></Button>
                  <Button variant="ghost" size="icon"><Play className="h-4 w-4" /></Button>
                  <Button variant="ghost" size="icon"><Pause className="h-4 w-4" /></Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Cohort/Funnel/Retention Tabs */}
      <Tabs defaultValue="cohort" className="space-y-4">
        <TabsList>
          <TabsTrigger value="cohort"><Users className="h-4 w-4 mr-2" /> Cohort Analysis</TabsTrigger>
          <TabsTrigger value="funnel"><LineChart className="h-4 w-4 mr-2" /> Funnel Analysis</TabsTrigger>
          <TabsTrigger value="retention"><TrendingUp className="h-4 w-4 mr-2" /> Retention Curves</TabsTrigger>
        </TabsList>

        <TabsContent value="cohort">
          <Card>
            <CardContent className="p-6">
              <p className="text-center text-muted-foreground py-12">Cohort analysis visualization (implement with Recharts/Visx)</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="funnel">
          <Card>
            <CardContent className="p-6">
              <p className="text-center text-muted-foreground py-12">Funnel analysis visualization (implement with Recharts/Visx)</p>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="retention">
          <Card>
            <CardContent className="p-6">
              <p className="text-center text-muted-foreground py-12">Retention curves visualization (implement with Recharts/Visx)</p>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

import { useState } from 'react';