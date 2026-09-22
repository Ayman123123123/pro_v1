import { useState } from 'react';
import { Server, Trash2, RefreshCw, Database, Zap, Trash, AlertTriangle, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn, formatBytes } from '@/utils';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { Dialog } from '@/components/ui/dialog';
import { Tabs } from '@/components/ui/tabs';

const TABS = [
  { value: 'keys', label: 'Keys Browser' },
  { value: 'memory', label: 'Memory Analysis' },
  { value: 'ttl', label: 'TTL Management' },
];

const mockKeys = Array.from({ length: 50 }, (_, i) => ({
  key: `cache:${['user', 'session', 'channel', 'message', 'config'][i % 5]}:${i}`,
  type: ['STRING', 'HASH', 'LIST', 'SET', 'ZSET'][i % 5],
  size: Math.floor(Math.random() * 10000) + 100,
  ttl: i % 3 === 0 ? -1 : Math.floor(Math.random() * 86400) + 3600,
  memory: Math.floor(Math.random() * 100000) + 1000,
}));

const mockMemory = {
  used: 2.4 * 1024 * 1024 * 1024,
  peak: 3.1 * 1024 * 1024 * 1024,
  limit: 4 * 1024 * 1024 * 1024,
  fragmentation: 1.23,
  keys: 125432,
  expired: 4521,
  evicted: 123,
};

export function CachePage() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('keys');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [size] = useState(25);
  const [flushDialogOpen, setFlushDialogOpen] = useState(false);
  const [flushType, setFlushType] = useState('db');
  
  const keys = mockKeys.slice(page * size, (page + 1) * size);
  const totalPages = Math.ceil(mockKeys.length / size);
  
  const keyColumns = [
    {
      key: 'key',
      title: 'Key',
      cell: (k: any) => <span className="font-mono text-yn-text-secondary text-sm max-w-xs truncate block">{k.key}</span>,
    },
    {
      key: 'type',
      title: 'Type',
      cell: (k: any) => <Badge variant="blue">{k.type}</Badge>,
    },
    {
      key: 'size',
      title: 'Size',
      cell: (k: any) => <span className="text-yn-text-secondary">{k.size} bytes</span>,
    },
    {
      key: 'ttl',
      title: 'TTL',
      cell: (k: any) => <span className="text-yn-text-secondary">{k.ttl === -1 ? 'No expiry' : `${Math.floor(k.ttl / 3600)}h ${Math.floor((k.ttl % 3600) / 60)}m`}</span>,
    },
    {
      key: 'memory',
      title: 'Memory',
      cell: (k: any) => <span className="text-yn-text-secondary">{formatBytes(k.memory)}</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
      cell: (k: any) => (
        <Button variant="ghost" size="sm" className="text-yn-error hover:text-yn-error">
          <Trash2 className="w-4 h-4" />
        </Button>
      ),
    },
  ];
  
  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('system.cache.title')}</h1>
          <p className="text-yn-text-secondary">{t('system.cache.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="secondary" size="sm" onClick={() => setFlushDialogOpen(true)}>
            <Trash className="w-4 h-4 mr-2" />
            Flush Cache
          </Button>
          <Button variant="secondary" size="sm">
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh
          </Button>
        </div>
      </div>
      
      {/* Memory Overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label="Used Memory" value={formatBytes(mockMemory.used)} icon={Server} color="blue" />
        <StatCard label="Peak Memory" value={formatBytes(mockMemory.peak)} icon={Zap} color="gold" />
        <StatCard label="Memory Limit" value={formatBytes(mockMemory.limit)} icon={Server} color="blue" />
        <StatCard label="Fragmentation" value={`${mockMemory.fragmentation}x`} icon={AlertTriangle} color="gold" />
      </div>
      
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard label="Total Keys" value={mockMemory.keys.toLocaleString()} icon={Database} color="green" />
        <StatCard label="Expired Keys" value={mockMemory.expired.toLocaleString()} icon={AlertTriangle} color="gold" />
        <StatCard label="Evicted Keys" value={mockMemory.evicted.toLocaleString()} icon={Trash2} color="danger" />
      </div>
      
      {/* Tabs */}
      <Tabs value={activeTab} onChange={setActiveTab} tabs={TABS} />
      
      {activeTab === 'keys' && (
        <div className="yn-card yn-card-liquid yn-glass">
          <div className="p-4 border-b border-yn-border">
            <SearchInput value={search} onChange={setSearch} placeholder="Search keys..." className="max-w-sm" />
          </div>
          <DataTable columns={keyColumns} data={keys} rowKey="key" emptyMessage="No keys found" />
          
          <div className="p-4 border-t border-yn-border flex items-center justify-between">
            <span className="text-sm text-yn-text-secondary">
              Showing {page * size + 1}–{Math.min((page + 1) * size, mockKeys.length)} of {mockKeys.length}
            </span>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page === totalPages - 1}>
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </div>
      )}
      
      {activeTab === 'memory' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <Zap className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">Memory Analysis</h3>
            <p>Detailed memory analysis charts coming soon</p>
            <div className="mt-6 grid grid-cols-3 gap-4 text-center">
              <div className="p-4 bg-yn-navy rounded-lg">
                <p className="text-2xl font-bold text-yn-green">60%</p>
                <p className="text-sm text-yn-text-muted">Memory Usage</p>
              </div>
              <div className="p-4 bg-yn-navy rounded-lg">
                <p className="text-2xl font-bold text-yn-gold">1.23x</p>
                <p className="text-sm text-yn-text-muted">Fragmentation</p>
              </div>
              <div className="p-4 bg-yn-navy rounded-lg">
                <p className="text-2xl font-bold text-yn-blue">{mockMemory.keys.toLocaleString()}</p>
                <p className="text-sm text-yn-text-muted">Total Keys</p>
              </div>
            </div>
          </div>
        </div>
      )}
      
      {activeTab === 'ttl' && (
        <div className="yn-card yn-card-liquid yn-glass p-6">
          <div className="text-center py-12 text-yn-text-muted">
            <AlertTriangle className="w-12 h-12 mx-auto mb-4 text-yn-text-muted/50" />
            <h3 className="font-medium text-yn-text mb-2">TTL Management</h3>
            <p>TTL policies and expiration management coming soon</p>
          </div>
        </div>
      )}
      
      <Dialog open={flushDialogOpen} onClose={() => setFlushDialogOpen(false)} title="Flush Cache">
        <p className="text-sm text-yn-text-secondary mb-4">This will permanently delete data from Redis. This action cannot be undone.</p>
        <div className="space-y-4">
          <div>
            <label className="yn-label">Flush Type</label>
            <select
              value={flushType}
              onChange={(e) => setFlushType(e.target.value)}
              className="yn-select"
            >
              <option value="db">Current Database (FLUSHDB)</option>
              <option value="all">All Databases (FLUSHALL)</option>
            </select>
          </div>
          <div className="flex items-center gap-2">
            <input type="checkbox" id="confirm" className="w-4 h-4 rounded border-yn-border bg-yn-navy text-yn-green focus:ring-yn-green" />
            <label htmlFor="confirm" className="text-sm text-yn-text-secondary">
              I understand this will permanently delete data
            </label>
          </div>
        </div>
      </Dialog>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: string; icon: React.ComponentType<{ className?: string }>; color: string }) {
  return (
    <div className="yn-card yn-card-liquid yn-glass p-4">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-yn-text-secondary">{label}</p>
          <p className="text-xl font-bold text-yn-text mt-1">{value}</p>
        </div>
        <div className={cn('w-10 h-10 rounded-lg flex items-center justify-center', `bg-yn-${color}/15`)} >
          <Icon className={cn('w-5 h-5', `text-yn-${color}`)} aria-hidden="true" />
        </div>
      </div>
    </div>
  );
}

export default CachePage;