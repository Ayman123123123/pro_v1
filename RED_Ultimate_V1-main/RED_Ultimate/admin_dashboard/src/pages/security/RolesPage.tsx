import { useState } from 'react';
import { Shield, Plus, Edit, Trash2, MoreVertical, Eye, Key, User, Lock, Users, Download } from 'lucide-react';
import { useRoles, mutations } from '@/api/queries';
import { cn } from '@/utils';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { SearchInput } from '@/components/ui/SearchInput';
import { DataTable } from '@/components/ui/DataTable';
import { Badge } from '@/components/ui/Badge';
import { DropdownMenu } from '@/components/ui/DropdownMenu';
import { Dialog } from '@/components/ui/Dialog';
import { Form } from '@/components/ui/Form';

const mockRoles = [
  { id: 'role-super-admin', name: 'Super Admin', description: 'Full system access', permissions: ['*'], isSystem: true, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'role-admin', name: 'Admin', description: 'Administrative access', permissions: ['users:*', 'content:*', 'system:*'], isSystem: true, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'role-moderator', name: 'Moderator', description: 'Content moderation', permissions: ['content:read', 'content:moderate', 'users:read'], isSystem: true, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'role-support', name: 'Support', description: 'Customer support access', permissions: ['users:read', 'tickets:*'], isSystem: true, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'role-viewer', name: 'Viewer', description: 'Read-only access', permissions: ['*:read'], isSystem: true, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 'role-custom-1', name: 'Content Manager', description: 'Manage content only', permissions: ['content:*', 'channels:read'], isSystem: false, createdAt: '2024-06-15T00:00:00Z', updatedAt: '2024-06-15T00:00:00Z' },
];

const ALL_PERMISSIONS = [
  'users:create', 'users:read', 'users:update', 'users:delete',
  'users:approve', 'users:ban', 'users:impersonate',
  'content:create', 'content:read', 'content:update', 'content:delete',
  'content:moderate', 'content:publish',
  'channels:create', 'channels:read', 'channels:update', 'channels:delete',
  'channels:moderate',
  'system:config', 'system:feature-flags', 'system:backups', 'system:audit',
  'security:roles', 'security:sso', 'security:mfa', 'security:sessions', 'security:api-keys',
  'analytics:read', 'reports:create', 'reports:read',
];

export function RolesPage() {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<any>(null);
  const [permissionDialogOpen, setPermissionDialogOpen] = useState(false);
  const [permissionRole, setPermissionRole] = useState<any>(null);
  
  const { data: roles, isLoading } = useRoles();
  const createMutation = mutations.useCreateRole();
  const updateMutation = mutations.useUpdateRole();
  const deleteMutation = mutations.useDeleteRole();
  
  const columns = [
    {
      key: 'name',
      header: 'Role Name',
      cell: (role: any) => (
        <div>
          <p className="font-medium text-yn-text">{role.name}</p>
          <p className="text-xs text-yn-text-muted">{role.id}</p>
        </div>
      ),
    },
    {
      key: 'description',
      header: 'Description',
      cell: (role: any) => <span className="text-yn-text-secondary max-w-xs truncate block">{role.description || '—'}</span>,
    },
    {
      key: 'permissions',
      header: 'Permissions',
      cell: (role: any) => (
        <Badge variant={role.permissions.includes('*') ? 'danger' : 'blue'}>
          {role.permissions.length} permissions
        </Badge>
      ),
    },
    {
      key: 'isSystem',
      header: 'Type',
      cell: (role: any) => <Badge variant={role.isSystem ? 'gold' : 'green'}>{role.isSystem ? 'System' : 'Custom'}</Badge>,
    },
    {
      key: 'createdAt',
      header: 'Created',
      cell: (role: any) => <span className="text-yn-text-secondary">{new Date(role.createdAt).toLocaleDateString()}</span>,
    },
    {
      key: 'actions',
      header: 'Actions',
      cell: (role: any) => (
        <DropdownMenu>
          <DropdownMenu.Trigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="w-4 h-4" />
            </Button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Content align="end">
            <DropdownMenu.Item onClick={() => { setEditingRole(role); setDialogOpen(true); }}>
              <Edit className="w-4 h-4 mr-2" />
              Edit
            </DropdownMenu.Item>
            <DropdownMenu.Item onClick={() => { setPermissionRole(role); setPermissionDialogOpen(true); }}>
              <Key className="w-4 h-4 mr-2" />
              Manage Permissions
            </DropdownMenu.Item>
            {!role.isSystem && (
              <DropdownMenu.Item className="text-yn-error" onClick={() => deleteMutation.mutate(role.id)}>
                <Trash2 className="w-4 h-4 mr-2" />
                Delete
              </DropdownMenu.Item>
            )}
          </DropdownMenu.Content>
        </DropdownMenu>
      ),
    },
  ];
  
  if (isLoading && !roles) {
    return <div className="yn-loading"><div className="yn-spinner" /></div>;
  }
  
  const displayRoles = roles || mockRoles;
  
  return (
    <div className="yn-page space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-bold text-yn-text">{t('security.rbac.title')}</h1>
          <p className="text-yn-text-secondary">{t('security.rbac.roles')}</p>
        </div>
        <Button variant="primary" size="sm" onClick={() => { setEditingRole(null); setDialogOpen(true); }}>
          <Plus className="w-4 h-4 mr-2" />
          Create Role
        </Button>
      </div>
      
      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={displayRoles} isLoading={isLoading} rowKey="id" emptyMessage="No roles found" />
      </div>
      
      <Dialog open={dialogOpen} onClose={() => { setDialogOpen(false); setEditingRole(null); }} title={editingRole ? 'Edit Role' : 'Create Role'}>
        <Form onSubmit={(data) => {
          if (editingRole) {
            updateMutation.mutate({ id: editingRole.id, data });
          } else {
            createMutation.mutate(data);
          }
          setDialogOpen(false);
          setEditingRole(null);
        }} initialValues={editingRole || {}}>
          <Form.Field name="name" label="Role Name" placeholder="Content Manager" required />
          <Form.Field name="description" label="Description" type="textarea" placeholder="Role description" />
        </Form>
      </Dialog>
      
      <Dialog open={permissionDialogOpen} onClose={() => { setPermissionDialogOpen(false); setPermissionRole(null); }} title="Manage Permissions" size="xl">
        {permissionRole && (
          <div className="space-y-4 max-h-[60vh] overflow-y-auto">
            <p className="text-sm text-yn-text-secondary">Role: <span className="font-medium text-yn-text">{permissionRole.name}</span></p>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2 max-h-[400px] overflow-y-auto">
              {ALL_PERMISSIONS.map((perm) => (
                <label key={perm} className="flex items-center gap-2 p-2 bg-yn-navy rounded-lg hover:bg-yn-surface transition-colors cursor-pointer">
                  <input
                    type="checkbox"
                    defaultChecked={permissionRole.permissions.includes('*') || permissionRole.permissions.includes(perm)}
                    className="w-4 h-4 rounded border-yn-border bg-yn-navy text-yn-green focus:ring-yn-green"
                  />
                  <span className="text-sm text-yn-text font-mono text-xs">{perm}</span>
                </label>
              ))}
            </div>
          </div>
        )}
      </Dialog>
    </div>
  );
}