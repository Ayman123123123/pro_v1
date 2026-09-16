import { useState } from 'react';
import { History, Filter, Download, Search, ChevronLeft, ChevronRight, MoreVertical, Eye, AlertTriangle, CheckCircle, XCircle, Info } from 'lucide-react';
import { useAuditLog } from '@/api/queries';
import { cn, formatRelativeTime, getStatusColor } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/SearchInput';
import { Select } from '@/components/ui/Select';
import { DateRangePicker } from '@/components/ui/DateRangePicker';
import { DataTable } from '@/components/ui/DataTable';
import { Pagination } from '@/components/ui/Pagination';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';

const CATEGORY_OPTIONS = [
  { value: '', label: 'All Categories' },
  { value: 'USER_MANAGEMENT', label: 'User Management' },
  { value: 'CONTENT_MODERATION', label: 'Content Moderation' },
  { value: 'SYSTEM_CONFIG', label: 'System Config' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'BACKUP', label: 'Backup' },
  { value: 'FEATURE_FLAGS', label: 'Feature Flags' },
];

const SEVERITY_OPTIONS = [
  { value: '', label: 'All Severities' },
  { value: 'INFO', label: 'Info' },
  { value: 'WARNING', label: 'Warning' },
  { value: 'ERROR', label: 'Error' },
  { value: 'CRITICAL', label: 'Critical' },
];

const mockAuditLogs = Array.from({ length: 50 }, (_, i) => ({
  id: `audit-${i}`,
  adminId: `admin-${i % 5}`,
  adminUsername: `admin${i % 5}`,
  action: ['CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'BAN', 'UNBAN'][i % 7],
  category: ['USER_MANAGEMENT', 'CONTENT_MODERATION', 'SYSTEM_CONFIG', 'SECURITY'][i % 4],
  severity: ['INFO', 'WARNING', 'ERROR', 'CRITICAL'][i % 4],
  targetType: ['User', 'Channel', 'Config', 'FeatureFlag'][i % 4],
  targetId: `target-${i}`,
  details: { reason: 'Test reason', metadata: {} },
  ipAddress: `192.168.1.${i % 255}`,
  userAgent: 'Mozilla/5.0 (Admin Dashboard)',
  createdAt: new Date(Date.now() - i * 3600000).toISOString(),
}));

export function AuditLogPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [severity, setSeverity] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  
  const { data, isLoading } = useAuditLog({ page, size, category, severity, startDate, endDate });
  
  // Use mock data for now
  const logs = mockAuditLogs.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(mockAuditLogs.length / size);
  
  const columns = [
    {
      key: 'createdAt',
      header: 'Timestamp',
      cell: (log: any) => <span className="text-yn-text-secondary font-mono text-sm">{formatRelativeTime(log.createdAt)}</span>,
    },
    {
      key: 'adminUsername',
      header: 'Admin',
      cell: (log: any) => (
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-full bg-yn-green/15 flex items-center justify-center text-xs text-yn-green font-bold">
            {log.adminUsername[0]}
          </div>
          <span className="font-medium text-yn-text">{log.adminUsername}</span>
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      cell: (log: any) => <Badge variant="blue">{log.action}</Badge>,
    },
    {
      key: 'category',
      header: 'Category',
      cell: (log: any) => <Badge variant="gold">{log.category}</Badge>,
    },
    {
      key: 'severity',
      header: 'Severity',
      cell: (log: any) => <Badge variant={getSeverityVariant(log.severity)} dot>{log.severity}</Badge>,
    },
    {
      key: 'target',
      header: 'Target',
      cell: (log: any) => (
        <div>
          <p className="font-mono text-yn-text-secondary text-sm">{log.targetType}</p>
          <p className="text-xs text-yn-text-muted">{log.targetId}</p>
        </div>
      ),
    },
    {
      key: 'ipAddress',
      header: 'IP Address',
      cell: (log: any) => <span className="text-yn-text-secondary font-mono text-sm">{log.ipAddress}</span>,
    },
    {
      key: 'details',
      header: 'Details',
      cell: (log: any) => (
        <Button variant="ghost" size="sm" className="w-full justify-start">
          <Eye className="w-4 h-4 mr-2" />
          View
        </Button>
      ),
    },
  ];
  
  if (isLoading && !data) {
    return <div className="yn-loading"><div className="yn-spinner" /></div>;
  }
  
  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.auditLog.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.auditLog.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Button variant="secondary" size="sm">
            <Search className="w-4 h-4 mr-2" />
            Full-text Search
          </Button>
        </div>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass p-4">
        <div className="flex flex-col sm:flex-row gap-4">
          <SearchInput value={search} onChange={setSearch} placeholder="Search audit logs..." className="flex-1 max-w-sm" />
          <Select value={category} onChange={setCategory} options={CATEGORY_OPTIONS} placeholder="Category" className="w-full sm:w-40" />
          <Select value={severity} onChange={setSeverity} options={SEVERITY_OPTIONS} placeholder="Severity" className="w-full sm:w-40" />
          <DateRangePicker
            value={{ start: startDate ? new Date(startDate) : new Date(Date.now() - 30 * 86400000), end: endDate ? new Date(endDate) : new Date() }}
            onChange={(range) => { setStartDate(range.start.toISOString()); setEndDate(range.end.toISOString()); }}
          />
        </div>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={logs} isLoading={isLoading} rowKey="id" emptyMessage="No audit log entries" />
        
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalItems={mockAuditLogs.length}
          onPageChange={setPage}
          onPageSizeChange={setSize}
          pageSize={size}
        />
      </div>
    </div>
  );
}

function getSeverityVariant(severity: string): 'green' | 'gold' | 'blue' | 'danger' {
  switch (severity) {
    case 'INFO': return 'blue';
    case 'WARNING': return 'gold';
    case 'ERROR': return 'danger';
    case 'CRITICAL': return 'danger';
    default: return 'blue';
  }
}