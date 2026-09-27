'use client';

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Phone, PhoneOff, Users, Activity, Wifi, RefreshCw, AlertTriangle } from 'lucide-react';
import { EmptyState, LoadingState } from './_shared';
import { apiFetch } from '../api';

/**
 * مراقبة المكالمات — على العقد الحقيقي للخادم.
 *
 * كان هذا الملف ينادي `/api/master/v1/calls/active` و`/calls/stats` عبر
 * `api.get()` من طبقة axios (baseURL = /api) فينتج مسار مزدوج
 * `/api/api/master/v1/...` ⇒ 404 دائماً، ثم `.then(r => r.data)` على كائن
 * مُفكَّك أصلاً ⇒ `undefined`. النتيجة: الجدول فارغ أبداً وبطاقات صفرية.
 *
 * المسارات الصحيحة (متحقَّق منها في `api/admin/RedMasterController.kt`):
 *   GET /api/master/v1/media/active-calls  ← ActiveCallRegistry.snapshot()
 *   GET /api/master/v1/stats/realtime      ← MasterStatsService.getLiveMetrics()
 * ولا بيانات وهمية عند الفشل: حالة خطأ صريحة.
 */

/** عنصر من `/api/master/v1/media/active-calls` (`.calls[]`). */
interface ActiveCallRow {
  id: string;
  type: string;
  room?: string;
  participants?: number;
  participantIds?: string[];
  startedAt?: string;
}

interface VoipMetrics {
  active_calls?: number;
  calls?: ActiveCallRow[];
  source?: string;
  timestamp?: number;
}

interface LiveMetrics {
  active_users?: number;
  messages_24h?: number;
  delivered_messages_24h?: number;
  read_messages_24h?: number;
  pending_messages_24h?: number;
  active_conversations?: number;
  delivery_rate_percent?: number;
  jvm_memory_percent?: number;
  db_health?: string;
  pending_approvals?: number;
  timestamp?: number;
}

async function fetchJson<T>(path: string): Promise<T> {
  const response = await apiFetch(path);
  if (!response.ok) throw new Error(`HTTP ${response.status} — ${path}`);
  return (await response.json()) as T;
}

/** `startedAt` يأتي ISO من السجل الحي؛ نحسب المدة ونعرضها بأمان. */
function formatDuration(startedAt?: string): string {
  if (!startedAt) return '—';
  const started = Date.parse(startedAt);
  if (Number.isNaN(started)) return '—';
  const sec = Math.max(0, Math.floor((Date.now() - started) / 1000));
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  const pad = (n: number) => n.toString().padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}

function CallTypeBadge({ type }: { type: string }) {
  const map: Record<string, { label: string; className: string }> = {
    VOIP: { label: 'صوتية/مرئية', className: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
    ONE_TO_ONE: { label: 'ثنائية', className: 'bg-blue-500/20 text-blue-400 border-blue-500/30' },
    GROUP: { label: 'مجموعة', className: 'bg-purple-500/20 text-purple-400 border-purple-500/30' },
    CONFERENCE: { label: 'مؤتمر', className: 'bg-amber-500/20 text-amber-400 border-amber-500/30' },
    LIVE_STREAM: { label: 'بث مباشر', className: 'bg-red-500/20 text-red-400 border-red-500/30' },
  };
  const { label, className } = map[type] ?? {
    label: type || 'غير معروف',
    className: 'bg-muted text-muted-foreground border-border',
  };
  return <Badge className={className}>{label}</Badge>;
}

export function CallsMonitorPage() {
  const [autoRefresh, setAutoRefresh] = useState(true);

  const voip = useQuery({
    queryKey: ['calls', 'active'],
    queryFn: () => fetchJson<VoipMetrics>('/api/master/v1/media/active-calls'),
    refetchInterval: autoRefresh ? 5000 : false,
  });

  const live = useQuery({
    queryKey: ['calls', 'live-metrics'],
    queryFn: () => fetchJson<LiveMetrics>('/api/master/v1/stats/realtime'),
    refetchInterval: autoRefresh ? 10000 : false,
  });

  const calls = useMemo<ActiveCallRow[]>(
    () => (Array.isArray(voip.data?.calls) ? (voip.data?.calls as ActiveCallRow[]) : []),
    [voip.data]
  );

  const participants = useMemo(
    () => calls.reduce((sum, call) => sum + (Number(call.participants) || 0), 0),
    [calls]
  );

  const cards = [
    { label: 'مكالمات نشطة', value: voip.data?.active_calls ?? calls.length, icon: Phone, color: 'text-blue-400' },
    { label: 'مشاركون في المكالمات', value: participants, icon: Users, color: 'text-green-400' },
    { label: 'مستخدمون متصلون', value: live.data?.active_users ?? '—', icon: Wifi, color: 'text-emerald-400' },
    {
      label: 'معدل التسليم (24س)',
      value: live.data?.delivery_rate_percent !== undefined ? `${live.data.delivery_rate_percent}%` : '—',
      icon: Activity,
      color: 'text-purple-400',
    },
  ];

  const failed = voip.isError || live.isError;

  return (
    <>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
              <Phone className="h-6 w-6 text-sovereign-gold" />
              مراقبة المكالمات النشطة
            </h1>
            <p className="text-muted-foreground mt-1">مباشر من سجل المكالمات الحقيقي في الخادم</p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant={autoRefresh ? 'default' : 'outline'}
              size="sm"
              onClick={() => setAutoRefresh((previous) => !previous)}
            >
              <Activity className="h-4 w-4 mr-1" />
              {autoRefresh ? 'تحديث تلقائي' : 'إيقاف التحديث'}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                void voip.refetch();
                void live.refetch();
              }}
            >
              <RefreshCw className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {failed && (
          <Card className="border-destructive/40 bg-destructive/5">
            <CardContent className="p-4 flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-destructive mt-0.5" />
              <div className="text-sm">
                <p className="font-medium text-foreground">تعذّر قراءة المقاييس الحية من الخادم</p>
                <p className="text-muted-foreground">
                  {(voip.error as Error | null)?.message ?? (live.error as Error | null)?.message ?? 'خطأ غير معروف'}
                </p>
              </div>
            </CardContent>
          </Card>
        )}

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {cards.map(({ label, value, icon: Icon, color }) => (
            <Card key={label} className="bg-card border-border">
              <CardContent className="p-4 flex items-center gap-3">
                <div className="p-2 rounded-lg bg-muted">
                  <Icon className={`h-5 w-5 ${color}`} />
                </div>
                <div>
                  <p className="text-2xl font-bold text-foreground">{value}</p>
                  <p className="text-xs text-muted-foreground">{label}</p>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        <Card className="bg-card border-border">
          <CardHeader>
            <CardTitle>المكالمات الجارية</CardTitle>
            <CardDescription>
              {autoRefresh ? 'يتحدّث كل 5 ثوانٍ تلقائياً' : 'التحديث التلقائي موقوف'} — المصدر:{' '}
              {voip.data?.source ?? '—'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {voip.isLoading ? (
              <LoadingState />
            ) : calls.length === 0 ? (
              <EmptyState
                message="لا توجد مكالمات جارية الآن على هذا الخادم"
              />
            ) : (
              <div className="rounded-md border border-border overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-muted/50">
                    <tr>
                      {['معرّف المكالمة', 'النوع', 'المشاركون', 'المدة', 'الغرفة'].map((header) => (
                        <th key={header} className="text-right px-4 py-3 font-medium text-muted-foreground whitespace-nowrap">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {calls.map((call, index) => (
                      <tr key={call.id} className={index % 2 === 0 ? 'bg-background' : 'bg-muted/20'}>
                        <td className="px-4 py-3 font-mono text-xs text-muted-foreground" title={call.id}>
                          {call.id?.slice(0, 8)}…
                        </td>
                        <td className="px-4 py-3">
                          <CallTypeBadge type={call.type} />
                        </td>
                        <td className="px-4 py-3 text-foreground font-medium">{call.participants ?? 0}</td>
                        <td className="px-4 py-3 font-mono text-foreground">{formatDuration(call.startedAt)}</td>
                        <td className="px-4 py-3 font-mono text-xs text-muted-foreground" title={call.room}>
                          {call.room ?? '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="bg-card border-border">
          <CardHeader>
            <CardTitle className="text-sm">مقاييس الرسائل — آخر 24 ساعة</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-muted-foreground text-xs">مُرسَلة</p>
              <p className="font-medium text-foreground">{live.data?.messages_24h ?? '—'}</p>
            </div>
            <div>
              <p className="text-muted-foreground text-xs">مُسلَّمة</p>
              <p className="font-medium text-foreground">{live.data?.delivered_messages_24h ?? '—'}</p>
            </div>
            <div>
              <p className="text-muted-foreground text-xs">مقروءة</p>
              <p className="font-medium text-foreground">{live.data?.read_messages_24h ?? '—'}</p>
            </div>
            <div>
              <p className="text-muted-foreground text-xs">قيد التسليم</p>
              <p className="font-medium text-foreground">{live.data?.pending_messages_24h ?? '—'}</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  );
}

export default CallsMonitorPage;
