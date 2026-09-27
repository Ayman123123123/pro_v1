import { useState } from 'react';
import { Plus, Edit, Trash2, MoreVertical, Key } from 'lucide-react';
import { useRoles, mutations } from '@/api/queries';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { DataTable } from '@/components/ui/data-table';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu } from '@/components/ui/simple-dropdown-menu';
import { Dialog } from '@/components/ui/dialog';
import { Form, FormField } from '@/components/ui/form';

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
  
  const { data: roles, isLoading, error } = useRoles();
  const createMutation = mutations.useCreateRole();
  const updateMutation = mutations.useUpdateRole();
  const deleteMutation = mutations.useDeleteRole();
  
  const columns = [
    {
      key: 'name',
      title: 'Role Name',
      cell: (role: any) => (
        <div>
          <p className="font-medium text-yn-text">{role.name}</p>
          <p className="text-xs text-yn-text-muted">{role.id}</p>
        </div>
      ),
    },
    {
      key: 'description',
      title: 'Description',
      cell: (role: any) => <span className="text-yn-text-secondary max-w-xs truncate block">{role.description || '—'}</span>,
    },
    {
      key: 'permissions',
      title: 'Permissions',
      cell: (role: any) => (
        <Badge variant={role.permissions.includes('*') ? 'danger' : 'blue'}>
          {role.permissions.length} permissions
        </Badge>
      ),
    },
    {
      key: 'isSystem',
      title: 'Type',
      cell: (role: any) => <Badge variant={role.isSystem ? 'gold' : 'green'}>{role.isSystem ? 'System' : 'Custom'}</Badge>,
    },
    {
      key: 'createdAt',
      title: 'Created',
      cell: (role: any) => <span className="text-yn-text-secondary">{new Date(role.createdAt).toLocaleDateString()}</span>,
    },
    {
      key: 'actions',
      title: 'Actions',
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
              <DropdownMenu.Item onClick={() => deleteMutation.mutate(role.id)}>
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

  const list = Array.isArray(roles) ? roles : [];
  const displayRoles = list;
  const errorText = String((error as any)?.message ?? '');
  
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
      {errorText.length > 0 && (
        <div className="yn-card yn-glass p-4 text-sm text-yn-error border border-yn-error/30">
          تعذر جلب الأدوار من الخادم عبر <span className="font-mono">GET /api/admin/security/roles</span>: {errorText}
        </div>
      )}

      <div className="yn-card yn-card-liquid yn-glass overflow-hidden">
        <DataTable columns={columns} data={displayRoles} isLoading={isLoading} rowKey="id" emptyMessage="No roles found" />
      </div>
      
      <Dialog open={dialogOpen} onClose={() => { setDialogOpen(false); setEditingRole(null); }} title={editingRole ? 'Edit Role' : 'Create Role'}>
        <Form onSubmit={(data: any) => {
          if (editingRole) {
            updateMutation.mutate({ id: editingRole.id, data });
          } else {
            createMutation.mutate(data);
          }
          setDialogOpen(false);
          setEditingRole(null);
        }} initialValues={editingRole || {}}>
          <FormField name="name" label="Role Name" placeholder="Content Manager" required />
          <FormField name="description" label="Description" type="textarea" placeholder="Role description" />
        </Form>
      </Dialog>
      
      <Dialog open={permissionDialogOpen} onClose={() => { setPermissionDialogOpen(false); setPermissionRole(null); }} title="Manage Permissions" size="xl">
        {permissionRole && (
          <div className="space-y-4 max-h-[60vh] overflow-y-auto">
            <p className="text-sm text-yn-text-secondary">Role: <span className="font-medium text-yn-text">{permissionRole.name}</span>{permissionRole.isSystem && <span className="text-xs text-yn-gold"> (نظامي — للعرض فقط، الخادم يرفض التعديل)</span>}</p>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-2 max-h-[400px] overflow-y-auto">
              {ALL_PERMISSIONS.map((perm) => (
                <label key={perm} className="flex items-center gap-2 p-2 bg-yn-navy rounded-lg hover:bg-yn-surface transition-colors cursor-pointer">
                  <input
                    type="checkbox"
                    defaultChecked={permissionRole.permissions.includes('*') || permissionRole.permissions.includes(perm)}
                    onChange={(e) => {
                      // تحديث محلي فوري + حفظ فعلي عبر PUT /security/roles/{id} (الخادم يرفض الأدوار النظامية)
                      const el = e.target as HTMLInputElement;
                      const current = new Set<string>(permissionRole.permissions);
                      if (el.checked) current.add(perm); else current.delete(perm);
                      setPermissionRole((r: any) => ({ ...r, permissions: Array.from(current) }));
                      if (!permissionRole.isSystem) {
                        updateMutation.mutate({ id: permissionRole.id, data: { permissions: Array.from(current) } });
                      }
                    }}
                    disabled={permissionRole.isSystem}
                    className="w-4 h-4 rounded border-yn-border bg-yn-navy text-yn-green focus:ring-yn-green"
                  />
                  <span className="text-yn-text font-mono text-xs">{perm}</span>
                </label>
              ))}
            </div>
            <p className="text-xs text-yn-text-muted">يُحفظ كل تغيير فوراً على الخادم ويُسجّل في سجل التدقيق.</p>
          </div>
        )}
      </Dialog>
    </div>
  );
}

export default RolesPage;