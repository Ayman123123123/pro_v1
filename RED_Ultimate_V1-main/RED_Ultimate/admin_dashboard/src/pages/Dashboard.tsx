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
import { Skeleton } from '@/components/ui/skeleton';

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
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center justify-between">
            <div>
              <Skeleton className="h-4 w-24 mb-2" />
              <Skeleton className="h-8 w-32" />
            </div>
            <Skeleton className="h-12 w-12 rounded-lg" />
          </div>
        </CardContent>
      </Card>
    );
  }

  const TrendIcon = trend === 'up' ? TrendingUp : trend === 'down' ? TrendingDown : null;
  const trendColor = trend === 'up' ? 'text-green-500' : trend === 'down' ? 'text-red-500' : 'text-muted-foreground';

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            <p className="text-3xl font-bold tracking-tight">{value}</p>
            {change !== undefined && (
              <div className={cn('flex items-center gap-1 text-sm', trendColor)}>
                {TrendIcon && <TrendIcon className="h-4 w-4" />}
                <span>{change >= 0 ? '+' : ''}{change.toFixed(1)}%</span>
                <span className="text-muted-foreground">vs last period</span>
              </div>
            )}
          </div>
          <div className={cn('p-3 rounded-xl', iconColor)}>
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
  const { data: metrics, isLoading: metricsLoading } = useQuery({
    queryKey: ['dashboard', 'metrics', timeRange],
    queryFn: () => fetchDashboardMetrics(timeRange),
    staleTime: 10_000,
    refetchInterval: 30_000,
  });

  // Fetch chart data
  const { data: chartData, isLoading: chartLoading } = useQuery({
    queryKey: ['dashboard', 'charts', timeRange],
    queryFn: () => fetchChartData(timeRange),
    staleTime: 15_000,
    refetchInterval: 60_000,
  });

  // Fetch system health
  const { data: systemHealth, isLoading: healthLoading } = useQuery({
    queryKey: ['dashboard', 'system-health'],
    queryFn: fetchSystemHealth,
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  // Fetch alerts
  const { data: alerts, isLoading: alertsLoading } = useQuery({
    queryKey: ['dashboard', 'alerts'],
    queryFn: fetchAlerts,
    staleTime: 10_000,
    refetchInterval: 15_000,
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

  const chartDataPoints = chartData || [];
  const healthData = systemHealth || [];
  const alertData = alerts || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.title')}</h1>
          <p className="text-muted-foreground">{t('dashboard.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Select value={timeRange} onValueChange={(v) => setTimeRange(v as typeof timeRange)}>
            <SelectTrigger className="w-[180px]">
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
          <Button variant="outline" size="sm" onClick={handleRefresh} disabled={metricsLoading}>
            <RefreshCw className={cn('h-4 w-4 mr-2', metricsLoading && 'animate-spin')} />
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {/* Connection Status */}
      <div className="flex items-center gap-2 text-sm">
        <span className={cn('h-2 w-2 rounded-full', isConnected ? 'bg-green-500' : 'bg-red-500')} />
        <span className="text-muted-foreground">
          {isConnected ? t('dashboard.realtime') : 'Disconnected'}
        </span>
      </div>

      {/* Metric Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title={t('dashboard.metrics.totalUsers')}
          value={metrics?.totalUsers?.toLocaleString() || '0'}
          change={metrics?.usersChange}
          icon={<Users className="h-6 w-6 text-white" />}
          iconColor="bg-blue-500"
          trend={metrics?.usersChange && metrics.usersChange > 0 ? 'up' : metrics?.usersChange && metrics.usersChange < 0 ? 'down' : 'neutral'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.activeUsers')}
          value={metrics?.activeUsers?.toLocaleString() || '0'}
          change={metrics?.activeUsersChange}
          icon={<Activity className="h-6 w-6 text-white" />}
          iconColor="bg-green-500"
          trend={metrics?.activeUsersChange && metrics.activeUsersChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.messages')}
          value={metrics?.messages?.toLocaleString() || '0'}
          change={metrics?.messagesChange}
          icon={<MessageSquare className="h-6 w-6 text-white" />}
          iconColor="bg-purple-500"
          trend={metrics?.messagesChange && metrics.messagesChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.calls')}
          value={metrics?.calls?.toLocaleString() || '0'}
          change={metrics?.callsChange}
          icon={<Phone className="h-6 w-6 text-white" />}
          iconColor="bg-orange-500"
          trend={metrics?.callsChange && metrics.callsChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.channels')}
          value={metrics?.channels?.toLocaleString() || '0'}
          change={metrics?.channelsChange}
          icon={<Hash className="h-6 w-6 text-white" />}
          iconColor="bg-indigo-500"
          trend={metrics?.channelsChange && metrics.channelsChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.revenue')}
          value={`$${(metrics?.revenue || 0).toLocaleString()}`}
          change={metrics?.revenueChange}
          icon={<DollarSign className="h-6 w-6 text-white" />}
          iconColor="bg-emerald-500"
          trend={metrics?.revenueChange && metrics.revenueChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.mau')}
          value={metrics?.mau?.toLocaleString() || '0'}
          change={metrics?.mauChange}
          icon={<Users className="h-6 w-6 text-white" />}
          iconColor="bg-cyan-500"
          trend={metrics?.mauChange && metrics.mauChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
        <MetricCard
          title={t('dashboard.metrics.dau')}
          value={metrics?.dau?.toLocaleString() || '0'}
          change={metrics?.dauChange}
          icon={<Activity className="h-6 w-6 text-white" />}
          iconColor="bg-pink-500"
          trend={metrics?.dauChange && metrics.dauChange > 0 ? 'up' : 'down'}
          loading={metricsLoading}
        />
      </div>

      {/* Charts Row */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Users Chart */}
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.charts.usersOverTime')}</CardTitle>
            <CardDescription>{t('dashboard.charts.userGrowth')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartDataPoints}>
                  <defs>
                    <linearGradient id="colorUsers" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="colorNewUsers" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#22c55e" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} />
                  <YAxis />
                  <Tooltip
                    formatter={(value: number) => [value.toLocaleString(), 'Users']}
                    labelFormatter={formatChartDate}
                  />
                  <Legend />
                  <Area type="monotone" dataKey="users" stroke="#3b82f6" fillOpacity={1} fill="url(#colorUsers)" name="Total Users" />
                  <Area type="monotone" dataKey="newUsers" stroke="#22c55e" fillOpacity={1} fill="url(#colorNewUsers)" name="New Users" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Messages & Calls Chart */}
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.charts.messagesOverTime')}</CardTitle>
            <CardDescription>Messages and calls activity</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chartDataPoints}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} />
                  <YAxis yAxisId="left" />
                  <YAxis yAxisId="right" orientation="right" />
                  <Tooltip labelFormatter={formatChartDate} />
                  <Legend />
                  <Bar yAxisId="left" dataKey="messages" fill="#8b5cf6" name="Messages" radius={[4, 4, 0, 0]} />
                  <Bar yAxisId="left" dataKey="calls" fill="#f97316" name="Calls" radius={[4, 4, 0, 0]} />
                  <Line yAxisId="right" type="monotone" dataKey="revenue" stroke="#22c55e" strokeWidth={2} dot={false} name="Revenue ($)" />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Revenue Pie Chart */}
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.charts.revenueOverTime')}</CardTitle>
            <CardDescription>Revenue breakdown by source</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={[
                      { name: 'Subscriptions', value: metrics?.revenueSubscriptions || 0 },
                      { name: 'In-app Purchases', value: metrics?.revenuePurchases || 0 },
                      { name: 'Ads', value: metrics?.revenueAds || 0 },
                      { name: 'Other', value: metrics?.revenueOther || 0 },
                    ]}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={2}
                    dataKey="value"
                    nameKey="name"
                    label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                  >
                    <Cell fill="#3b82f6" />
                    <Cell fill="#22c55e" />
                    <Cell fill="#f97316" />
                    <Cell fill="#8b5cf6" />
                  </Pie>
                  <Tooltip formatter={(value: number) => [`$${value.toLocaleString()}`, 'Revenue']} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Retention / DAU/MAU Ratio */}
        <Card>
          <CardHeader>
            <CardTitle>Retention & Engagement</CardTitle>
            <CardDescription>DAU/MAU ratio and retention metrics</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartDataPoints}>
                  <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                  <XAxis dataKey="date" tickFormatter={formatChartDate} />
                  <YAxis domain={[0, 100]} />
                  <Tooltip labelFormatter={formatChartDate} />
                  <Legend />
                  <Line type="monotone" dataKey="dauMauRatio" stroke="#8b5cf6" strokeWidth={2} dot={false} name="DAU/MAU %" />
                  <Line type="monotone" dataKey="retentionDay1" stroke="#ec4899" strokeWidth={2} dot={false} name="Day 1 Retention %" />
                  <Line type="monotone" dataKey="retentionDay7" stroke="#06b6d4" strokeWidth={2} dot={false} name="Day 7 Retention %" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* System Health & Alerts */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* System Health */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Server className="h-5 w-5" />
              {t('dashboard.systemHealth')}
            </CardTitle>
            <CardDescription>Real-time system health monitoring</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {healthData.map((health) => (
                <div
                  key={health.component}
                  className={cn(
                    'p-4 rounded-lg border',
                    health.status === 'healthy' && 'border-green-200 bg-green-50',
                    health.status === 'degraded' && 'border-yellow-200 bg-yellow-50',
                    health.status === 'critical' && 'border-red-200 bg-red-50'
                  )}
                >
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="font-medium">{health.component}</h4>
                    <Badge variant={
                      health.status === 'healthy' ? 'success' :
                      health.status === 'degraded' ? 'warning' : 'destructive'
                    }>
                      {health.status === 'healthy' && <CheckCircle className="h-3 w-3 mr-1" />}
                      {health.status === 'degraded' && <AlertTriangle className="h-3 w-3 mr-1" />}
                      {health.status === 'critical' && <XCircle className="h-3 w-3 mr-1" />}
                      {t(`common.${health.status}`)}
                    </Badge>
                  </div>
                  <div className="space-y-1 text-sm text-muted-foreground">
                    {health.cpu !== undefined && (
                      <div className="flex justify-between">
                        <span>CPU</span>
                        <span className={cn('font-medium', health.cpu > 80 ? 'text-red-500' : health.cpu > 60 ? 'text-yellow-500' : 'text-green-500')}>
                          {health.cpu}%
                        </span>
                      </div>
                    )}
                    {health.memory !== undefined && (
                      <div className="flex justify-between">
                        <span>Memory</span>
                        <span className={cn('font-medium', health.memory > 80 ? 'text-red-500' : health.memory > 60 ? 'text-yellow-500' : 'text-green-500')}>
                          {health.memory}%
                        </span>
                      </div>
                    )}
                    {health.disk !== undefined && (
                      <div className="flex justify-between">
                        <span>Disk</span>
                        <span className={cn('font-medium', health.disk > 80 ? 'text-red-500' : health.disk > 60 ? 'text-yellow-500' : 'text-green-500')}>
                          {health.disk}%
                        </span>
                      </div>
                    )}
                    {health.network !== undefined && (
                      <div className="flex justify-between">
                        <span>Network</span>
                        <span>{health.network} Mbps</span>
                      </div>
                    )}
                    {health.connections !== undefined && (
                      <div className="flex justify-between">
                        <span>DB Connections</span>
                        <span>{health.connections}</span>
                      </div>
                    )}
                    {health.latency !== undefined && (
                      <div className="flex justify-between">
                        <span>Latency</span>
                        <span className={cn('font-medium', health.latency > 200 ? 'text-red-500' : health.latency > 100 ? 'text-yellow-500' : 'text-green-500')}>
                          {health.latency}ms
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              ))}
              {healthLoading && (
                <div className="col-span-full grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                  {[1, 2, 3, 4, 5, 6].map((i) => (
                    <Skeleton key={i} className="h-32" />
                  ))}
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Alerts */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-orange-500" />
              {t('dashboard.alerts')}
            </CardTitle>
            <CardDescription>Recent critical alerts</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-3 max-h-[400px] overflow-y-auto">
              {alertData.length === 0 && !alertsLoading ? (
                <div className="text-center py-8 text-muted-foreground">
                  <CheckCircle className="h-12 w-12 mx-auto mb-2 text-green-500" />
                  <p>No active alerts</p>
                </div>
              ) : (
                alertData.map((alert) => (
                  <div
                    key={alert.id}
                    className={cn(
                      'p-3 rounded-lg border-l-4',
                      alert.level === 'P0' && 'border-red-500 bg-red-50',
                      alert.level === 'P1' && 'border-orange-500 bg-orange-50',
                      alert.level === 'P2' && 'border-yellow-500 bg-yellow-50'
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <Badge variant={
                            alert.level === 'P0' ? 'destructive' :
                            alert.level === 'P1' ? 'warning' : 'info'
                          }>
                            {alert.level}
                          </Badge>
                          <span className="text-sm font-medium">{alert.component}</span>
                        </div>
                        <p className="text-sm text-muted-foreground">{alert.message}</p>
                        <p className="text-xs text-muted-foreground mt-1">
                          {format(new Date(alert.timestamp), 'MMM d, HH:mm:ss')}
                        </p>
                      </div>
                      {!alert.acknowledged && (
                        <Button variant="ghost" size="icon" className="h-6 w-6">
                          <CheckCircle className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                ))
              )}
              {alertsLoading && (
                <div className="space-y-3">
                  {[1, 2, 3].map((i) => (
                    <Skeleton key={i} className="h-20" />
                  ))}
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

// API Functions
async function fetchDashboardMetrics(timeRange: string) {
  const response = await fetch(`/api/admin/dashboard/metrics?range=${timeRange}`);
  if (!response.ok) throw new Error('Failed to fetch metrics');
  return response.json();
}

async function fetchChartData(timeRange: string) {
  const response = await fetch(`/api/admin/dashboard/charts?range=${timeRange}`);
  if (!response.ok) throw new Error('Failed to fetch chart data');
  return response.json();
}

async function fetchSystemHealth(): Promise<SystemHealthData[]> {
  const response = await fetch('/api/admin/system/health');
  if (!response.ok) throw new Error('Failed to fetch system health');
  const json: unknown = await response.json();
  // ✅ FIX: الخادم يرجع كائن (Map) وليس مصفوفة — .map() كان سيعطي TypeError وقت التشغيل
  if (Array.isArray(json)) return json as SystemHealthData[];
  if (json && typeof json === 'object') {
    const obj = json as Record<string, unknown>;
    for (const key of ['components', 'systems', 'health', 'data']) {
      if (Array.isArray(obj[key])) return obj[key] as SystemHealthData[];
    }
    // كائن مسطح: { database: {...}, redis: {...} } → نحوله لمصفوفة
    return Object.entries(obj).map(([name, val]) => ({
      component: name,
      status: (val as Record<string, unknown>)?.status === 'UP' || (val as Record<string, unknown>)?.healthy === true ? 'healthy' : 'degraded',
    }));
  }
  return [];
}

async function fetchAlerts(): Promise<AlertData[]> {
  const response = await fetch('/api/admin/alerts?limit=50');
  if (!response.ok) throw new Error('Failed to fetch alerts');
  const json: unknown = await response.json();
  // ✅ FIX: نفس الحماية — نقبل مصفوفة أو كائن مغلف { alerts: [...] }
  if (Array.isArray(json)) return json as AlertData[];
  if (json && typeof json === 'object') {
    const obj = json as Record<string, unknown>;
    if (Array.isArray(obj['alerts'])) return obj['alerts'] as AlertData[];
    if (Array.isArray(obj['data'])) return obj['data'] as AlertData[];
  }
  return [];
}
export default Dashboard;
