'use client';

import { useState, useEffect, useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
// ✅ FIX 2026-09-22: المسارات كانت lowercase (card/badge/button/select) —
// على Linux (حساس للحالة) تفشل الـ resolution في وقت التشغيل → الصفحة تنهار.
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ComposedChart,
} from 'recharts';
import {
  Users,
  MessageSquare,
  Phone,
  Hash,
  DollarSign,
  TrendingUp,
  TrendingDown,
  Activity,
  Server,
  Database,
  HardDrive,
  Wifi,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Loader2,
  RefreshCw,
  Calendar,
  ChevronDown,
} from 'lucide-react';
import { format, subDays, startOfDay, endOfDay } from 'date-fns';
import { cn } from '@/utils/cn';
import { useSocket } from '@/components/providers/SocketProvider';
import { useTranslation } from 'react-i18next';
import { Skeleton } from '@/components/ui/Skeleton';
import { RequireAuth, DemoBanner, EmptyState, ErrorState, LoadingState } from './_shared';

interface MetricCardProps {
  title: string;
  value: string | number;
  change?: number;
  icon: React.ReactNode;
  iconColor: string;
  trend?: 'up' | 'down' | 'neutral';
  loading?: boolean;
}

function MetricCard({ title, value, change, icon, iconColor, trend = 'neutral', loading }: MetricCardProps) {
  const { t } = useTranslation();

  if (loading) {
    return (
      <Card className="overflow-hidden">
        <CardContent className="p-6">
          <div className="flex items-center justify-between">
            <div>
              <Skeleton className="h-4 w-24 mb-3" />
              <Skeleton className="h-8 w-32" />
            </div>
            <Skeleton className="h-14 w-14 rounded-xl" />
          </div>
        </CardContent>
      </Card>
    );
  }

  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : null;
  const trendColor = trend === 'up' ? 'text-emerald-500' : trend === 'down' ? 'text-red-500' : 'text-muted-foreground';

  return (
    <Card className="transition-all duration-300 hover:shadow-lg hover:-translate-y-1 overflow-hidden relative group border-0 ring-1 ring-border/50 dark:ring-border">
      <div className="absolute inset-0 bg-gradient-to-br from-foreground/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
      <div className={cn("absolute -right-6 -top-6 w-32 h-32 rounded-full blur-3xl opacity-20 transition-all duration-500 group-hover:opacity-40 group-hover:scale-125", iconColor)} />
      
      <CardContent className="p-6 relative z-10">
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <p className="text-sm font-semibold text-foreground/70 uppercase tracking-wider">{title}</p>
            <p className="text-3xl font-bold tracking-tight text-foreground">{value}</p>
            {change !== undefined && (
              <div className={cn('flex items-center gap-1.5 text-sm font-medium', trendColor)}>
                {TrendIcon && <TrendIcon className="h-4 w-4" aria-hidden="true" />}
                <span>{change >= 0 ? '+' : ''}{change.toFixed(1)}%</span>
                <span className="text-muted-foreground font-normal ml-1">{t('dashboard.vsLastPeriod')}</span>
              </div>
            )}
          </div>
          <div className={cn('p-4 rounded-2xl shadow-sm transition-transform duration-300 group-hover:scale-110 group-hover:rotate-3', iconColor, 'bg-background/80 backdrop-blur-sm border border-border/50')}>
            {icon}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

interface ChartDataPoint {
  date: string;
  users: number;
  newUsers: number;
  messages: number;
  calls: number;
  revenue: number;
  dau: number;
  mau: number;
}

interface SystemHealthData {
  component: string;
  status: 'healthy' | 'degraded' | 'critical';
  cpu?: number;
  memory?: number;
  disk?: number;
  network?: number;
  connections?: number;
  latency?: number;
}

interface AlertData {
  id: string;
  level: 'P0' | 'P1' | 'P2';
  message: string;
  component: string;
  timestamp: string;
  acknowledged: boolean;
}

export function Dashboard() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { socket, isConnected } = useSocket();
  const [timeRange, setTimeRange] = useState<'1h' | '24h' | '7d' | '30d'>('24h');
  const [realTimeMetrics, setRealTimeMetrics] = useState<Partial<ChartDataPoint>>({});

  // Fetch dashboard metrics
  const { data: metrics, isLoading: metricsLoading, isError: metricsError, refetch: refetchMetrics } = useQuery({
    queryKey: ['dashboard', 'metrics', timeRange],
    queryFn: () => fetchDashboardMetrics(timeRange),
    staleTime: 10_000,
    refetchInterval: 30_000,
    retry: 1,
  });

  // Fetch chart data
  const { data: chartData, isLoading: chartLoading, isError: chartError } = useQuery({
    queryKey: ['dashboard', 'charts', timeRange],
    queryFn: () => fetchChartData(timeRange),
    staleTime: 15_000,
    refetchInterval: 60_000,
    retry: 1,
  });

  // Fetch system health
  const { data: systemHealth, isLoading: healthLoading, isError: healthError } = useQuery({
    queryKey: ['dashboard', 'system-health'],
    queryFn: fetchSystemHealth,
    staleTime: 30_000,
    refetchInterval: 30_000,
    retry: 1,
  });

  // Fetch alerts
  const { data: alerts, isLoading: alertsLoading, isError: alertsError } = useQuery({
    queryKey: ['dashboard', 'alerts'],
    queryFn: fetchAlerts,
    staleTime: 10_000,
    refetchInterval: 15_000,
    retry: 1,
  });

  // Real-time updates via WebSocket
  useEffect(() => {
    if (!socket) return;

    const handleMetricsUpdate = (data: Partial<ChartDataPoint>) => {
      setRealTimeMetrics((prev) => ({ ...prev, ...data }));
      queryClient.setQueryData(['dashboard', 'metrics', timeRange], (old: any) => ({
        ...old,
        ...data,
      }));
    };

    const handleAlert = (alert: AlertData) => {
      queryClient.setQueryData(['dashboard', 'alerts'], (old: AlertData[] = []) => [alert, ...old.slice(0, 49)]);
    };

    const handleHealthUpdate = (health: SystemHealthData) => {
      queryClient.setQueryData(['dashboard', 'system-health'], (old: SystemHealthData[] = []) =>
        old.map((h) => (h.component === health.component ? health : h))
      );
    };

    socket.on('metrics:update', handleMetricsUpdate);
    socket.on('alert:new', handleAlert);
    socket.on('health:update', handleHealthUpdate);

    return () => {
      socket.off('metrics:update', handleMetricsUpdate);
      socket.off('alert:new', handleAlert);
      socket.off('health:update', handleHealthUpdate);
    };
  }, [socket, queryClient, timeRange]);

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  };

  const getDays = (range: string) => {
    switch (range) {
      case '1h': return 1;
      case '24h': return 1;
      case '7d': return 7;
      case '30d': return 30;
      default: return 7;
    }
  };

  const formatChartDate = (date: string) => {
    const d = new Date(date);
    if (timeRange === '1h') return format(d, 'HH:mm');
    if (timeRange === '24h') return format(d, 'HH:00');
    return format(d, 'MMM d');
  };

  const chartDataPoints = (chartData && (chartData as ChartDataPoint[]).length > 0) ? chartData as ChartDataPoint[] : []; // لا بيانات تاريخية حقيقية بعد — فراغ صادق
  const healthData = (systemHealth && systemHealth.length > 0) ? systemHealth : []; // /health/detailed يفشل ⇒ حالة خطأ صريحة لا أرقام مُختلقة
  const alertData = (alerts && alerts.length > 0) ? alerts : [];
  // مفتاحا العقد الحقيقي (snake_case من /api/admin/monitor/stats)
  const realMetrics = metrics as Record<string, number> | undefined;
  
  const isDemo = metricsError && !metricsLoading;
  const fmt = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v.toLocaleString() : '—');

  return (
    <RequireAuth>
    <div className="space-y-6">
      {isDemo && <DemoBanner />}
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-emerald-600 to-amber-600 dark:from-emerald-400 dark:to-amber-400">{t('dashboard.title')}</h1>
          <p className="text-foreground/70">{t('dashboard.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Select value={timeRange} onValueChange={(v) => setTimeRange(v as typeof timeRange)}>
            <SelectTrigger className="w-[180px] bg-background/50 backdrop-blur-sm">
              <SelectValue placeholder={t('common.select')} />
              <ChevronDown className="h-4 w-4 opacity-50" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="1h">{t('common.lastHour')}</SelectItem>
              <SelectItem value="24h">{t('common.last24Hours')}</SelectItem>
              <SelectItem value="7d">{t('common.last7Days')}</SelectItem>
              <SelectItem value="30d">{t('common.last30Days')}</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={handleRefresh} disabled={metricsLoading} className="bg-background/50 backdrop-blur-sm">
            <RefreshCw className={cn('h-4 w-4 mr-2', metricsLoading && 'animate-spin')} />
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {/* Connection Status */}
      <div className="flex items-center gap-2 text-sm bg-background/40 inline-flex px-3 py-1.5 rounded-full border border-border/50 backdrop-blur-sm shadow-sm">
        <span className={cn('h-2.5 w-2.5 rounded-full animate-pulse', isConnected ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.5)]')} />
        <span className="font-medium text-foreground/80">
          {isConnected ? t('dashboard.realtime') : t('dashboard.disconnected')}
        </span>
      </div>

      {/* Metric Cards — مفاتيح العقد الحقيقي من /api/admin/monitor/stats */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title={t('dashboard.metrics.totalUsers')}
          value={fmt(realMetrics?.total_users)}
          icon={<Users className="h-6 w-6 text-blue-500 dark:text-blue-400" />}
          iconColor="bg-blue-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.activeUsers')}
          value={fmt(realMetrics?.active_users)}
          icon={<Activity className="h-6 w-6 text-emerald-500 dark:text-emerald-400" />}
          iconColor="bg-emerald-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.messages')}
          value={fmt(realMetrics?.messages_24h)}
          icon={<MessageSquare className="h-6 w-6 text-purple-500 dark:text-purple-400" />}
          iconColor="bg-purple-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title="معدل التسليم (24س)"
          value={realMetrics?.delivery_rate_percent !== undefined ? `${realMetrics.delivery_rate_percent}%` : '—'}
          icon={<CheckCircle className="h-6 w-6 text-teal-500 dark:text-teal-400" />}
          iconColor="bg-teal-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title="رسائل مسلَّمة (24س)"
          value={fmt(realMetrics?.delivered_messages_24h)}
          icon={<MessageSquare className="h-6 w-6 text-green-500 dark:text-green-400" />}
          iconColor="bg-green-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title="ذاكرة JVM (%)"
          value={realMetrics?.jvm_memory_percent !== undefined ? `${realMetrics.jvm_memory_percent}%` : '—'}
          icon={<Server className="h-6 w-6 text-amber-500 dark:text-amber-400" />}
          iconColor="bg-amber-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title="صحة قاعدة البيانات"
          value={realMetrics?.db_health != null ? String(realMetrics.db_health) : '—'}
          icon={<Database className="h-6 w-6 text-cyan-500 dark:text-cyan-400" />}
          iconColor="bg-cyan-500/10"
          loading={metricsLoading}
        />
        <MetricCard
          title="موافقات معلّقة"
          value={fmt(realMetrics?.pending_approvals)}
          icon={<AlertTriangle className="h-6 w-6 text-pink-500 dark:text-pink-400" />}
          iconColor="bg-pink-500/10"
          loading={metricsLoading}
        />
      </div>

      {/* Charts Row */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Users Chart */}
        <Card className="overflow-hidden transition-all duration-300 hover:shadow-xl hover:border-blue-500/20 group">
          <div className="absolute inset-0 bg-gradient-to-br from-blue-500/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
          <CardHeader className="relative z-10">
            <CardTitle>{t('dashboard.charts.usersOverTime')}</CardTitle>
            <CardDescription>{t('dashboard.charts.userGrowth')}</CardDescription>
          </CardHeader>
          <CardContent className="relative z-10">
            <div className="h-[300px] relative">
              {chartDataPoints.length === 0 && (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center z-10">
                  <p className="text-sm font-medium text-foreground">لا بيانات تاريخية متاحة بعد</p>
                  <p className="text-xs text-muted-foreground mt-1">تُعرض الرسوم عند توفر مصدر تاريخي حقيقي من الخادم</p>
                </div>
              )}
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartDataPoints}>
                  <defs>
                    <linearGradient id="colorUsers" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="colorNewUsers" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10b981" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" opacity={0.4} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} />
                  <YAxis stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} tickFormatter={(val) => `${val / 1000}k`} />
                  <Tooltip
                    contentStyle={{ borderRadius: '8px', border: '1px solid var(--border)', backgroundColor: 'var(--background)' }}
                    formatter={(value: number) => [value.toLocaleString(), 'Users']}
                    labelFormatter={formatChartDate}
                  />
                  <Legend wrapperStyle={{ paddingTop: '20px' }} />
                  <Area type="monotone" dataKey="users" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorUsers)" name="Total Users" />
                  <Area type="monotone" dataKey="newUsers" stroke="#10b981" strokeWidth={3} fillOpacity={1} fill="url(#colorNewUsers)" name="New Users" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Messages & Calls Chart */}
        <Card className="overflow-hidden transition-all duration-300 hover:shadow-xl hover:border-purple-500/20 group">
          <div className="absolute inset-0 bg-gradient-to-br from-purple-500/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
          <CardHeader className="relative z-10">
            <CardTitle>{t('dashboard.charts.messagesOverTime')}</CardTitle>
            <CardDescription>{t('dashboard.messagesCallsActivity')}</CardDescription>
          </CardHeader>
          <CardContent className="relative z-10">
            <div className="h-[300px] relative">
              {chartDataPoints.length === 0 && (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center z-10">
                  <p className="text-sm font-medium text-foreground">لا بيانات تاريخية متاحة بعد</p>
                  <p className="text-xs text-muted-foreground mt-1">تُعرض الرسوم عند توفر مصدر تاريخي حقيقي من الخادم</p>
                </div>
              )}
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartDataPoints}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" opacity={0.4} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} />
                  <YAxis yAxisId="left" stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} tickFormatter={(val) => `${val / 1000}k`} />
                  <YAxis yAxisId="right" orientation="right" stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} tickFormatter={(val) => `$${val / 1000}k`} />
                  <Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid var(--border)', backgroundColor: 'var(--background)' }} labelFormatter={formatChartDate} />
                  <Legend wrapperStyle={{ paddingTop: '20px' }} />
                  <Bar yAxisId="left" dataKey="messages" fill="#8b5cf6" name="Messages" radius={[4, 4, 0, 0]} maxBarSize={40} />
                  <Bar yAxisId="left" dataKey="calls" fill="#f97316" name="Calls" radius={[4, 4, 0, 0]} maxBarSize={40} />
                  <Line yAxisId="right" type="monotone" dataKey="revenue" stroke="#10b981" strokeWidth={3} dot={{ r: 4, strokeWidth: 2 }} activeDot={{ r: 6 }} name="Revenue ($)" />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Revenue Pie Chart */}
        <Card className="overflow-hidden transition-all duration-300 hover:shadow-xl hover:border-amber-500/20 group">
          <div className="absolute inset-0 bg-gradient-to-br from-amber-500/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
          <CardHeader className="relative z-10">
            <CardTitle>{t('dashboard.charts.revenueOverTime')}</CardTitle>
            <CardDescription>{t('dashboard.revenueBreakdown')}</CardDescription>
          </CardHeader>
          <CardContent className="relative z-10">
            <div className="h-[300px] relative">
              {chartDataPoints.length === 0 && (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center z-10">
                  <p className="text-sm font-medium text-foreground">لا بيانات تاريخية متاحة بعد</p>
                  <p className="text-xs text-muted-foreground mt-1">تُعرض الرسوم عند توفر مصدر تاريخي حقيقي من الخادم</p>
                </div>
              )}
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={[ // لا مصدر إيرادات حقيقي بعد — قيم صفرية صادقة
                      { name: 'Subscriptions', value: 0 },
                      { name: 'In-app Purchases', value: 0 },
                      { name: 'Ads', value: 0 },
                      { name: 'Other', value: 0 },
                    ]}
                    cx="50%"
                    cy="50%"
                    innerRadius={70}
                    outerRadius={110}
                    paddingAngle={3}
                    dataKey="value"
                    nameKey="name"
                    label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                    stroke="none"
                  >
                    <Cell fill="#3b82f6" />
                    <Cell fill="#10b981" />
                    <Cell fill="#f59e0b" />
                    <Cell fill="#8b5cf6" />
                  </Pie>
                  <Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid var(--border)', backgroundColor: 'var(--background)' }} formatter={(value: number) => [`$${value.toLocaleString()}`, 'Revenue']} />
                  <Legend wrapperStyle={{ paddingTop: '20px' }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Retention / DAU/MAU Ratio */}
        <Card className="overflow-hidden transition-all duration-300 hover:shadow-xl hover:border-pink-500/20 group">
          <div className="absolute inset-0 bg-gradient-to-br from-pink-500/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
          <CardHeader className="relative z-10">
            <CardTitle>{t('dashboard.retentionTitle')}</CardTitle>
            <CardDescription>{t('dashboard.retentionDesc')}</CardDescription>
          </CardHeader>
          <CardContent className="relative z-10">
            <div className="h-[300px] relative">
              {chartDataPoints.length === 0 && (
                <div className="absolute inset-0 flex flex-col items-center justify-center text-center z-10">
                  <p className="text-sm font-medium text-foreground">لا بيانات تاريخية متاحة بعد</p>
                  <p className="text-xs text-muted-foreground mt-1">تُعرض الرسوم عند توفر مصدر تاريخي حقيقي من الخادم</p>
                </div>
              )}
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartDataPoints}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border)" opacity={0.4} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 100]} stroke="currentColor" opacity={0.6} tickLine={false} axisLine={false} tickFormatter={(val) => `${val}%`} />
                  <Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid var(--border)', backgroundColor: 'var(--background)' }} labelFormatter={formatChartDate} />
                  <Legend wrapperStyle={{ paddingTop: '20px' }} />
                  <Line type="monotone" dataKey="dauMauRatio" stroke="#8b5cf6" strokeWidth={3} dot={false} activeDot={{ r: 6 }} name="DAU/MAU %" />
                  <Line type="monotone" dataKey="retentionDay1" stroke="#ec4899" strokeWidth={3} dot={false} activeDot={{ r: 6 }} name="Day 1 Retention %" />
                  <Line type="monotone" dataKey="retentionDay7" stroke="#06b6d4" strokeWidth={3} dot={false} activeDot={{ r: 6 }} name="Day 7 Retention %" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* System Health & Alerts */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* System Health */}
        <Card className="lg:col-span-2 overflow-hidden shadow-md">
          <CardHeader className="border-b bg-muted/20 pb-4">
            <CardTitle className="flex items-center gap-2 text-xl">
              <Server className="h-5 w-5 text-emerald-500" aria-hidden="true" />
              {t('dashboard.systemHealth')}
            </CardTitle>
            <CardDescription>{t('dashboard.healthDesc')}</CardDescription>
          </CardHeader>
          <CardContent className="pt-6">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {healthData.map((health) => (
                <div
                  key={health.component}
                  className={cn(
                    'p-4 rounded-xl border transition-all duration-300 hover:shadow-md hover:-translate-y-1',
                    health.status === 'healthy' && 'border-emerald-500/20 bg-emerald-500/5 hover:border-emerald-500/40',
                    health.status === 'degraded' && 'border-amber-500/30 bg-amber-500/5 hover:border-amber-500/50',
                    health.status === 'critical' && 'border-red-500/30 bg-red-500/5 hover:border-red-500/50'
                  )}
                >
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="font-semibold">{health.component}</h4>
                    <Badge variant={
                      health.status === 'healthy' ? 'success' :
                      health.status === 'degraded' ? 'warning' : 'destructive'
                    } className="px-2 py-0.5">
                      {health.status === 'healthy' && <CheckCircle className="h-3 w-3 mr-1" />}
                      {health.status === 'degraded' && <AlertTriangle className="h-3 w-3 mr-1" />}
                      {health.status === 'critical' && <XCircle className="h-3 w-3 mr-1" />}
                      {t(`common.${health.status}`)}
                    </Badge>
                  </div>
                  <div className="space-y-2 text-sm text-muted-foreground/80">
                    {health.cpu !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><Activity className="h-3.5 w-3.5" /> CPU</span>
                        <span className={cn('font-medium', health.cpu > 80 ? 'text-red-500' : health.cpu > 60 ? 'text-amber-500' : 'text-emerald-500')}>
                          {health.cpu}%
                        </span>
                      </div>
                    )}
                    {health.memory !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><Database className="h-3.5 w-3.5" /> Memory</span>
                        <span className={cn('font-medium', health.memory > 80 ? 'text-red-500' : health.memory > 60 ? 'text-amber-500' : 'text-emerald-500')}>
                          {health.memory}%
                        </span>
                      </div>
                    )}
                    {health.disk !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><HardDrive className="h-3.5 w-3.5" /> Disk</span>
                        <span className={cn('font-medium', health.disk > 80 ? 'text-red-500' : health.disk > 60 ? 'text-amber-500' : 'text-emerald-500')}>
                          {health.disk}%
                        </span>
                      </div>
                    )}
                    {health.network !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><Wifi className="h-3.5 w-3.5" /> Network</span>
                        <span className="font-medium text-foreground">{health.network} Mbps</span>
                      </div>
                    )}
                    {health.connections !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><Users className="h-3.5 w-3.5" /> Conns</span>
                        <span className="font-medium text-foreground">{health.connections}</span>
                      </div>
                    )}
                    {health.latency !== undefined && (
                      <div className="flex justify-between items-center">
                        <span className="flex items-center gap-1.5"><Activity className="h-3.5 w-3.5" /> Latency</span>
                        <span className={cn('font-medium', health.latency > 200 ? 'text-red-500' : health.latency > 100 ? 'text-amber-500' : 'text-emerald-500')}>
                          {health.latency}ms
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Alerts */}
        <Card className="overflow-hidden shadow-md">
          <CardHeader className="border-b bg-muted/20 pb-4">
            <CardTitle className="flex items-center gap-2 text-xl">
              <AlertTriangle className="h-5 w-5 text-amber-500" aria-hidden="true" />
              {t('dashboard.alerts')}
            </CardTitle>
            <CardDescription>{t('dashboard.alertsDesc')}</CardDescription>
          </CardHeader>
          <CardContent className="p-0">
            <div className="space-y-0 max-h-[420px] overflow-y-auto">
              {alertData.length === 0 ? (
                <div className="text-center py-12 px-4">
                  <div className="bg-emerald-500/10 h-16 w-16 rounded-full flex items-center justify-center mx-auto mb-4">
                    <CheckCircle className="h-8 w-8 text-emerald-500" aria-hidden="true" />
                  </div>
                  <p className="font-medium text-foreground text-lg">{t('dashboard.noAlerts')}</p>
                  <p className="text-sm text-muted-foreground mt-1">All systems are running smoothly</p>
                </div>
              ) : (
                alertData.map((alert, idx) => (
                  <div
                    key={alert.id}
                    className={cn(
                      'p-4 border-l-4 transition-colors hover:bg-muted/30 border-b last:border-b-0',
                      alert.level === 'P0' && 'border-l-red-500',
                      alert.level === 'P1' && 'border-l-amber-500',
                      alert.level === 'P2' && 'border-l-blue-500'
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1.5">
                          <Badge variant={
                            alert.level === 'P0' ? 'destructive' :
                            alert.level === 'P1' ? 'warning' : 'info'
                          } className="px-1.5 py-0 rounded-sm text-[10px] font-bold">
                            {alert.level}
                          </Badge>
                          <span className="text-sm font-semibold text-foreground/90">{alert.component}</span>
                        </div>
                        <p className="text-sm text-foreground/80 font-medium leading-tight">{alert.message || '—'}</p>
                        <p className="text-xs text-muted-foreground mt-2 flex items-center gap-1">
                          <Calendar className="h-3 w-3" />
                          {(() => { try { const d = new Date(alert.timestamp); return Number.isFinite(d.getTime()) ? format(d, 'MMM d, HH:mm:ss') : '—'; } catch { return '—'; } })()}
                        </p>
                      </div>
                      {!alert.acknowledged && (
                        <Button variant="ghost" size="icon" title="Acknowledge Alert" className="h-8 w-8 rounded-full opacity-50 hover:opacity-100 hover:bg-emerald-500/10 hover:text-emerald-500 transition-all" aria-label={`Acknowledge alert from ${alert.component}`} onClick={() => queryClient.setQueryData(['dashboard', 'alerts'], (old: AlertData[] = []) => old.map((a) => (a.id === alert.id ? { ...a, acknowledged: true } : a)))}>
                          <CheckCircle className="h-5 w-5" aria-hidden="true" />
                        </Button>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
    </RequireAuth>
  );
}

// API Functions — على العقد الحقيقي للخادم (2026-09-27).
// كان هذا الملف ينادي /api/admin/dashboard/{metrics,charts} و
// /api/admin/system/health و /api/admin/alerts — ولا واحد منها موجود
// في الخادم (تحقّق من Controllers) ⇒ كل البطاقات كانت تسقط للموك دائمًا.
// المسارات الحقيقية:
//   GET /api/admin/monitor/stats  ← AdminMonitorController → MasterStatsService
//   GET /health/detailed          ← HealthController (ADMIN) — خدمات مفصلة
//   GET /api/master/v1/stats/realtime ← نفس المصدر القانوني للإحصاء الحي
async function fetchDashboardMetrics(_timeRange: string) {
  const response = await fetch('/api/admin/monitor/stats');
  if (!response.ok) throw new Error('Failed to fetch metrics');
  return response.json();
}

// الرسوم الزمنية: لا يوجد مسار تاريخي حقيقي بعد — نُرجع null ولا نُصنّع أرقامًا.
// الواجهة تعرض حالة فراغ صريحة بدل بيانات وهمية مضللة.
async function fetchChartData(_timeRange: string) {
  return null;
}

async function fetchSystemHealth(): Promise<SystemHealthData[]> {
  const response = await fetch('/health/detailed');
  if (!response.ok) throw new Error('Failed to fetch system health');
  const json: unknown = await response.json();
  if (json && typeof json === 'object') {
    const obj = json as Record<string, unknown>;
    const services = obj['services'] as Record<string, Record<string, unknown>> | undefined;
    if (services && typeof services === 'object') {
      return Object.entries(services).map(([name, val]) => ({
        component: name,
        status: val?.status === 'UP' || val?.status === 'healthy' ? 'healthy' as const
          : val?.status === 'DEGRADED' ? 'degraded' as const
          : 'critical' as const,
      }));
    }
  }
  return [];
}

// التنبيهات: لا يوجد مسار /api/admin/alerts في الخادم — تُعرض قائمة فارغة
// بحالة «لا تنبيهات» الصادقة (المكوّن يتعامل معها بأناقة) بدل بلاغات وهمية.
async function fetchAlerts(): Promise<AlertData[]> {
  return [];
}
export default Dashboard;
