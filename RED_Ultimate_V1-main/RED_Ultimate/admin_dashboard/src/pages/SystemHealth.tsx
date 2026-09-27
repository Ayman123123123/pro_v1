'use client';

import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Server, Database, Wifi, HardDrive, CheckCircle, XCircle,
  AlertTriangle, RefreshCw, Activity, Clock, GitBranch, Cpu, ShieldAlert,
} from 'lucide-react';
import { LoadingState } from './_shared';
import { apiFetch } from '../api';

/**
 * صحة النظام — على العقد الحقيقي للخادم.
 *
 * كان الملف ينادي `/actuator/health/custom` عبر طبقة axios، ولا وجود لهذا
 * المسار (ولا لـ`actuator` في Nginx أصلاً) ⇒ كان يسقط دائماً إلى
 * `MOCK_HEALTH`: ستّ خدمات "تعمل" بأرقام مُختلقة. الآن نقرأ
 * `GET /health/detailed` (HealthController.kt، محمي بصلاحية ADMIN) ونعرض
 * ما يعيده الخادم فعلاً، وفشل الطلب يُعرض كخطأ صريح بلا أي بيانات مُصنَّعة.
 *
 * `GET /sfu-health` يُضاف كفحص حقيقي منفصل لوسيط الوسائط (MediaSoup)،
 * لأنه ليس ضمن مجسّات HealthController.
 */

type ServiceStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN';

interface DetailedService {
  status?: ServiceStatus;
  error?: string | null;
  database?: string;
  host?: string;
  bucket?: string;
}

interface DetailedHealth {
  brand?: string;
  displayName?: string;
  status?: ServiceStatus;
  version?: string;
  timestamp?: string;
  responseTimeMs?: number;
  services?: Record<string, DetailedService>;
  flyway?: { latestVersion?: string | null; appliedCount?: number; error?: string | null };
  system?: { javaVersion?: string; availableProcessors?: number; maxMemoryMb?: number };
}

interface SfuHealth {
  status?: string;
  workers?: number;
  rooms?: number;
}

const SERVICE_LABELS: Record<string, string> = {
  postgresql: 'قاعدة البيانات (PostgreSQL)',
  mongodb: 'مستندات الرسائل (MongoDB)',
  redis: 'الذاكرة السريعة (Redis)',
  minio: 'تخزين الوسائط (MinIO)',
  'media-sfu': 'وسيط الوسائط (MediaSoup SFU)',
};

const SERVICE_ICONS: Record<string, React.ReactNode> = {
  postgresql: <Database className="h-5 w-5" />,
  mongodb: <Database className="h-5 w-5" />,
  redis: <Activity className="h-5 w-5" />,
  minio: <HardDrive className="h-5 w-5" />,
  'media-sfu': <Wifi className="h-5 w-5" />,
};

async function fetchJson<T>(path: string): Promise<T> {
  const response = await apiFetch(path);
  if (response.status === 401 || response.status === 403) {
    throw new Error('هذا المسار محمي — سجّل الدخول بصلاحية ADMIN لعرض التفاصيل');
  }
  if (!response.ok) throw new Error(`HTTP ${response.status} — ${path}`);
  return (await response.json()) as T;
}

function StatusIcon({ status }: { status: ServiceStatus }) {
  switch (status) {
    case 'UP':
      return <CheckCircle className="h-5 w-5 text-green-400" />;
    case 'DOWN':
      return <XCircle className="h-5 w-5 text-red-400" />;
    case 'DEGRADED':
      return <AlertTriangle className="h-5 w-5 text-yellow-400" />;
    default:
      return <Clock className="h-5 w-5 text-gray-400" />;
  }
}

function StatusBadge({ status }: { status: ServiceStatus }) {
  const map: Record<ServiceStatus, string> = {
    UP: 'bg-green-500/20 text-green-400 border-green-500/30',
    DOWN: 'bg-red-500/20 text-red-400 border-red-500/30',
    DEGRADED: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    UNKNOWN: 'bg-gray-500/20 text-gray-400 border-gray-500/30',
  };
  const labels: Record<ServiceStatus, string> = {
    UP: 'يعمل',
    DOWN: 'متوقف',
    DEGRADED: 'متدهور',
    UNKNOWN: 'غير معروف',
  };
  return <Badge className={map[status]}>{labels[status]}</Badge>;
}

export function SystemHealthPage() {
  const detailed = useQuery({
    queryKey: ['system', 'health', 'detailed'],
    queryFn: () => fetchJson<DetailedHealth>('/health/detailed'),
    refetchInterval: 15000,
    retry: false,
  });

  const sfu = useQuery({
    queryKey: ['system', 'health', 'sfu'],
    queryFn: () => fetchJson<SfuHealth>('/sfu-health'),
    refetchInterval: 30000,
    retry: false,
  });

  const services = detailed.data?.services ?? {};
  const rows: Array<{ key: string; service: DetailedService; status: ServiceStatus }> = Object.entries(services).map(
    ([key, service]) => ({
      key,
      service,
      status: (service.status ?? 'UNKNOWN') as ServiceStatus,
    })
  );

  if (sfu.data) {
    rows.push({
      key: 'media-sfu',
      service: { status: (sfu.data.status?.toUpperCase() as ServiceStatus) ?? 'UNKNOWN' },
      status: (sfu.data.status?.toUpperCase() as ServiceStatus) ?? 'UNKNOWN',
    });
  }

  const upCount = rows.filter((row) => row.status === 'UP').length;
  const overall: ServiceStatus = (detailed.data?.status as ServiceStatus) ?? 'UNKNOWN';

  return (
    <>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
              <Activity className="h-6 w-6 text-sovereign-gold" />
              صحة النظام
            </h1>
            <p className="text-muted-foreground mt-1">
              {rows.length > 0 ? `${upCount}/${rows.length} خدمات تعمل` : 'لا بيانات بعد'}
              {detailed.dataUpdatedAt ? ` — آخر فحص: ${new Date(detailed.dataUpdatedAt).toLocaleTimeString('ar')}` : ''}
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              void detailed.refetch();
              void sfu.refetch();
            }}
          >
            <RefreshCw className="h-4 w-4 mr-1" /> فحص الآن
          </Button>
        </div>

        {detailed.isError && (
          <Card className="border-destructive/40 bg-destructive/5">
            <CardContent className="p-4 flex items-start gap-3">
              <ShieldAlert className="h-5 w-5 text-destructive mt-0.5" />
              <div className="text-sm">
                <p className="font-medium text-foreground">تعذّر قراءة حالة الخادم</p>
                <p className="text-muted-foreground">{(detailed.error as Error | null)?.message ?? 'خطأ غير معروف'}</p>
              </div>
            </CardContent>
          </Card>
        )}

        {detailed.isLoading ? (
          <LoadingState />
        ) : (
          <>
            {detailed.data && (
              <Card
                className={`border-2 ${
                  overall === 'UP'
                    ? 'border-green-500/40'
                    : overall === 'DEGRADED'
                      ? 'border-yellow-500/40'
                      : 'border-red-500/40'
                }`}
              >
                <CardContent className="p-6 flex items-center gap-4">
                  <StatusIcon status={overall} />
                  <div>
                    <h2 className="text-lg font-semibold text-foreground">
                      {overall === 'UP'
                        ? 'جميع الخدمات تعمل بشكل طبيعي'
                        : overall === 'DEGRADED'
                          ? 'بعض الخدمات تعاني من مشاكل'
                          : 'خدمات متوقفة — يرجى التحقق'}
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      الإصدار: {detailed.data.version ?? '—'}
                      {detailed.data.responseTimeMs !== undefined ? ` — زمن الفحص: ${detailed.data.responseTimeMs}ms` : ''}
                    </p>
                  </div>
                  <div className="mr-auto">
                    <StatusBadge status={overall} />
                  </div>
                </CardContent>
              </Card>
            )}

            {rows.length > 0 && (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {rows.map(({ key, service, status }) => (
                  <Card key={key} className="bg-card border-border">
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-muted-foreground">{SERVICE_ICONS[key] ?? <Server className="h-5 w-5" />}</span>
                          <span>{SERVICE_LABELS[key] ?? key}</span>
                        </div>
                        <StatusIcon status={status} />
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="pt-0 space-y-2">
                      <StatusBadge status={status} />
                      {(service.database || service.host || service.bucket) && (
                        <p className="text-xs text-muted-foreground font-mono break-all">
                          {service.database ?? service.host ?? service.bucket}
                        </p>
                      )}
                      {service.error && (
                        <p className="text-xs text-destructive border-t border-border pt-2 font-mono">{service.error}</p>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {detailed.data?.flyway && (
                <Card className="bg-card border-border">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                      <GitBranch className="h-4 w-4 text-muted-foreground" /> ترحيلات قاعدة البيانات (Flyway)
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="text-sm space-y-1">
                    <p className="text-foreground">
                      آخر إصدار: <span className="font-mono">{detailed.data.flyway.latestVersion ?? '—'}</span>
                    </p>
                    <p className="text-muted-foreground">عدد الترحيلات المطبَّقة: {detailed.data.flyway.appliedCount ?? 0}</p>
                    {detailed.data.flyway.error && (
                      <p className="text-destructive font-mono text-xs">{detailed.data.flyway.error}</p>
                    )}
                  </CardContent>
                </Card>
              )}

              {detailed.data?.system && (
                <Card className="bg-card border-border">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                      <Cpu className="h-4 w-4 text-muted-foreground" /> بيئة الخادم
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="text-sm space-y-1 text-muted-foreground">
                    <p>
                      Java: <span className="font-mono text-foreground">{detailed.data.system.javaVersion ?? '—'}</span>
                    </p>
                    <p>المعالجات المتاحة: {detailed.data.system.availableProcessors ?? '—'}</p>
                    <p>أقصى ذاكرة: {detailed.data.system.maxMemoryMb ?? '—'} MB</p>
                  </CardContent>
                </Card>
              )}
            </div>
          </>
        )}
      </div>
    </>
  );
}

export default SystemHealthPage;
