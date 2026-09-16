import { useState } from 'react';
import { HardDrive, Plus, Download, RefreshCw, Trash2, RotateCcw, MoreVertical, Eye, AlertTriangle, CheckCircle, XCircle, Clock } from 'lucide-react';
import { useBackups, mutations } from '@/api/queries';
import { cn, formatRelativeTime, formatBytes } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/SearchInput';
import { DataTable } from '@/components/ui/DataTable';
import { Pagination } from '@/components/ui/Pagination';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';
import { Dialog } from '@/components/ui/Dialog';
import { Form } from '@/components/ui/Form';

const TYPE_OPTIONS = [
  { value: 'FULL', label: 'Full Backup' },
  { value: 'INCREMENTAL', label: 'Incremental' },
  { value: 'CONFIG_ONLY', label: 'Config Only' },
  { value: 'USER_DATA', label: 'User Data' },
];

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'FAILED', label: 'Failed' },
];

const mockBackups = Array.from({ length: 20 }, (_, i) => ({
  id: `backup-${i}`,
  name: `backup-${new Date(Date.now() - i * 86400000).toISOString().split('T')[0]}`,
  type: ['FULL', 'INCREMENTAL', 'CONFIG_ONLY', 'USER_DATA'][i % 4],
  status: ['COMPLETED', 'COMPLETED', 'COMPLETED', 'IN_PROGRESS', 'FAILED'][i % 5],
  size: Math.floor(Math.random() * 10000000000) + 1000000000,
  startedAt: new Date(Date.now() - i * 86400000).toISOString(),
  completedAt: i % 5 !== 3 ? new Date(Date.now() - i * 86400000 + 3600000).toISOString() : undefined,
  notes: i % 3 === 0 ? 'Scheduled backup' : undefined,
}));

export function BackupsPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [restoreDialogOpen, setRestoreDialogOpen] = useState(false);
  const [restoringBackup, setRestoringBackup] = useState<any>(null);
  
  const { data: backups, isLoading } = useBackups();
  const createMutation = mutations.useCreateBackup();
  const restoreMutation = mutations.useRestoreBackup();
  const deleteMutation = mutations.useDeleteBackup();
  
  // Use mock data
  const logs = mockBackups.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(mockBackups.length / size);
  
  const columns = [
    {
      key: 'name',
      header: 'Backup Name',
      cell: (backup: any) => (
        <div>
          <p className="font-mono font-medium text-yn-text">{backup.name}</p>
          <p className="text-xs text-yn-text-muted">{backup.type}</p>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      cell: (backup: any) => <Badge variant="blue">{backup.type}</Badge>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (backup: any) => <Badge variant={getStatusVariant(backup.status)} dot>{backup.status}</Badge>,
    },
    {
      key: 'size',
      header: 'Size',
      cell: (backup: any) => <span className="text-yn-text-secondary font-mono">{formatBytes(backup.size)}</span>,
    },
    {
      key: 'startedAt',
      header: 'Started',
      cell: (backup: any) => <span className="text-yn-text-secondary">{formatRelativeTime(backup.startedAt)}</span>,
    },
    {
      key: 'completedAt',
      header: 'Completed',
      cell: (backup: any) => backup.completedAt ? <span className="text-yn-text-secondary">{formatRelativeTime(backup.completedAt)}</span> : <span className="text-yn-text-muted">—</span>,
    },
    {
      key: 'actions',
      header: 'Actions',
      cell: (backup: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            {backup.status === 'COMPLETED' && (
              <DropdownMenu.Item onClick={() => { setRestoringBackup(backup); setRestoreDialogOpen(true); }}>
                <RotateCcw className="w-4 h-4 mr-2" />
                Restore
              </DropdownMenu.Item>
            )}
            <DropdownMenu.Item>
              <Download className="w-4 h-4 mr-2" />
              Download
            </DropdownMenu.Item>
            <DropdownMenu.Item>
              <Eye className="w-4 h-4 mr-2" />
              View Details
            </DropdownMenu.Item>
            <DropdownMenu.Separator />
            <DropdownMenu.Item className="text-yn-error" onClick={() => deleteMutation.mutate(backup.id)}>
              <Trash2 className="w-4 h-4 mr-2" />
              Delete
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
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.backups.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.backups.subtitle')}</p>
        </div>
        <Button variant="primary" size="sm" onClick={() => setDialogOpen(true)}>
          <Plus className="w-4 h-4 mr-2" />
          Create Backup
        </Button>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={logs} rowKey="id" emptyMessage="No backups found" />
        
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalItems={mockBackups.length}
          onPageChange={setPage}
          onPageSizeChange={setSize}
          pageSize={size}
        />
      </div>
      
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title="Create Backup">
        <Form onSubmit={(data) => { createMutation.mutate(data); setDialogOpen(false); }}>
          <Form.Field name="type" label="Backup Type" type="select" options={TYPE_OPTIONS} required />
          <Form.Field name="notes" label="Notes" type="textarea" placeholder="Optional notes" />
        </Form>
      </Dialog>
      
      <Dialog open={restoreDialogOpen} onClose={() => { setRestoreDialogOpen(false); setRestoringBackup(null); }} title="Restore Backup" description="This will restore the system to the state at backup time. This action cannot be undone.">
        {restoringBackup && (
          <Form onSubmit={(data) => { restoreMutation.mutate({ backupId: restoringBackup.id, confirmCode: data.confirmCode }); setRestoreDialogOpen(false); setRestoringBackup(null); }}>
            <div className="mb-4 p-3 bg-yn-error/10 border border-yn-error/30 rounded-lg">
              <p className="text-yn-error font-medium">Warning: This will overwrite current data!</p>
              <p className="text-sm text-yn-text-secondary mt-1">Backup: {restoringBackup.name} ({restoringBackup.type})</p>
            </div>
            <Form.Field name="confirmCode" label="Type 'RESTORE' to confirm" placeholder="RESTORE" required />
          </Form>
        )}
      </Dialog>
    </div>
  );
}

function getStatusVariant(status: string): 'green' | 'gold' | 'blue' | 'danger' {
  switch (status) {
    case 'COMPLETED': return 'green';
    case 'IN_PROGRESS': return 'blue';
    case 'PENDING': return 'gold';
    case 'FAILED': return 'danger';
    default: return 'gold';
  }
}