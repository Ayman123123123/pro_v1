import { useQuery } from '@tanstack/react-query';
import { GitBranch, CheckCircle, XCircle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { apiFetch } from '../../api';
import { LoadingState, ErrorState, EmptyState } from '../_shared';

/**
 * هجرات Flyway — على العقد الحقيقي للخادم (2026-09-27).
 *
 * كانت الصفحة تعرض 15 هجرة وهمية (V0100+) بأرقام عشوائية — الآن تقرأ
 * GET /api/admin/system/migrations (AdminSystemOpsController) الذي يقرأ
 * flyway_schema_history مباشرة. فشل الوصول يعني قاعدة غير مُهاجَرة
 * ويُعرض كحالة صريحة لا كجدول مُختلق.
 */

interface MigrationRow {
  id: string;
  version: string;
  description: string;
  type: string;
  status: 'SUCCESS' | 'FAILED';
  checksum: string;
  executedBy: string;
  executedAt: string;
  executionTime: number;
  script?: string;
}

interface MigrationsResponse {
  available: boolean;
  migrations: MigrationRow[];
  total: number;
  failed: number;
  currentVersion?: string;
  reason?: string;
}

async function fetchMigrations(): Promise<MigrationsResponse> {
  const res = await apiFetch('/api/admin/system/migrations');
  if (!res.ok) throw new Error(`HTTP ${res.status} — /api/admin/system/migrations`);
  return (await res.json()) as MigrationsResponse;
}

export function MigrationsPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['system', 'migrations'],
    queryFn: fetchMigrations,
    staleTime: 30_000,
    refetchInterval: 60_000,
    retry: 1,
  });

  if (isLoading) return <LoadingState label="تحميل هجرات Flyway…" />;
  if (isError) return <ErrorState onRetry={refetch} message="تعذر قراءة سجل الهجرات من الخادم" />;
  if (!data?.available) {
    return (
      <EmptyState
        message={`سجل الهجرات غير متاح — ${data?.reason || 'القاعدة ليست مُهاجَرة بـ Flyway'}`}
      />
    );
  }

  const migrations = data.migrations;

  const columns = [
    {
      key: 'version',
      title: 'Version',
      cell: (m: MigrationRow) => <span className="font-mono font-medium text-yn-text">{m.version}</span>,
    },
    {
      key: 'description',
      title: 'Description',
      cell: (m: MigrationRow) => <span className="text-yn-text-secondary">{m.description}</span>,
    },
    {
      key: 'type',
      title: 'Type',
      cell: (m: MigrationRow) => <Badge variant="blue">{m.type}</Badge>,
    },
    {
      key: 'status',
      title: 'Status',
      cell: (m: MigrationRow) => (
        <Badge variant={m.status === 'SUCCESS' ? 'green' : 'danger'}>
          {m.status === 'SUCCESS' ? 'ناجحة' : 'فاشلة'}
        </Badge>
      ),
    },
    {
      key: 'executedBy',
      title: 'Executed By',
      cell: (m: MigrationRow) => <span className="text-yn-text-secondary">{m.executedBy || 'system'}</span>,
    },
    {
      key: 'executionTime',
      title: 'Duration',
      cell: (m: MigrationRow) => <span className="font-mono text-xs">{m.executionTime}ms</span>,
    },
    {
      key: 'executedAt',
      title: 'Executed At',
      cell: (m: MigrationRow) => <span className="text-xs text-yn-text-secondary">{m.executedAt?.replace('T', ' ').slice(0, 19) || '—'}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
            <GitBranch className="h-6 w-6 text-sovereign-gold" />
            هجرات قاعدة البيانات (Flyway)
          </h1>
          <p className="text-muted-foreground mt-1">
            {data.currentVersion ? `الإصدار الحالي: ${data.currentVersion}` : 'من سجل flyway_schema_history مباشرة'}
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="h-4 w-4" />
          تحديث
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard label="إجمالي الهجرات" value={data.total} icon={GitBranch} color="blue" />
        <StatCard label="ناجحة" value={data.total - data.failed} icon={CheckCircle} color="green" />
        <StatCard label="فاشلة" value={data.failed} icon={XCircle} color="danger" />
      </div>

      <DataTable columns={columns} data={migrations} rowKey="id" emptyMessage="لا هجرات مسجلة" />
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: number; icon: React.ComponentType<{ className?: string }>; color: string }) {
  const colorMap: Record<string, string> = {
    blue: 'text-blue-500 bg-blue-500/10',
    green: 'text-emerald-500 bg-emerald-500/10',
    danger: 'text-red-500 bg-red-500/10',
  };
  return (
    <div className="rounded-xl border border-border/60 bg-card p-4 flex items-center gap-4">
      <div className={`p-3 rounded-xl ${colorMap[color] || colorMap.blue}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-2xl font-bold text-foreground">{value.toLocaleString()}</p>
      </div>
    </div>
  );
}
