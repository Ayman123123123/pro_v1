import { useState } from 'react';
import { Plus, Edit, MoreVertical, Eye, Download, RefreshCw } from 'lucide-react';
import { useConfig, mutations } from '@/api/queries';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/SearchInput';
import { Select } from '@/components/ui/Select';
import { DataTable } from '@/components/ui/DataTable';
import { Pagination } from '@/components/ui/Pagination';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';
import { Dialog } from '@/components/ui/Dialog';
import { Form, FormField } from '@/components/ui/Form';

const TYPE_OPTIONS = [
  { value: '', label: 'All Types' },
  { value: 'STRING', label: 'String' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'BOOLEAN', label: 'Boolean' },
  { value: 'JSON', label: 'JSON' },
];

export function ConfigPage() {
  const { t } = useTranslation();
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<any>(null);
  
  const { data, isLoading } = useConfig({ page, size, search });
  
  const updateMutation = mutations.useUpdateConfig();
  const createMutation = mutations.useUpdateConfig(); // Reuse for create
  
  const columns = [
    {
      key: 'key',
      title: 'Key',
      cell: (config: any) => (
        <div>
          <p className="font-mono font-medium text-yn-text">{config.key}</p>
        </div>
      ),
    },
    {
      key: 'value',
      title: 'Value',
      cell: (config: any) => (
        <span className="font-mono text-yn-text-secondary max-w-xs truncate block">
          {config.type === 'JSON' ? JSON.stringify(config.value) : config.value}
        </span>
      ),
    },
    {
      key: 'type',
      title: 'Type',
      cell: (config: any) => <Badge variant="blue">{config.type}</Badge>,
    },
    {
      key: 'version',
      title: 'Version',
      cell: (config: any) => <span className="text-yn-text-secondary font-mono">v{config.version}</span>,
    },
    {
      key: 'description',
      title: 'Description',
      cell: (config: any) => <span className="text-yn-text-muted max-w-xs truncate block">{config.description || '—'}</span>,
    },
    {
      key: 'updatedAt',
      title: 'Updated',
      cell: (config: any) => <span className="text-yn-text-secondary">{new Date(config.updatedAt).toLocaleString()}</span>,
    },
    {
      key: 'updatedBy',
      title: 'Updated By',
      cell: (config: any) => <span className="text-yn-text-secondary">{config.updatedBy}</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: (config: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Item onClick={() => { setEditingConfig(config); setDialogOpen(true); }}>
              <Edit className="w-4 h-4 mr-2" />
              Edit
            </DropdownMenu.Item>
            <DropdownMenu.Item>
              <Eye className="w-4 h-4 mr-2" />
              View History
            </DropdownMenu.Item>
            <DropdownMenu.Item>
              <RefreshCw className="w-4 h-4 mr-2" />
              Rollback
            </DropdownMenu.Item>
          </DropdownMenu.Content>
        </DropdownMenu>
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
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.config.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.config.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm">
            <Download className="w-4 h-4 mr-2" />
            Export
          </Button>
          <Button variant="primary" size="sm" onClick={() => { setEditingConfig(null); setDialogOpen(true); }}>
            <Plus className="w-4 h-4 mr-2" />
            Add Config
          </Button>
        </div>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass p-4">
        <div className="flex flex-col sm:flex-row gap-4">
          <SearchInput value={search} onChange={setSearch} placeholder="Search config..." className="flex-1 max-w-sm" />
          <Select value={type} onChange={setType} options={TYPE_OPTIONS} placeholder="Type" className="w-full sm:w-40" />
        </div>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={data?.content || []} isLoading={isLoading} rowKey="key" emptyMessage="No configuration entries" />
        
        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onChange={setPage}
          />
        )}
      </div>
      
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingConfig ? 'Edit Configuration' : 'Add Configuration'}>
        <Form onSubmit={(data: any) => {
          if (editingConfig) {
            updateMutation.mutate({ key: editingConfig.key, value: data.value, description: data.description });
          } else {
            createMutation.mutate({ key: data.key, value: data.value, description: data.description });
          }
          setDialogOpen(false);
          setEditingConfig(null);
        }} initialValues={editingConfig || {}}>
          <FormField name="key" label="Key" placeholder="config.key" required />
          <FormField name="value" label="Value" placeholder="value" required />
          <FormField name="type" label="Type" type="select" options={TYPE_OPTIONS.filter(o => o.value)} required />
          <FormField name="description" label="Description" type="textarea" placeholder="Description" />
        </Form>
      </Dialog>
    </div>
  );
}

export default ConfigPage;