import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Server, Trash2, RefreshCw, Zap, AlertTriangle, ChevronLeft, ChevronRight } from 'lucide-react';
import { formatBytes } from '@/utils';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { apiFetch } from '../../api';
import { LoadingState, ErrorState } from '../_shared';

/**
 * متصفح كاش Redis — على العقد الحقيقي للخادم (2026-09-27).
 *
 * كانت الصفحة تعرض 50 مفتاحاً وهمياً وإحصاءات ذاكرة مُختلقة — الآن تقرأ
 * GET /api/admin/system/cache (AdminSystemOpsController: SCAN + INFO memory/stats)
 * وتبطل بالنمط عبر POST /api/admin/system/cache/invalidate (بديل آمن عن FLUSHALL).
 */

interface CacheKeyRow {
  key: string;
  type: string;
  ttl: number;
  size: number | null;
  memory: number | null;
}

interface CacheMemory {
  used: number | null;
  peak: number | null;
  limit: number | null;
  fragmentation: number | null;
  keys: number;
  expired: number;
  evicted: number;
}

interface CacheResponse {
  keys: CacheKeyRow[];
  truncated: boolean;
  pattern: string;
  memory: CacheMemory;
}

const PAGE_SIZE = 25;

export function CachePage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [invalidateOpen, setInvalidateOpen] = useState(false);
  const [invalidatePattern, setInvalidatePattern] = useState('');
  const [invalidating, setInvalidating] = useState(false);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['system', 'cache', search],
    queryFn: async (): Promise<CacheResponse> => {
      const params = new URLSearchParams({ count: '200' });
      if (search.trim()) params.set('pattern', search.trim());
      const res = await apiFetch(`/api/admin/system/cache?${params}`);
      if (!res.ok) throw new Error(`HTTP ${res.status} — /api/admin/system/cache`);
      return (await res.json()) as CacheResponse;
    },
    staleTime: 15_000,
    refetchInterval: 30_000,
    retry: 1,
  });

  const allKeys = data?.keys ?? [];
  const totalPages = Math.max(1, Math.ceil(allKeys.length / PAGE_SIZE));
  const keys = allKeys.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const mem = data?.memory;

  const invalidate = async () => {
    setInvalidating(true);
    try {
      const res = await apiFetch('/api/admin/system/cache/invalidate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pattern: invalidatePattern.trim() || '*', limit: 500 }),
      });
      if (res.ok) {
        setInvalidateOpen(false);
        setInvalidatePattern('');
        queryClient.invalidateQueries({ queryKey: ['system', 'cache'] });
      }
    } finally {
      setInvalidating(false);
    }
  };

  if (isLoading) return <LoadingState label="مسح مفاتيح Redis…" />;
  if (isError) return <ErrorState onRetry={refetch} message="تعذر قراءة الكاش من الخادم — تأكد من عمل Redis" />;

  const columns = [
    {
      key: 'key',
      title: 'Key',
      cell: (k: CacheKeyRow) => <span className="font-mono text-xs text-yn-text break-all">{k.key}</span>,
    },
    {
      key: 'type',
      title: 'Type',
      cell: (k: CacheKeyRow) => <Badge variant="blue">{k.type}</Badge>,
    },
    {
      key: 'ttl',
      title: 'TTL',
      cell: (k: CacheKeyRow) => (
        <span className="font-mono text-xs">
          {k.ttl === -1 ? <Badge variant="gold">دائم</Badge> : k.ttl === -2 ? 'منتهي' : `${k.ttl}s`}
        </span>
      ),
    },
    {
      key: 'memory',
      title: 'Memory',
      cell: (k: CacheKeyRow) => <span className="text-xs text-yn-text-secondary">{k.memory != null ? formatBytes(k.memory) : '—'}</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
            <Server className="h-6 w-6 text-sovereign-gold" />
            كاش Redis
          </h1>
          <p className="text-muted-foreground mt-1">
            {data?.truncated ? `أول ${allKeys.length} مفتاحاً مطابقاً (مقتطع)` : `${allKeys.length} مفتاحاً مطابقاً`}
            {data?.pattern && data.pattern !== '*' ? ` — النمط: ${data.pattern}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="h-4 w-4" />
            تحديث
          </Button>
          <Button variant="destructive" size="sm" onClick={() => setInvalidateOpen(true)}>
            <Trash2 className="h-4 w-4" />
            إبطال بالنمط
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <StatCard label="الذاكرة المستخدمة" value={mem?.used != null ? formatBytes(mem.used) : '—'} icon={Server} color="blue" />
        <StatCard label="ذروة الذاكرة" value={mem?.peak != null ? formatBytes(mem.peak) : '—'} icon={Zap} color="gold" />
        <StatCard label="حد الذاكرة" value={mem?.limit != null && mem.limit > 0 ? formatBytes(mem.limit) : 'بلا حد'} icon={Server} color="blue" />
        <StatCard
          label="التجزئة"
          value={mem?.fragmentation != null ? `${mem.fragmentation}x` : '—'}
          icon={AlertTriangle}
          color={mem?.fragmentation != null && mem.fragmentation > 1.5 ? 'danger' : 'gold'}
        />
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <SearchInput
          value={search}
          onChange={(v: string) => { setSearch(v); setPage(0); }}
          placeholder="فلترة بالنمط (مثال: session:*)…"
        />
        <div className="flex items-center gap-2 text-xs text-muted-foreground flex-wrap">
          <span>مفاتيح القاعدة: {mem?.keys?.toLocaleString() ?? '—'}</span>
          <span>· منتهية: {mem?.expired?.toLocaleString() ?? '—'}</span>
          <span>· مطرودة: {mem?.evicted?.toLocaleString() ?? '—'}</span>
        </div>
      </div>

      <DataTable columns={columns} data={keys} rowKey="key" emptyMessage="لا مفاتيح مطابقة" />

      <div className="flex items-center justify-between">
        <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
          <ChevronLeft className="h-4 w-4" /> السابق
        </Button>
        <span className="text-sm text-muted-foreground">صفحة {page + 1} من {totalPages}</span>
        <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>
          التالي <ChevronRight className="h-4 w-4" />
        </Button>
      </div>

      {invalidateOpen && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={() => setInvalidateOpen(false)}>
          <div className="bg-background rounded-xl border border-border p-6 w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-bold text-foreground flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 text-amber-500" />
              إبطال مفاتيح بالنمط
            </h3>
            <p className="text-sm text-muted-foreground">
              بديل آمن عن FLUSHALL — يُحذف فقط ما يطابق النمط بحد 500 مفتاح.
              مثال: <code className="font-mono">session:*</code>
            </p>
            <input
              className="w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm text-foreground"
              value={invalidatePattern}
              onChange={(e) => setInvalidatePattern(e.target.value)}
              placeholder="pattern:*"
            />
            <div className="flex justify-end gap-2">
                            <Button variant="outline" onClick={() => setInvalidateOpen(false)}>إلغاء</Button>
              <Button variant="destructive" onClick={invalidate} disabled={invalidating}>
                <Trash2 className="h-4 w-4" />
                {invalidating ? 'جارٍ الإبطال…' : 'إبطال'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: string; icon: React.ComponentType<{ className?: string }>; color: string }) {
  const colorMap: Record<string, string> = {
    blue: 'text-blue-500 bg-blue-500/10',
    gold: 'text-amber-500 bg-amber-500/10',
    danger: 'text-red-500 bg-red-500/10',
  };
  return (
    <div className="rounded-xl border border-border/60 bg-card p-4 flex items-center gap-4">
      <div className={`p-3 rounded-xl ${colorMap[color] || colorMap.blue}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div className="min-w-0">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-xl font-bold text-foreground truncate">{value}</p>
      </div>
    </div>
  );
}
