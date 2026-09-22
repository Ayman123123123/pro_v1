'use client';

import { useState, useCallback } from 'react';
import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  SortingState,
  ColumnFiltersState,
  VisibilityState,
  PaginationState,
} from '@tanstack/react-table';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/utils/cn';
import { useTranslation } from 'react-i18next';
import { toast } from '@/hooks/useToast';
import { useRef } from 'react';
import {
  Search,
  Filter,
  Download,
  MoreVertical,
  CheckCircle,
  XCircle,
  Shield,
  UserX,
  UserCheck,
  Mail,
  Phone,
  Calendar,
  Eye,
  Edit,
  Trash2,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  Loader2,
} from 'lucide-react';
import { format } from 'date-fns';

interface User {
  id: string;
  username: string;
  displayName: string;
  email: string;
  phone: string;
  role: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'BANNED';
  avatarUrl?: string;
  createdAt: string;
  lastLogin: string;
  redId?: string;
  devices?: number;
  sessions?: number;
}

interface UsersResponse {
  content: User[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const STATUS_COLORS: Record<User['status'], 'default' | 'success' | 'destructive' | 'warning' | 'info'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'destructive',
  SUSPENDED: 'warning',
  BANNED: 'destructive',
};

const STATUS_LABELS: Record<User['status'], string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
  BANNED: 'Banned',
};

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super Admin',
  ADMIN: 'Admin',
  MODERATOR: 'Moderator',
  SUPPORT: 'Support',
  VIEWER: 'Viewer',
  USER: 'User',
};

// ✅ FIX 2026-09-22: columns كانت ثابت module-level يسترعي handle* (معرّفة داخل
// UsersPage) → "Cannot find name". الآن دالة تستقبل الـ handlers من المكوّن.
interface UserActions {
  handleView: (u: User) => void;
  handleEdit: (u: User) => void;
  handleApprove: (u: User) => void;
  handleReject: (u: User) => void;
  handleBan: (u: User) => void;
  handleUnban: (u: User) => void;
  handleImpersonate: (u: User) => void;
  handleDelete: (u: User) => void;
}

function buildColumns(a: UserActions): ColumnDef<User>[] {
  const { handleView, handleEdit, handleApprove, handleReject, handleBan, handleUnban, handleImpersonate, handleDelete } = a;
  return [
  {
    id: 'select',
    header: ({ table }) => (
      <Checkbox
        checked={table.getIsAllPageRowsSelected()}
        indeterminate={table.getIsSomePageRowsSelected() && !table.getIsAllPageRowsSelected()}
        onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
        aria-label="Select all"
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        checked={row.getIsSelected()}
        onCheckedChange={(value) => row.toggleSelected(!!value)}
        aria-label="Select row"
      />
    ),
    enableSorting: false,
    enableHiding: false,
    size: 40,
  },
  {
    accessorKey: 'username',
    header: 'Username',
    cell: ({ row }) => {
      const user = row.original;
      return (
        <div className="flex items-center gap-3">
          <Avatar className="h-8 w-8">
            <AvatarImage src={user.avatarUrl} alt={user.displayName} />
            <AvatarFallback className="text-xs">
              {user.displayName?.charAt(0).toUpperCase() || user.username.charAt(0).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div>
            <p className="font-medium">{user.username}</p>
            <p className="text-xs text-muted-foreground">@{user.username}</p>
          </div>
        </div>
      );
    },
    size: 200,
  },
  {
    accessorKey: 'displayName',
    header: 'Display Name',
    cell: ({ row }) => row.original.displayName,
    size: 150,
  },
  {
    accessorKey: 'email',
    header: 'Email',
    cell: ({ row }) => (
      <a href={`mailto:${row.original.email}`} className="text-primary hover:underline">
        {row.original.email}
      </a>
    ),
    size: 200,
  },
  {
    accessorKey: 'phone',
    header: 'Phone',
    cell: ({ row }) => row.original.phone || '—',
    size: 130,
  },
  {
    accessorKey: 'role',
    header: 'Role',
    cell: ({ row }) => (
      <Badge variant="outline">{ROLE_LABELS[row.original.role] || row.original.role}</Badge>
    ),
    size: 120,
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => (
      <Badge variant={STATUS_COLORS[row.original.status]}>
        {STATUS_LABELS[row.original.status]}
      </Badge>
    ),
    size: 100,
  },
  {
    accessorKey: 'createdAt',
    header: 'Created',
    cell: ({ row }) => format(new Date(row.original.createdAt), 'MMM d, yyyy'),
    size: 120,
  },
  {
    accessorKey: 'lastLogin',
    header: 'Last Login',
    cell: ({ row }) => row.original.lastLogin ? format(new Date(row.original.lastLogin), 'MMM d, yyyy HH:mm') : 'Never',
    size: 150,
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => (
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8">
            <MoreVertical className="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuLabel>Actions for {row.original.username}</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => handleView(row.original)}>
            <Eye className="h-4 w-4 mr-2" /> View Details
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleEdit(row.original)}>
            <Edit className="h-4 w-4 mr-2" /> Edit
          </DropdownMenuItem>
          {row.original.status === 'PENDING' && (
            <>
              <DropdownMenuItem onClick={() => handleApprove(row.original)} className="text-green-600">
                <CheckCircle className="h-4 w-4 mr-2" /> Approve
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleReject(row.original)} className="text-red-600">
                <XCircle className="h-4 w-4 mr-2" /> Reject
              </DropdownMenuItem>
            </>
          )}
          {row.original.status !== 'BANNED' && (
            <DropdownMenuItem onClick={() => handleBan(row.original)} className="text-orange-600">
              <UserX className="h-4 w-4 mr-2" /> Ban
            </DropdownMenuItem>
          )}
          {row.original.status === 'BANNED' && (
            <DropdownMenuItem onClick={() => handleUnban(row.original)} className="text-green-600">
              <UserCheck className="h-4 w-4 mr-2" /> Unban
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => handleImpersonate(row.original)} className="text-blue-600">
            <Shield className="h-4 w-4 mr-2" /> Impersonate
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => handleDelete(row.original)} className="text-red-600">
            <Trash2 className="h-4 w-4 mr-2" /> Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    ),
    enableSorting: false,
    size: 60,
  },
];
}

export function UsersPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [sorting, setSorting] = useState<SortingState>([{ id: 'createdAt', desc: true }]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 20 });
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  // Fetch users
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['users', pagination, sorting, columnFilters, globalFilter],
    queryFn: () => fetchUsers(pagination, sorting, columnFilters, globalFilter),
    placeholderData: (previousData) => previousData,
  });

  // Mutations
  const approveMutation = useMutation({
    mutationFn: (id: string) => approveUser(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User approved'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectUser(id, reason),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User rejected'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const banMutation = useMutation({
    mutationFn: ({ id, reason, durationDays }: { id: string; reason: string; durationDays?: number }) => banUser(id, reason, durationDays),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User banned'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const unbanMutation = useMutation({
    mutationFn: (id: string) => unbanUser(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User unbanned'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('User deleted'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const bulkActionMutation = useMutation({
    mutationFn: ({ action, ids, reason, durationDays }: { action: string; ids: string[]; reason?: string; durationDays?: number }) =>
      bulkAction(action, ids, reason, durationDays),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); toast.success('Bulk action completed'); },
    onError: (err: Error) => toast.error(err.message),
  });

  const handleView = (user: User) => {
    console.log('View user:', user);
  };

  const handleEdit = (user: User) => {
    console.log('Edit user:', user);
  };

  const handleApprove = (user: User) => {
    approveMutation.mutate(user.id);
  };

  const handleReject = (user: User) => {
    const reason = prompt('Reason for rejection:');
    if (reason) rejectMutation.mutate({ id: user.id, reason });
  };

  const handleBan = (user: User) => {
    const reason = prompt('Reason for ban:');
    const duration = prompt('Duration in days (optional):');
    if (reason) banMutation.mutate({ id: user.id, reason, durationDays: duration ? parseInt(duration) : undefined });
  };

  const handleUnban = (user: User) => {
    if (confirm(`Unban ${user.username}?`)) unbanMutation.mutate(user.id);
  };

  const handleImpersonate = (user: User) => {
    if (confirm(`Impersonate ${user.username}? This will be logged.`)) {
      console.log('Impersonate:', user);
      toast.info('Impersonation started', 'Activity is being logged');
    }
  };

  const handleDelete = (user: User) => {
    if (confirm(`Delete ${user.username}? This action cannot be undone.`)) deleteMutation.mutate(user.id);
  };

  const handleBulkAction = (action: string) => {
    const selectedIds = Array.from(selectedRows);
    if (selectedIds.length === 0) return;

    let reason: string | undefined;
    let durationDays: number | undefined;

    if (action === 'reject') {
      reason = prompt('Reason for rejection:') ?? undefined;
      if (!reason) return;
    } else if (action === 'ban') {
      reason = prompt('Reason for ban:') ?? undefined;
      if (!reason) return;
      const duration = prompt('Duration in days (optional):');
      if (duration) durationDays = parseInt(duration);
    } else if (action === 'delete') {
      if (!confirm(`Delete ${selectedIds.length} users? This cannot be undone.`)) return;
    }

    bulkActionMutation.mutate({ action, ids: selectedIds, reason, durationDays });
  };

  const columns = buildColumns({ handleView, handleEdit, handleApprove, handleReject, handleBan, handleUnban, handleImpersonate, handleDelete });

  const table = useReactTable({
    data: data?.content || [],
    columns,
    state: {
      sorting,
      columnFilters,
      globalFilter,
      pagination,
      columnVisibility,
      rowSelection,
    },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onGlobalFilterChange: setGlobalFilter,
    onPaginationChange: setPagination,
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    manualPagination: true,
    pageCount: data?.totalPages || 1,
    initialState: {
      pagination: { pageIndex: 0, pageSize: 20 },
    },
  });

  // Virtualizer for large datasets
  const parentRef = useRef<HTMLTableSectionElement>(null);
  const virtualizer = useVirtualizer({
    count: table.getRowModel().rows.length,
    getScrollElement: () => parentRef.current?.parentElement ?? null,
    estimateSize: () => 56,
    overscan: 5,
  });

  const handleExport = async (format: 'csv' | 'excel' | 'pdf') => {
    toast.info(`Exporting as ${format.toUpperCase()}...`);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('users.title')}</h1>
          <p className="text-muted-foreground">{data?.totalElements || 0} {t('users.totalUsers')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => handleExport('csv')}>
            <Download className="h-4 w-4 mr-2" /> CSV
          </Button>
          <Button variant="outline" onClick={() => handleExport('excel')}>
            <Download className="h-4 w-4 mr-2" /> Excel
          </Button>
          <Button variant="outline" onClick={() => handleExport('pdf')}>
            <Download className="h-4 w-4 mr-2" /> PDF
          </Button>
        </div>
      </div>

      {/* Toolbar */}
      <Card>
        <CardContent className="p-4">
          <div className="flex flex-col sm:flex-row gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t('users.searchPlaceholder')}
                value={globalFilter}
                onChange={(e) => setGlobalFilter(e.target.value)}
                className="pl-10"
              />
            </div>
            <Select value={columnFilters.find(f => f.id === 'status')?.value as string || ''} onValueChange={(v) => setColumnFilters(prev => [...prev.filter(f => f.id !== 'status'), { id: 'status', value: v }])}>
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder={t('users.filters.status')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">{t('common.all')}</SelectItem>
                <SelectItem value="PENDING">Pending</SelectItem>
                <SelectItem value="APPROVED">Approved</SelectItem>
                <SelectItem value="REJECTED">Rejected</SelectItem>
                <SelectItem value="SUSPENDED">Suspended</SelectItem>
                <SelectItem value="BANNED">Banned</SelectItem>
              </SelectContent>
            </Select>
            <Select value={columnFilters.find(f => f.id === 'role')?.value as string || ''} onValueChange={(v) => setColumnFilters(prev => [...prev.filter(f => f.id !== 'role'), { id: 'role', value: v }])}>
              <SelectTrigger className="w-[150px]">
                <SelectValue placeholder={t('users.filters.role')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">{t('common.all')}</SelectItem>
                <SelectItem value="SUPER_ADMIN">Super Admin</SelectItem>
                <SelectItem value="ADMIN">Admin</SelectItem>
                <SelectItem value="MODERATOR">Moderator</SelectItem>
                <SelectItem value="SUPPORT">Support</SelectItem>
                <SelectItem value="VIEWER">Viewer</SelectItem>
              </SelectContent>
            </Select>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">
                  <Filter className="h-4 w-4 mr-2" /> Columns
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {table.getAllColumns().map((column) => (
                  <DropdownMenuItem key={column.id} className="flex items-center gap-2">
                    <Checkbox
                      checked={column.getIsVisible()}
                      onCheckedChange={(value) => column.toggleVisibility(!!value)}
                    />
                    {column.id}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </CardContent>
      </Card>

      {/* Bulk Actions Bar */}
      {selectedRows.size > 0 && (
        <Card className="border-yellow-500 bg-yellow-50">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <span className="font-medium">{selectedRows.size} {t('users.selected')}</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('approve')}>
                  <CheckCircle className="h-4 w-4 mr-2" /> Approve
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('reject')}>
                  <XCircle className="h-4 w-4 mr-2" /> Reject
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('ban')}>
                  <UserX className="h-4 w-4 mr-2" /> Ban
                </Button>
                <Button variant="outline" size="sm" onClick={() => handleBulkAction('unban')}>
                  <UserCheck className="h-4 w-4 mr-2" /> Unban
                </Button>
                <Button variant="destructive" size="sm" onClick={() => handleBulkAction('delete')}>
                  <Trash2 className="h-4 w-4 mr-2" /> Delete
                </Button>
                <Button variant="ghost" size="sm" onClick={() => { setSelectedRows(new Set()); table.resetRowSelection(); }}>
                  Clear
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-6">
              <div className="space-y-4">
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="flex items-center gap-4 p-4">
                    <Skeleton className="h-8 w-8 rounded-full" />
                    <div className="flex-1 space-y-2">
                      <Skeleton className="h-4 w-32" />
                      <Skeleton className="h-3 w-48" />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : error ? (
            <div className="p-6 text-center text-red-500">
              Error loading users: {error.message}
              <Button variant="outline" className="ml-4" onClick={() => refetch()}>Retry</Button>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full" role="grid">
                  <thead className="bg-muted/50">
                    {table.getHeaderGroups().map((headerGroup) => (
                      <tr key={headerGroup.id}>
                        {headerGroup.headers.map((header) => (
                          <th
                            key={header.id}
                            className={cn(
                              'h-12 px-4 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider',
                              header.column.getCanSort() && 'cursor-pointer select-none hover:bg-muted',
                              !header.column.getCanSort() && 'cursor-default'
                            )}
                            onClick={header.column.getToggleSortingHandler()}
                            style={{
                              width: header.column.getSize(),
                              minWidth: header.column.getSize(),
                            }}
                          >
                            <div className="flex items-center gap-2">
                              {flexRender(header.column.columnDef.header, header.getContext())}
                              {header.column.getIsSorted() && (
                                <ArrowUpDown className="h-4 w-4" />
                              )}
                            </div>
                          </th>
                        ))}
                      </tr>
                    ))}
                  </thead>
                  <tbody
                    ref={parentRef}
                    className="h-[calc(100vh-400px)] max-h-[600px] overflow-y-auto"
                  >
                    <div style={{ height: virtualizer.getTotalSize() }}>
                      {virtualizer.getVirtualItems().map((virtualRow) => (
                        <tr
                          key={virtualRow.key}
                          style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            width: '100%',
                            height: `${virtualRow.size}px`,
                            transform: `translateY(${virtualRow.start}px)`,
                          }}
                          className={cn(
                            'border-b transition-colors',
                            virtualRow.index % 2 === 0 ? 'bg-background' : 'bg-muted/30',
                            'hover:bg-accent/50'
                          )}
                        >
                          {table.getRowModel().rows[virtualRow.index]?.getVisibleCells().map((cell) => (
                            <td
                              key={cell.id}
                              className="px-4 py-3 align-middle"
                              style={{ width: cell.column.getSize() }}
                            >
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </div>
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              <div className="flex items-center justify-between p-4 border-t">
                <div className="text-sm text-muted-foreground">
                  Page {table.getState().pagination.pageIndex + 1} of {table.getPageCount()}
                  {' '}({data?.totalElements || 0} total)
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.previousPage()}
                    disabled={!table.getCanPreviousPage()}
                  >
                    <ChevronsLeft className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.previousPage()}
                    disabled={!table.getCanPreviousPage()}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.nextPage()}
                    disabled={!table.getCanNextPage()}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => table.nextPage()}
                    disabled={!table.getCanNextPage()}
                  >
                    <ChevronsRight className="h-4 w-4" />
                  </Button>
                  <Select
                    value={String(table.getState().pagination.pageSize)}
                    onValueChange={(v) => table.setPageSize(Number(v))}
                  >
                    <SelectTrigger className="w-[100px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {[10, 20, 50, 100].map((size) => (
                        <SelectItem key={size} value={String(size)}>
                          {size} per page
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// API Functions
async function fetchUsers(pagination: PaginationState, sorting: SortingState, filters: ColumnFiltersState, globalFilter: string): Promise<UsersResponse> {
  const params = new URLSearchParams({
    page: String(pagination.pageIndex),
    size: String(pagination.pageSize),
    ...(sorting[0] && { sortBy: sorting[0].id, sortDir: sorting[0].desc ? 'desc' : 'asc' }),
    ...(globalFilter && { search: globalFilter }),
    ...Object.fromEntries(filters.map(f => [f.id, String(f.value)])),
  });

  const response = await fetch(`/api/admin/users?${params}`);
  if (!response.ok) throw new Error('Failed to fetch users');
  return response.json();
}

async function approveUser(id: string) {
  const response = await fetch(`/api/admin/users/${id}/approve`, { method: 'POST' });
  if (!response.ok) throw new Error('Failed to approve user');
}

async function rejectUser(id: string, reason: string) {
  const response = await fetch(`/api/admin/users/${id}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  });
  if (!response.ok) throw new Error('Failed to reject user');
}

async function banUser(id: string, reason: string, durationDays?: number) {
  const response = await fetch(`/api/admin/users/${id}/ban`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason, durationDays }),
  });
  if (!response.ok) throw new Error('Failed to ban user');
}

async function unbanUser(id: string) {
  const response = await fetch(`/api/admin/users/${id}/unban`, { method: 'POST' });
  if (!response.ok) throw new Error('Failed to unban user');
}

async function deleteUser(id: string) {
  const response = await fetch(`/api/admin/users/${id}`, { method: 'DELETE' });
  if (!response.ok) throw new Error('Failed to delete user');
}

async function bulkAction(action: string, ids: string[], reason?: string, durationDays?: number) {
  const response = await fetch('/api/admin/users/bulk', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, ids, reason, durationDays }),
  });
  if (!response.ok) throw new Error('Bulk action failed');
}