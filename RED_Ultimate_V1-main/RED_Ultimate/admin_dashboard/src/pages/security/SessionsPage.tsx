import { useState } from 'react';
import { Activity, Monitor, Smartphone, MapPin, Shield, X, Download, Filter, MoreVertical, RotateCcw, Eye, Trash2 } from 'lucide-react';
import { useAllSessions, mutations } from '@/api/queries';
import { cn, formatRelativeTime } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/SearchInput';
import { Select } from '@/components/ui/Select';
import { DataTable } from '@/components/ui/DataTable';
import { Pagination } from '@/components/ui/Pagination';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';
import { Dialog } from '@/components/ui/Dialog';

const mockSessions = Array.from({ length: 100 }, (_, i) => ({
  id: `session-${i}`,
  userId: `user-${i % 20}`,
  username: `user${i % 20}`,
  deviceId: `device-${i}`,
  deviceName: ['Chrome on Windows', 'Firefox on Mac', 'Safari on iPhone', 'Android App', 'iOS App'][i % 5],
  platform: ['Web', 'Mobile', 'Desktop'][i % 3],
  ipAddress: `192.168.${i % 255}.${Math.floor(Math.random() * 255)}`,
  location: ['New York, US', 'London, UK', 'Tokyo, JP', 'Sydney, AU', 'Berlin, DE'][i % 5],
  userAgent: 'Mozilla/5.0...',
  createdAt: new Date(Date.now() - i * 3600000).toISOString(),
  lastActivityAt: new Date(Date.now() - Math.random() * 3600000).toISOString(),
  isCurrent: i === 0,
  isTrusted: i % 3 === 0,
  riskScore: Math.floor(Math.random() * 100),
}));

export function SessionsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [search, setSearch] = useState('');
  const [platform, setPlatform] = useState('');
  const [trusted, setTrusted] = useState('');
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false);
  const [revokingSession, setRevokingSession] = useState<any>(null);
  const [revokeAllDialogOpen, setRevokeAllDialogOpen] = useState(false);
  const [revokeAllUser, setRevokeAllUser] = useState<any>(null);
  
  const { data: sessions, isLoading } = useAllSessions();
  const revokeMutation = mutations.useRevokeSession();
  const revokeAllMutation = mutations.useRevokeAllSessions();
  
  const displaySessions = mockSessions.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(mockSessions.length / size);
  
  const columns = [
    {
      key: 'user',
      header: 'User',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-full bg-yn-green/15 flex items-center justify-center text-xs text-yn-green font-bold">
            {s.username[0]}
          </div>
          <div>
            <p className="font-medium text-yn-text">{s.username}</p>
            <p className="text-xs text-yn-text-muted font-mono">{s.userId}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'device',
      header: 'Device',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-yn-blue/15 flex items-center justify-center">
            {s.platform === 'Mobile' ? <Smartphone className="w-4 h-4 text-yn-blue" /> : <Monitor className="w-4 h-4 text-yn-blue" />}
          </div>
          <div>
            <p className="font-medium text-yn-text">{s.deviceName}</p>
            <p className="text-xs text-yn-text-muted">{s.platform}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'location',
      header: 'Location',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <MapPin className="w-4 h-4 text-yn-text-muted" />
          <span className="text-yn-text-secondary">{s.location}</span>
        </div>
      ),
    },
    {
      key: 'ipAddress',
      header: 'IP Address',
      cell: (s: any) => <span className="font-mono text-yn-text-secondary text-sm">{s.ipAddress}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <Badge variant={s.isTrusted ? 'green' : 'gold'} dot>
            {s.isTrusted ? 'Trusted' : 'Untrusted'}
          </Badge>
          {s.isCurrent && <Badge variant="blue" className="ml-1">Current</Badge>}
        </div>
      ),
    },
    {
      key: 'riskScore',
      header: 'Risk Score',
      cell: (s: any) => (
        <div className="flex items-center gap-2">
          <div className="w-20 h-2 bg-yn-navy rounded-full overflow-hidden">
            <div className="h-full bg-gradient-to-r from-yn-green via-yn-gold to-yn-error rounded-full" style={{ width: `${s.riskScore}%` }} />
          </div>
          <span className="text-sm font-mono text-yn-text">{s.riskScore}%</span>
        </div>
      ),
    },
    {
      key: 'createdAt',
      header: 'Created',
      cell: (s: any) => <span className="text-yn-text-secondary">{formatRelativeTime(s.createdAt)}</span>,
    },
    {
      key: 'lastActivityAt',
      header: 'Last Activity',
      cell: (s: any) => <span className="text-yn-text-secondary">{formatRelativeTime(s.lastActivityAt)}</span>,
    },
    {
      key: 'actions',
      header: 'Actions',
      cell: (s: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Item onClick={() => { setRevokingSession(s); setRevokeDialogOpen(true); }}>
              <X className="w-4 h-4 mr-2" />
              Revoke Session
            </DropdownMenu.Item>
            <DropdownMenu.Item onClick={() => { setRevokeAllUser({ userId: s.userId, username: s.username }); setRevokeAllDialogOpen(true); }}>
              <RotateCcw className="w-4 h-4 mr-2" />
              Revoke All User Sessions
            </DropdownMenu.Item>
            <DropdownMenu.Separator />
            <DropdownMenu.Item onClick={() => { /* trust */ }}>
              <Shield className="w-4 h-4 mr-2" />
              {s.isTrusted ? 'Mark Untrusted' : 'Mark Trusted'}
            </DropdownMenu.Item>
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
          <Button variant="secondary" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Button variant="secondary" size="sm">
            <Filter className="w-4 h-4 mr-2" />
            Filters
          </Button>
        </div>
      </div>
      
      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Total Sessions" value={mockSessions.length} icon={Activity} color="blue" />
        <StatCard label="Active Now" value={mockSessions.filter(s => new Date(s.lastActivityAt).getTime() > Date.now() - 300000).length} icon={Activity} color="green" />
        <StatCard label="Trusted Devices" value={mockSessions.filter(s => s.isTrusted).length} icon={Shield} color="gold" />
        <StatCard label="High Risk" value={mockSessions.filter(s => s.riskScore > 70).length} icon={AlertTriangle} color="danger" />
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass">
        <div className="p-4 border-b border-yn-border">
          <div className="flex flex-col sm:flex-row gap-4">
            <SearchInput value={search} onChange={setSearch} placeholder="Search sessions..." className="flex-1 max-w-sm" />
            <Select value={platform} onChange={setPlatform} options={[
              { value: '', label: 'All Platforms' },
              { value: 'Web', label: 'Web' },
              { value: 'Mobile', label: 'Mobile' },
              { value: 'Desktop', label: 'Desktop' },
            ]} placeholder="Platform" className="w-full sm:w-40" />
            <Select value={trusted} onChange={setTrusted} options={[
              { value: '', label: 'All' },
              { value: 'true', label: 'Trusted' },
              { value: 'false', label: 'Untrusted' },
            ]} placeholder="Trust" className="w-full sm:w-40" />
          </div>
        </div>
        
        <DataTable columns={columns} data={displaySessions} isLoading={isLoading} rowKey="id" emptyMessage="No sessions found" />
        
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalItems={mockSessions.length}
          onPageChange={setPage}
          onPageSizeChange={setSize}
          pageSize={size}
        />
      </div>
      
      <Dialog open={revokeDialogOpen} onClose={() => { setRevokeDialogOpen(false); setRevokingSession(null); }} title="Revoke Session" description="This will immediately terminate the selected session. The user will need to log in again.">
        {revokingSession && (
          <Form onSubmit={(data) => { revokeMutation.mutate({ sessionId: revokingSession.id, reason: data.reason }); setRevokeDialogOpen(false); setRevokingSession(null); }}>
            <p className="text-sm text-yn-text-secondary mb-4">
              Revoking session for <span className="font-medium text-yn-text">{revokingSession.username}</span> on <span className="font-medium text-yn-text">{revokingSession.deviceName}</span>
            </p>
            <Form.Field name="reason" label="Reason" type="textarea" placeholder="Reason for revocation" required />
          </Form>
        )}
      </Dialog>
      
      <Dialog open={revokeAllDialogOpen} onClose={() => { setRevokeAllDialogOpen(false); setRevokeAllUser(null); }} title="Revoke All User Sessions" description="This will terminate ALL sessions for the selected user across all devices.">
        {revokeAllUser && (
          <Form onSubmit={(data) => { revokeAllMutation.mutate(revokeAllUser.userId); setRevokeAllDialogOpen(false); setRevokeAllUser(null); }}>
            <p className="text-sm text-yn-text-secondary mb-4">
              Revoking all sessions for <span className="font-medium text-yn-text">{revokeAllUser.username}</span>
            </p>
            <Form.Field name="reason" label="Reason" type="textarea" placeholder="Reason for mass revocation" required />
          </Form>
        )}
      </Dialog>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: number; icon: React.ComponentType<{ className?: string }>; color: string }) {
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