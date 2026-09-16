import { GitBranch, AlertTriangle, CheckCircle, XCircle, RefreshCw, Download, MoreVertical, Eye, Play, Pause } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils';
import { Button } from '@/components/ui/Button';
import { DataTable } from '@/components/ui/DataTable';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';

const mockMigrations = Array.from({ length: 15 }, (_, i) => ({
  id: `migration-${i}`,
  version: `V${String(100 + i).padStart(4, '0')}`,
  description: `Migration ${i + 1}: ${['Add users table', 'Add indexes', 'Update schema', 'Add foreign keys', 'Create views'][i % 5]}`,
  type: ['SQL', 'JAVA', 'SHELL'][i % 3],
  status: ['SUCCESS', 'SUCCESS', 'SUCCESS', 'PENDING', 'FAILED'][i % 5],
  checksum: `abc123${i}`,
  executedBy: `system`,
  executedAt: new Date(Date.now() - i * 3600000).toISOString(),
  executionTime: Math.floor(Math.random() * 5000) + 100,
}));

export function MigrationsPage() {
  const { t } = useTranslation();
  
  const columns = [
    {
      key: 'version',
      header: 'Version',
      cell: (m: any) => <span className="font-mono font-medium text-yn-text">{m.version}</span>,
    },
    {
      key: 'description',
      header: 'Description',
      cell: (m: any) => <span className="text-yn-text-secondary">{m.description}</span>,
    },
    {
      key: 'type',
      header: 'Type',
      cell: (m: any) => <Badge variant="blue">{m.type}</Badge>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (m: any) => <Badge variant={getStatusVariant(m.status)} dot>{m.status}</Badge>,
    },
    {
      key: 'checksum',
      header: 'Checksum',
      cell: (m: any) => <span className="font-mono text-yn-text-secondary text-sm">{m.checksum}</span>,
    },
    {
      key: 'executedBy',
      header: 'Executed By',
      cell: (m: any) => <span className="text-yn-text-secondary">{m.executedBy}</span>,
    },
    {
      key: 'executedAt',
      header: 'Executed At',
      cell: (m: any) => m.executedAt ? <span className="text-yn-text-secondary">{new Date(m.executedAt).toLocaleString()}</span> : <span className="text-yn-text-muted">—</span>,
    },
    {
      key: 'executionTime',
      header: 'Duration',
      cell: (m: any) => <span className="text-yn-text-secondary font-mono">{m.executionTime}ms</span>,
    },
    {
      key: 'actions',
      header: 'Actions',
      cell: (m: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Item>
              <Eye className="w-4 h-4 mr-2" />
              View SQL
            </DropdownMenu.Item>
            {m.status === 'PENDING' && (
              <DropdownMenu.Item>
                <Play className="w-4 h-4 mr-2" />
                Execute
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
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.migrations.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.migrations.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export Report
          </Button>
          <Button variant="secondary" size="sm">
            <RefreshCw className="w-4 h-4 mr-2" />
            Check for Pending
          </Button>
        </div>
      </div>
      
      {/* Status Summary */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Total Migrations" value={mockMigrations.length} icon={GitBranch} color="blue" />
        <StatCard label="Applied" value={mockMigrations.filter(m => m.status === 'SUCCESS').length} icon={CheckCircle} color="green" />
        <StatCard label="Pending" value={mockMigrations.filter(m => m.status === 'PENDING').length} icon={AlertTriangle} color="gold" />
        <StatCard label="Failed" value={mockMigrations.filter(m => m.status === 'FAILED').length} icon={XCircle} color="danger" />
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={mockMigrations} rowKey="id" emptyMessage="No migrations found" />
      </div>
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

function getStatusVariant(status: string): 'green' | 'gold' | 'blue' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'green';
    case 'PENDING': return 'gold';
    case 'FAILED': return 'danger';
    default: return 'gold';
  }
}