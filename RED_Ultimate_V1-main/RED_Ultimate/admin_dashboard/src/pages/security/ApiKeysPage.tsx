import { useState } from 'react';
import { Key, Plus, Trash2, Copy, Eye, MoreVertical, AlertTriangle, CheckCircle, XCircle, RotateCcw } from 'lucide-react';
import { useApiKeys, mutations } from '@/api/queries';
import { cn, formatRelativeTime } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Select } from '@/components/ui/select';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu } from '@/components/ui/simple-dropdown-menu';
import { Dialog } from '@/components/ui/dialog';
import { Form, FormField } from '@/components/ui/form';

const mockApiKeys = Array.from({ length: 20 }, (_, i) => ({
  id: `key-${i}`,
  name: `API Key ${i + 1}`,
  keyPrefix: `rd_${Math.random().toString(36).substring(2, 10)}`,
  fullKey: i === 0 ? `rd_${Math.random().toString(36).substring(2, 10)}${Math.random().toString(36).substring(2, 20)}` : undefined,
  scopes: ['users:read', 'content:read', 'analytics:read'].slice(0, (i % 3) + 1),
  expiresAt: i % 4 === 0 ? undefined : new Date(Date.now() + (30 + i) * 86400000).toISOString(),
  lastUsedAt: i % 3 === 0 ? new Date(Date.now() - i * 86400000).toISOString() : undefined,
  createdAt: new Date(Date.now() - i * 86400000).toISOString(),
  createdBy: 'admin',
  isActive: i % 5 !== 0,
  usageCount: Math.floor(Math.random() * 10000),
}));

const ALL_SCOPES = [
  'users:read', 'users:write', 'users:delete',
  'content:read', 'content:write', 'content:delete', 'content:moderate',
  'channels:read', 'channels:write', 'channels:delete',
  'analytics:read', 'reports:read', 'reports:create',
  'system:config', 'system:feature-flags', 'system:backups',
  'security:roles', 'security:sessions', 'security:api-keys',
  'admin:*',
];

export function ApiKeysPage() {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingKey, setEditingKey] = useState<any>(null);
  const [showKeyDialogOpen, setShowKeyDialogOpen] = useState(false);
  const [showingKey, setShowingKey] = useState<any>(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  
  const { data: keys, isLoading } = useApiKeys();
  const createMutation = mutations.useCreateApiKey();
  const revokeMutation = mutations.useRevokeApiKey();
  
  // البيانات الحقيقية من الخادم أولاً، والوهمية احتياط فقط عند غيابها
  const displayKeys = Array.isArray(keys) && keys.length > 0 ? keys : mockApiKeys;
  
  const columns = [
    {
      key: 'name',
      title: 'Name',
      cell: (key: any) => (
        <div>
          <p className="font-medium text-yn-text">{key.name}</p>
          <p className="text-xs text-yn-text-muted font-mono">{key.keyPrefix}...</p>
        </div>
      ),
    },
    {
      key: 'scopes',
      title: 'Scopes',
      cell: (key: any) => (
        <div className="flex flex-wrap gap-1">
          {key.scopes.slice(0, 3).map((scope: string) => (
            <Badge key={scope} variant="blue" className="text-xs">{scope}</Badge>
          ))}
          {key.scopes.length > 3 && (
            <Badge variant="default" className="text-xs">+{key.scopes.length - 3} more</Badge>
          )}
        </div>
      ),
    },
    {
      key: 'status',
      title: 'Status',
      cell: (key: any) => (
        <Badge variant={key.isActive ? 'green' : 'danger'}>
          {key.isActive ? 'Active' : 'Revoked'}
        </Badge>
      ),
    },
    {
      key: 'expiresAt',
      title: 'Expires',
      cell: (key: any) => key.expiresAt ? (
        <span className={cn('text-yn-text-secondary', new Date(key.expiresAt) < new Date() && 'text-yn-error')}>
          {formatRelativeTime(key.expiresAt)}
        </span>
      ) : <Badge variant="gold">Never</Badge>,
    },
    {
      key: 'lastUsedAt',
      title: 'Last Used',
      cell: (key: any) => key.lastUsedAt ? <span className="text-yn-text-secondary">{formatRelativeTime(key.lastUsedAt)}</span> : <span className="text-yn-text-muted">Never</span>,
    },
    {
      key: 'usageCount',
      title: 'Usage',
      cell: (key: any) => <span className="text-yn-text-secondary font-mono">{key.usageCount.toLocaleString()}</span>,
    },
    {
      key: 'createdAt',
      title: 'Created',
      cell: (key: any) => <span className="text-yn-text-secondary">{formatRelativeTime(key.createdAt)}</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: (key: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            {key.fullKey && (
              <DropdownMenu.Item onClick={() => { setShowingKey(key); setShowKeyDialogOpen(true); }}>
                <Eye className="w-4 h-4 mr-2" />
                Show Full Key
              </DropdownMenu.Item>
            )}
            <DropdownMenu.Item onClick={() => { /* copy prefix */ }}>
              <Copy className="w-4 h-4 mr-2" />
              Copy Prefix
            </DropdownMenu.Item>
            <DropdownMenu.Item onClick={() => { /* regenerate */ }}>
              <RotateCcw className="w-4 h-4 mr-2" />
              Rotate Key
            </DropdownMenu.Item>
            <DropdownMenu.Item onClick={() => revokeMutation.mutate(key.id)}>
              {key.isActive ? (
                <>
                  <XCircle className="w-4 h-4 mr-2" />
                  Revoke
                </>
              ) : (
                <>
                  <CheckCircle className="w-4 h-4 mr-2" />
                  Restore
                </>
              )}
            </DropdownMenu.Item>
            <DropdownMenu.Item>
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
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.apiKeys.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.apiKeys.subtitle')}</p>
        </div>
        <Button variant="primary" size="sm" onClick={() => { setEditingKey(null); setDialogOpen(true); }}>
          <Plus className="w-4 h-4 mr-2" />
          Create API Key
        </Button>
      </div>
      
      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Total Keys" value={displayKeys.length} icon={Key} color="blue" />
        <StatCard label="Active" value={displayKeys.filter(k => k.isActive).length} icon={CheckCircle} color="green" />
        <StatCard label="Revoked" value={displayKeys.filter(k => !k.isActive).length} icon={XCircle} color="danger" />
        <StatCard label="Expiring Soon" value={displayKeys.filter(k => k.expiresAt && new Date(k.expiresAt) < new Date(Date.now() + 7 * 86400000)).length} icon={AlertTriangle} color="gold" />
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass">
        <div className="p-4 border-b border-yn-border">
          <div className="flex flex-col sm:flex-row gap-4">
            <SearchInput value={search} onChange={setSearch} placeholder="Search API keys..." className="flex-1 max-w-sm" />
            <Select value={status} onChange={setStatus} options={[
              { value: '', label: 'All Status' },
              { value: 'active', label: 'Active' },
              { value: 'revoked', label: 'Revoked' },
              { value: 'expired', label: 'Expired' },
            ]} placeholder="Status" className="w-full sm:w-40" />
          </div>
        </div>
        
        <DataTable columns={columns} data={displayKeys} isLoading={isLoading} rowKey="id" emptyMessage="No API keys found" />
      </div>
      
      <Dialog open={dialogOpen} onClose={() => { setDialogOpen(false); setEditingKey(null); }} title={editingKey ? 'Edit API Key' : 'Create API Key'} size="lg">
        <Form onSubmit={(data: any) => { createMutation.mutate(data); setDialogOpen(false); setEditingKey(null); }}>
          <FormField name="name" label="Key Name" placeholder="Production API Key" required />
          <FormField name="scopes" label="Scopes" type="textarea" placeholder="Enter scopes (one per line): users:read, content:read..." required />
          <FormField name="expiresAt" label="Expiration Date" type="text" />
          <div className="pt-4 border-t border-yn-border">
            <p className="text-sm text-yn-text-secondary mb-3">The full API key will only be shown once after creation. Make sure to copy and store it securely.</p>
          </div>
        </Form>
      </Dialog>
      
      <Dialog open={showKeyDialogOpen} onClose={() => { setShowKeyDialogOpen(false); setShowingKey(null); }} title="API Key">
        <p className="text-sm text-yn-text-secondary mb-4">This is the only time the full key will be displayed. Copy it now and store it securely.</p>
        {showingKey && (
          <div className="space-y-4">
            <div className="p-4 bg-yn-navy rounded-lg border border-yn-border font-mono text-sm break-all">
              {showingKey.fullKey}
            </div>
            <Button variant="primary" onClick={() => { navigator.clipboard.writeText(showingKey.fullKey); setShowKeyDialogOpen(false); setShowingKey(null); }} className="w-full">
              <Copy className="w-4 h-4 mr-2" />
              Copy to Clipboard
            </Button>
            <p className="text-sm text-yn-error text-center">This key will not be shown again.</p>
          </div>
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

export default ApiKeysPage;