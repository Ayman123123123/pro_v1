import { useState } from 'react';
import { Activity, Monitor, Smartphone, MapPin, X, MoreVertical, RefreshCw, ShieldCheck } from 'lucide-react';
import { useAllSessions, mutations } from '@/api/queries';
import { cn, formatRelativeTime } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { DataTable } from '@/components/ui/data-table';
import { Pagination } from '@/components/ui/pagination';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu } from '@/components/ui/simple-dropdown-menu';
import { Dialog } from '@/components/ui/dialog';
import { Form, FormField } from '@/components/ui/form';

/**
 * جلسات الأدمن النشطة — على العقد الحقيقي للخادم (2026-09-27).
 *
 * GET  /api/admin/sessions                       → كيان AdminSession (نشطة وغير منتهية فقط)
 * POST /api/admin/sessions/{id}/terminate        → إنهاء جلسة بسبب (بتدقيق SECURITY)
 * POST /api/admin/sessions/cleanup               → تنظيف الجلسات المنتهية (بدل revoke-all الوهمي)
 *
 * كانت الصفحة تعرض 100 جلسة مُختلقة بمستخدمين وهميين لأنها كانت تنادي /security/sessions
 * غير الموجودة — الآن تقرأ الحقول الحقيقية: adminId, ipAddress, userAgent, deviceInfo,
 * startedAt, lastActiveAt, expiresAt, isActive.
 */

function derivePlatform(ua?: string | null): 'Mobile' | 'Desktop' {
  if (!ua) return 'Desktop';
  return /mobile|android|iphone|ipad/i.test(ua) ? 'Mobile' : 'Desktop';
}

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) : id;
}

export function SessionsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [search, setSearch] = useState('');
  const [platform, setPlatform] = useState('');
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false);
  const [revokingSession, setRevokingSession] = useState<any>(null);
  const [cleanupDialogOpen, setCleanupDialogOpen] = useState(false);

  const { data: sessions, isLoading, error, refetch } = useAllSessions();
  const revokeMutation = mutations.useRevokeSession();
  const cleanupMutation = mutations.useRevokeAllSessions();

  const errorText = String((error as any)?.message ?? '');
  const list = Array.isArray(sessions) ? sessions : [];
  const filtered = list.filter((s: any) => {
    if (platform && derivePlatform(s.userAgent) !== platform) return false;
    if (search) {
      const hay = `${s.ipAddress || ''} ${s.userAgent || ''} ${s.deviceInfo || ''} ${shortId(s.adminId || '')}`.toLowerCase();
      if (!hay.includes(search.toLowerCase())) return false;
    }
    return true;
  });
  const displaySessions = filtered.slice(page * size, (page + 1) * size);
  const totalPages = Math.max(1, Math.ceil(filtered.length / size));
  const activeNow = list.filter((s: any) => new Date(s.lastActiveAt).getTime() > Date.now() - 300000).length;

  const columns = [
    {
      key: 'admin',
      title: 'Admin',
      cell: (s: any) => (
        <div>
          <p className="font-medium text-yn-text font-mono text-sm">{shortId(s.adminId || s.id)}</p>
          <p className="text-xs text-yn-text-muted">admin session</p>
        </div>
      ),
    },
    {
      key: 'device',
      title: 'Device',
      cell: (s: any) => {
        const plat = derivePlatform(s.userAgent);
        return (
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-yn-blue/15 flex items-center justify-center">
              {plat === 'Mobile' ? <Smartphone className="w-4 h-4 text-yn-blue" /> : <Monitor className="w-4 h-4 text-yn-blue" />}
            </div>
            <div className="max-w-xs">
              <p className="text-sm text-yn-text truncate">{s.deviceInfo || s.userAgent?.slice(0, 60) || '—'}</p>
              <p className="text-xs text-yn-text-muted">{plat}</p>
            </div>
          </div>
        );
      },
    },
    {
      key: 'location',
      title: 'Location',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <MapPin className="w-4 h-4 text-yn-text-muted" />
          <span className="text-yn-text-secondary">{s.location || '—'}</span>
        </div>
      ),
    },
    {
      key: 'ipAddress',
      title: 'IP Address',
      cell: (s: any) => <span className="font-mono text-yn-text-secondary text-sm">{s.ipAddress || '—'}</span>,
    },
    {
      key: 'status',
      title: 'Status',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <Badge variant={s.isActive ? 'green' : 'danger'}>{s.isActive ? 'Active' : 'Terminated'}</Badge>
          {new Date(s.expiresAt) < new Date() && <Badge variant="gold">Expired</Badge>}
          {s.isCurrent === true && <Badge variant="blue">Current</Badge>}
        </div>
      ),
    },
    {
      key: 'startedAt',
      title: 'Started',
      cell: (s: any) => <span className="text-yn-text-secondary">{formatRelativeTime(s.startedAt)}</span>,
    },
    {
      key: 'lastActiveAt',
      title: 'Last Activity',
      cell: (s: any) => <span className="text-yn-text-secondary">{formatRelativeTime(s.lastActiveAt)}</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: (s: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            {s.isActive && (
              <DropdownMenu.Item onClick={() => { setRevokingSession(s); setRevokeDialogOpen(true); }}>
                <X className="w-4 h-4 mr-2" />
                Terminate Session
              </DropdownMenu.Item>
            )}
          </DropdownMenu.Content>
        </DropdownMenu>
      ),
    },
  ];

  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.sessions.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.sessions.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={() => refetch()}>
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setCleanupDialogOpen(true)}>
            <ShieldCheck className="w-4 h-4 mr-2" />
            Cleanup Expired
          </Button>
        </div>
      </div>

      {errorText.length > 0 && (
        <div className="yn-card yn-glass p-4 text-sm text-yn-error border border-yn-error/30">
          تعذر جلب الجلسات من الخادم عبر <span className="font-mono">GET /api/admin/sessions</span>: {errorText}
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Total Sessions" value={list.length} icon={Activity} color="blue" />
        <StatCard label="Active Now (5m)" value={activeNow} icon={Activity} color="green" />
        <StatCard label="Filtered" value={filtered.length} icon={Monitor} color="gold" />
        <StatCard label="Page" value={`${page + 1}/${totalPages}`} icon={ShieldCheck} color="green" />
      </div>

      <div className="yn-card yn-card-liquid yn-glass">
        <div className="p-4 border-b border-yn-border">
          <div className="flex flex-col sm:flex-row gap-4">
            <SearchInput value={search} onChange={setSearch} placeholder="Search by IP / agent / device..." className="flex-1 max-w-sm" />
            <select
              value={platform}
              onChange={(e) => setPlatform(e.target.value)}
              className="yn-input w-full sm:w-40"
              aria-label="Platform filter"
            >
              <option value="">All Platforms</option>
              <option value="Web">Web / Desktop</option>
              <option value="Mobil
le" />
            </select>
          </div>
        </div>

        <DataTable columns={columns} data={displaySessions} isLoading={isLoading} rowKey="id" emptyMessage="No active admin sessions" />

        <Pagination
          page={page}
          totalPages={totalPages}
          onChange={setPage}
        />
      </div>

      <Dialog open={revokeDialogOpen} onClose={() => { setRevokeDialogOpen(false); setRevokingSession(null); }} title="Terminate Session">
        {revokingSession && (
          <Form onSubmit={(data: any) => { revokeMutation.mutate({ sessionId: revokingSession.id, reason: data.reason }); setRevokeDialogOpen(false); setRevokingSession(null); }}>
            <p className="text-sm text-yn-text-secondary mb-4">
              إنهاء الجلسة <span className="font-mono text-yn-text">{shortId(revokingSession.id)}</span> من <span className="text-yn-text">{revokingSession.ipAddress || 'IP غير معروف'}</span> — سيتطلب دخولاً جديداً.
            </p>
            <FormField name="reason" label="Reason" type="textarea" placeholder="سبب الإنهاء" required />
          </Form>
        )}
      </Dialog>

      <Dialog open={cleanupDialogOpen} onClose={() => setCleanupDialogOpen(false)} title="Cleanup Expired Sessions">
        <Form onSubmit={() => { cleanupMutation.mutate(''); setCleanupDialogOpen(false); }}>
          <p className="text-sm text-yn-text-secondary mb-4">
            يحذف كل الجلسات المنتهية صلاحيتها من الخادم (POST /api/admin/sessions/cleanup) ويسجّل العملية في سجل التدقيق.
          </p>
          <Button variant="primary" type="submit" className="w-full">Run Cleanup</Button>
        </Form>
      </Dialog>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: number | string; icon: React.ComponentType<{ className?: string }>; color: string }) {
  return (
    <div className="yn-card yn-card-liquid yn-glass p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-yn-text-secondary">{label}</p>
          <p className="text-2xl font-bold text-yn-text mt-1">{value}</p>
        </div>
        <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center', `bg-yn-${color}/15`)} >
          <Icon className={cn('w-5 h-5', `text-yn-${color}`)} aria-hidden="true" />
        </div>
      </div>
    </div>
  );
}

export default SessionsPage;
