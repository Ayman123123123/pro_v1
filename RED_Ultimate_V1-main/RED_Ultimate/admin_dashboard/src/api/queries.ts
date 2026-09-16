import { useQuery, useMutation, useQueryClient, QueryKey } from '@tanstack/react-query';
import { api } from './client';
import type {
  User,
  PageResponse,
  DashboardMetrics,
  SystemHealthComponent,
  Alert,
  ReportedContent,
  Channel,
  FeatureFlag,
  ConfigEntry,
  AuditLogEntry,
  Backup,
  Role,
  Permission,
  ApiKey,
  Session,
  Notification,
  RealtimeMetrics,
} from '@/types';

const queryKeys = {
  dashboard: {
    metrics: ['dashboard', 'metrics'] as QueryKey,
    health: ['dashboard', 'health'] as QueryKey,
    alerts: ['dashboard', 'alerts'] as QueryKey,
    realtime: ['dashboard', 'realtime'] as QueryKey,
  },
  users: {
    list: (params: Record<string, unknown>) => ['users', 'list', params] as QueryKey,
    detail: (id: string) => ['users', 'detail', id] as QueryKey,
    sessions: (id: string) => ['users', 'sessions', id] as QueryKey,
    devices: (id: string) => ['users', 'devices', id] as QueryKey,
    activity: (id: string) => ['users', 'activity', id] as QueryKey,
    security: (id: string) => ['users', 'security', id] as QueryKey,
  },
  content: {
    moderationQueue: (params: Record<string, unknown>) => ['content', 'moderation', params] as QueryKey,
    channels: (params: Record<string, unknown>) => ['content', 'channels', params] as QueryKey,
    channelDetail: (id: string) => ['content', 'channel', id] as QueryKey,
    reportedContent: (id: string) => ['content', 'reported', id] as QueryKey,
  },
  system: {
    featureFlags: ['system', 'featureFlags'] as QueryKey,
    config: (params: Record<string, unknown>) => ['system', 'config', params] as QueryKey,
    auditLog: (params: Record<string, unknown>) => ['system', 'auditLog', params] as QueryKey,
    backups: ['system', 'backups'] as QueryKey,
    migrations: ['system', 'migrations'] as QueryKey,
    cache: ['system', 'cache'] as QueryKey,
  },
  security: {
    roles: ['security', 'roles'] as QueryKey,
    apiKeys: ['security', 'apiKeys'] as QueryKey,
    sessions: ['security', 'sessions'] as QueryKey,
  },
  analytics: {
    reports: (params: Record<string, unknown>) => ['analytics', 'reports', params] as QueryKey,
    customReport: (id: string) => ['analytics', 'customReport', id] as QueryKey,
  },
} as const;

export function useDashboardMetrics() {
  return useQuery({
    queryKey: queryKeys.dashboard.metrics,
    queryFn: () => api.get<DashboardMetrics>('/dashboard/metrics'),
    refetchInterval: 30000,
    staleTime: 10000,
  });
}

export function useSystemHealth() {
  return useQuery({
    queryKey: queryKeys.dashboard.health,
    queryFn: () => api.get<SystemHealthComponent[]>('/dashboard/health'),
    refetchInterval: 60000,
    staleTime: 30000,
  });
}

export function useAlerts(params?: { severity?: string; acknowledged?: boolean }) {
  return useQuery({
    queryKey: [...queryKeys.dashboard.alerts, params],
    queryFn: () => api.get<Alert[]>('/dashboard/alerts', params),
    refetchInterval: 15000,
    staleTime: 5000,
  });
}

export function useRealtimeMetrics() {
  return useQuery({
    queryKey: queryKeys.dashboard.realtime,
    queryFn: () => api.get<RealtimeMetrics>('/dashboard/realtime'),
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

export function useUsers(params: {
  page?: number;
  size?: number;
  status?: string;
  role?: string;
  search?: string;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
} = {}) {
  return useQuery({
    queryKey: queryKeys.users.list(params),
    queryFn: () => api.get<PageResponse<User>>('/users', params),
    placeholderData: (previousData) => previousData,
  });
}

export function useUserDetail(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.detail(userId),
    queryFn: () => api.get<User>(`/users/${userId}`),
    enabled: !!userId,
  });
}

export function useUserSessions(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.sessions(userId),
    queryFn: () => api.get<Session[]>(`/users/${userId}/sessions`),
    enabled: !!userId,
  });
}

export function useUserDevices(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.devices(userId),
    queryFn: () => api.get<Device[]>(`/users/${userId}/devices`),
    enabled: !!userId,
  });
}

export function useUserActivity(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.activity(userId),
    queryFn: () => api.get<ActivityLogEntry[]>(`/users/${userId}/activity`),
    enabled: !!userId,
  });
}

export function useUserSecurityEvents(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.security(userId),
    queryFn: () => api.get<SecurityEvent[]>(`/users/${userId}/security-events`),
    enabled: !!userId,
  });
}

export function useModerationQueue(params: {
  page?: number;
  size?: number;
  status?: string;
  category?: string;
  priority?: string;
} = {}) {
  return useQuery({
    queryKey: queryKeys.content.moderationQueue(params),
    queryFn: () => api.get<PageResponse<ReportedContent>>('/content/moderation', params),
    refetchInterval: 10000,
  });
}

export function useReportedContent(contentId: string) {
  return useQuery({
    queryKey: queryKeys.content.reportedContent(contentId),
    queryFn: () => api.get<ReportedContent>(`/content/reported/${contentId}`),
    enabled: !!contentId,
  });
}

export function useChannels(params: {
  page?: number;
  size?: number;
  type?: string;
  search?: string;
} = {}) {
  return useQuery({
    queryKey: queryKeys.content.channels(params),
    queryFn: () => api.get<PageResponse<Channel>>('/content/channels', params),
  });
}

export function useChannelDetail(channelId: string) {
  return useQuery({
    queryKey: queryKeys.content.channelDetail(channelId),
    queryFn: () => api.get<Channel>(`/content/channels/${channelId}`),
    enabled: !!channelId,
  });
}

export function useFeatureFlags() {
  return useQuery({
    queryKey: queryKeys.system.featureFlags,
    queryFn: () => api.get<FeatureFlag[]>('/system/feature-flags'),
    staleTime: 60000,
  });
}

export function useConfig(params: { page?: number; size?: number; search?: string } = {}) {
  return useQuery({
    queryKey: queryKeys.system.config(params),
    queryFn: () => api.get<PageResponse<ConfigEntry>>('/system/config', params),
  });
}

export function useAuditLog(params: {
  page?: number;
  size?: number;
  adminId?: string;
  action?: string;
  category?: string;
  severity?: string;
  startDate?: string;
  endDate?: string;
} = {}) {
  return useQuery({
    queryKey: queryKeys.system.auditLog(params),
    queryFn: () => api.get<PageResponse<AuditLogEntry>>('/system/audit', params),
  });
}

export function useBackups() {
  return useQuery({
    queryKey: queryKeys.system.backups,
    queryFn: () => api.get<Backup[]>('/system/backups'),
  });
}

export function useRoles() {
  return useQuery({
    queryKey: queryKeys.security.roles,
    queryFn: () => api.get<Role[]>('/security/roles'),
    staleTime: 60000,
  });
}

export function useApiKeys() {
  return useQuery({
    queryKey: queryKeys.security.apiKeys,
    queryFn: () => api.get<ApiKey[]>('/security/api-keys'),
  });
}

export function useAllSessions() {
  return useQuery({
    queryKey: queryKeys.security.sessions,
    queryFn: () => api.get<Session[]>('/security/sessions'),
    refetchInterval: 30000,
  });
}

export function useNotifications(params?: { unreadOnly?: boolean }) {
  return useQuery({
    queryKey: ['notifications', params],
    queryFn: () => api.get<Notification[]>('/notifications', params),
    refetchInterval: 30000,
  });
}

export const mutations = {
  useApproveUser: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (userId: string) => api.post(`/users/${userId}/approve`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['users'] });
        queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.metrics });
      },
    });
  },

  useBanUser: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ userId, reason, durationDays }: { userId: string; reason: string; durationDays?: number }) =>
        api.post(`/users/${userId}/ban`, { reason, durationDays }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['users'] });
        queryClient.invalidateQueries({ queryKey: queryKeys.dashboard.metrics });
      },
    });
  },

  useUnbanUser: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (userId: string) => api.post(`/users/${userId}/unban`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['users'] });
      },
    });
  },

  useBulkAction: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ action, userIds }: { action: string; userIds: string[] }) =>
        api.post('/users/bulk', { action, userIds }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['users'] });
      },
    });
  },

  useModerateContent: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({
        contentId,
        action,
        notes,
      }: {
        contentId: string;
        action: 'APPROVE' | 'REJECT' | 'DELETE' | 'WARN' | 'SHADOW_BAN' | 'ESCALATE';
        notes?: string;
      }) => api.post(`/content/reported/${contentId}/moderate`, { action, notes }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['content', 'moderation'] });
      },
    });
  },

  useUpdateFeatureFlag: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ key, data }: { key: string; data: Partial<FeatureFlag> }) =>
        api.put(`/system/feature-flags/${key}`, data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.featureFlags });
      },
    });
  },

  useCreateFeatureFlag: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (data: Omit<FeatureFlag, 'createdAt' | 'updatedAt' | 'createdBy'>) =>
        api.post('/system/feature-flags', data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.featureFlags });
      },
    });
  },

  useDeleteFeatureFlag: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (key: string) => api.delete(`/system/feature-flags/${key}`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.featureFlags });
      },
    });
  },

  useUpdateConfig: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ key, value, description }: { key: string; value: string; description?: string }) =>
        api.put(`/system/config/${key}`, { value, description }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['system', 'config'] });
      },
    });
  },

  useCreateBackup: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (data: { type: Backup['type']; notes?: string }) =>
        api.post('/system/backups', data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.backups });
      },
    });
  },

  useRestoreBackup: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ backupId, confirmCode }: { backupId: string; confirmCode: string }) =>
        api.post(`/system/backups/${backupId}/restore`, { confirmCode }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.backups });
      },
    });
  },

  useDeleteBackup: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (backupId: string) => api.delete(`/system/backups/${backupId}`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.system.backups });
      },
    });
  },

  useCreateRole: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (data: Omit<Role, 'id' | 'createdAt' | 'updatedAt'>) =>
        api.post('/security/roles', data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.roles });
      },
    });
  },

  useUpdateRole: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ id, data }: { id: string; data: Partial<Role> }) =>
        api.put(`/security/roles/${id}`, data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.roles });
      },
    });
  },

  useDeleteRole: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (id: string) => api.delete(`/security/roles/${id}`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.roles });
      },
    });
  },

  useCreateApiKey: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (data: { name: string; scopes: string[]; expiresAt?: string }) =>
        api.post('/security/api-keys', data),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.apiKeys });
      },
    });
  },

  useRevokeApiKey: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (id: string) => api.delete(`/security/api-keys/${id}`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.apiKeys });
      },
    });
  },

  useRevokeSession: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: ({ sessionId, reason }: { sessionId: string; reason: string }) =>
        api.post(`/security/sessions/${sessionId}/revoke`, { reason }),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.sessions });
      },
    });
  },

  useRevokeAllSessions: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (userId: string) => api.post(`/security/sessions/revoke-all/${userId}`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: queryKeys.security.sessions });
      },
    });
  },

  useMarkNotificationRead: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: (id: string) => api.put(`/notifications/${id}/read`),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
      },
    });
  },

  useMarkAllNotificationsRead: () => {
    const queryClient = useQueryClient();
    return useMutation({
      mutationFn: () => api.put('/notifications/read-all'),
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
      },
    });
  },

  useExportData: () => {
    return useMutation({
      mutationFn: ({ type, format, params }: { type: string; format: 'csv' | 'excel' | 'pdf'; params: Record<string, unknown> }) =>
        api.post('/export', { type, format, params }, { responseType: 'blob' }),
    });
  },
};

interface Device {
  id: string;
  name: string;
  platform: string;
  appVersion: string;
  osVersion: string;
  lastActive: string;
  isTrusted: boolean;
  pushToken?: string;
}

interface SecurityEvent {
  id: string;
  type: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  resolved: boolean;
}

export { queryKeys };