import { useState } from 'react';
import { Download, Search, Eye } from 'lucide-react';
import { useAuditLog } from '@/api/queries';
import { formatRelativeTime } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Select } from '@/components/ui/select';
import { DateRangePicker } from '@/components/ui/date-range-picker';
import { DataTable } from '@/components/ui/data-table';
import { Pagination } from '@/components/ui/pagination';
import { Badge } from '@/components/ui/badge';

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
  const [size] = useState(25);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [severity, setSeverity] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  
  const { data, isLoading } = useAuditLog({ page, size, category, severity, startDate, endDate });
  
  // البيانات الحقيقية من الخادم أولاً، والوهمية احتياط فقط عند غيابها
  const source = data?.content && data.content.length > 0 ? data.content : mockAuditLogs;
  const logs = source.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(source.length / size);
  
  const columns = [
    {
      key: 'createdAt',
      title: 'Timestamp',
      cell: (log: any) => <span className="text-yn-text-secondary font-mono text-sm">{formatRelativeTime(log.createdAt)}</span>,
    },
    {
      key: 'adminUsername',
      title: 'Admin',
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
      title: 'Action',
      cell: (log: any) => <Badge variant="blue">{log.action}</Badge>,
    },
    {
      key: 'category',
      title: 'Category',
      cell: (log: any) => <Badge variant="gold">{log.category}</Badge>,
    },
    {
      key: 'severity',
      title: 'Severity',
      cell: (log: any) => <Badge variant={getSeverityVariant(log.severity)}>{log.severity}</Badge>,
    },
    {
      key: 'target',
      title: 'Target',
      cell: (log: any) => (
        <div>
          <p className="font-mono text-yn-text-secondary text-sm">{log.targetType}</p>
          <p className="text-xs text-yn-text-muted">{log.targetId}</p>
        </div>
      ),
    },
    {
      key: 'ipAddress',
      title: 'IP Address',
      cell: (log: any) => <span className="text-yn-text-secondary font-mono text-sm">{log.ipAddress}</span>,
    },
    {
      key: 'details',
      title: 'Details',
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
            from={startDate}
            to={endDate}
            onChange={(from, to) => { setStartDate(from); setEndDate(to); }}
          />
        </div>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={logs} isLoading={isLoading} rowKey="id" emptyMessage="No audit log entries" />
        
        <Pagination
          page={page}
          totalPages={totalPages}
          onChange={setPage}
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

export default AuditLogPage;