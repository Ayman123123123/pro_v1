import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './client';
import { usePolling } from '@/hooks/usePolling';
import type {
  User,
  Device,
  SecurityEvent,
  ActivityLogEntry,
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
  RealtimeMetrics as CanonicalRealtimeMetrics,
} from '@/types';

/**
 * طبقة الاستعلامات الخفيفة — بلا `@tanstack/react-query`
 * (كانت ميتة: لا QueryClientProvider في التطبيق، فكانت الخطافات تنفجر
 * وقت التشغيل؛ وpackage.json ممنوع المساس به).
 * العقد الخارجي مطابق لسابقه: كل خطاف استعلام يُرجع
 * `{ data, error, isLoading, isError, isSuccess, refetch }`
 * وكل طفرة تُرجع `{ mutate, mutateAsync, isPending, isLoading, isError, error, reset }`.
 * لا setInterval خام هنا (حارس الواجهة) — الاستطلاع عبر usePolling.
 */

// ━━━━━━━━━━━━ RealtimeMetrics موحّدة ━━━━━━━━━━━━
// الشكل المسطّح من `@/types` (users/messages/calls/... أرقام) مضافًا إليه
// حقل `health` الاختياري من شكل `src/api.ts` الحي
// (`{ users, health: Record<string, SystemHealth>, timestamp }`).
// تقاطع الشكلين في نوع واحد: أي حمولة من أي عميل تُقبل وتُقرأ بأمان.
// (التوحيد النهائي في `src/types/index.ts` خارج النطاق — لم يُمسّ.)
export interface RealtimeMetrics extends CanonicalRealtimeMetrics {
  health?: Record<string, SystemHealthComponent>;
}

// ━━━━━━━━━━━━ مفاتيح الاستعلام (للتوافق — بلا QueryKey) ━━━━━━━━━━━━
export const queryKeys = {
  dashboard: {
    metrics: ['dashboard', 'metrics'] as const,
    health: ['dashboard', 'health'] as const,
    alerts: ['dashboard', 'alerts'] as const,
    realtime: ['dashboard', 'realtime'] as const,
  },
  users: {
    list: (params: Record<string, unknown>) => ['users', 'list', params] as const,
    detail: (id: string) => ['users', 'detail', id] as const,
    sessions: (id: string) => ['users', 'sessions', id] as const,
    devices: (id: string) => ['users', 'devices', id] as const,
    activity: (id: string) => ['users', 'activity', id] as const,
    security: (id: string) => ['users', 'security', id] as const,
  },
  content: {
    moderationQueue: (params: Record<string, unknown>) => ['content', 'moderation', params] as const,
    channels: (params: Record<string, unknown>) => ['content', 'channels', params] as const,
    channelDetail: (id: string) => ['content', 'channel', id] as const,
    reportedContent: (id: string) => ['content', 'reported', id] as const,
  },
  system: {
    featureFlags: ['system', 'featureFlags'] as const,
    config: (params: Record<string, unknown>) => ['system', 'config', params] as const,
    auditLog: (params: Record<string, unknown>) => ['system', 'auditLog', params] as const,
    backups: ['system', 'backups'] as const,
    migrations: ['system', 'migrations'] as const,
    cache: ['system', 'cache'] as const,
  },
  security: {
    roles: ['security', 'roles'] as const,
    apiKeys: ['security', 'apiKeys'] as const,
    sessions: ['security', 'sessions'] as const,
  },
  analytics: {
    reports: (params: Record<string, unknown>) => ['analytics', 'reports', params] as const,
    customReport: (id: string) => ['analytics', 'customReport', id] as const,
  },
} as const;

// ━━━━━━━━━━━━ البنية الخفيفة ━━━━━━━━━━━━
export interface QueryResult<T> {
  data: T | undefined;
  error: unknown;
  isLoading: boolean;
  isError: boolean;
  isSuccess: boolean;
  refetch: () => Promise<void>;
}

interface QueryOptions {
  /** معادل `enabled` في react-query — لا جلب إطلاقًا عند false. */
  enabled?: boolean;
  /** معادل `refetchInterval` — بالمللي ثانية، null يعني بلا استطلاع. */
  intervalMs?: number | null;
  /** معادل `placeholderData: previous => previous` — إبقاء القديم أثناء التحديث. */
  keepPrevious?: boolean;
}

export function useApiQuery<T>(
  fetcher: () => Promise<T>,
  deps: unknown[],
  options: QueryOptions = {}
): QueryResult<T> {
  const { enabled = true, intervalMs = null, keepPrevious = true } = options;
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState<boolean>(enabled);
  const mounted = useRef(true);
  const requestId = useRef(0);
  const fetcherRef = useRef(fetcher);
  const key = JSON.stringify(deps ?? []);

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const refetch = useCallback(async () => {
    if (!enabled) return;
    const id = ++requestId.current;
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetcherRef.current();
      if (mounted.current && requestId.current === id) setData(result);
    } catch (e) {
      if (mounted.current && requestId.current === id) {
        if (!keepPrevious) setData(undefined);
        setError(e);
      }
    } finally {
      if (mounted.current && requestId.current === id) setIsLoading(false);
    }
  }, [enabled, key, keepPrevious]);

  useEffect(() => {
    mounted.current = true;
    if (enabled) void refetch();
    else setIsLoading(false);
    return () => {
      mounted.current = false;
    };
  }, [refetch, enabled]);

  // استطلاع مُدار (يتوقف عند إخفاء التبويب/انقطاع الشبكة) بدل refetchInterval
  usePolling(
    () => {
      void refetch();
    },
    enabled ? intervalMs : null,
    { immediate: false }
  );

  return {
    data,
    error,
    isLoading,
    isError: error !== null,
    isSuccess: error === null && data !== undefined,
    refetch,
  };
}

export interface MutationResult<TArgs, TRes> {
  mutate: (args: TArgs) => void;
  mutateAsync: (args: TArgs) => Promise<TRes>;
  isPending: boolean;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  reset: () => void;
}

export function useApiMutation<TArgs = void, TRes = unknown>(
  fn: (args: TArgs) => Promise<TRes>
): MutationResult<TArgs, TRes> {
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const fnRef = useRef(fn);

  useEffect(() => {
    fnRef.current = fn;
  });

  const mutateAsync = useCallback(async (args: TArgs): Promise<TRes> => {
    setIsPending(true);
    setError(null);
    try {
      return await fnRef.current(args);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setIsPending(false);
    }
  }, []);

  const mutate = useCallback(
    (args: TArgs) => {
      // بلا كاش مركزي يُبطل — الصفحات تُحدّث عبر refetch/الاستطلاع الدوري
      void mutateAsync(args).catch(() => {});
    },
    [mutateAsync]
  );

  const reset = useCallback(() => {
    setError(null);
    setIsPending(false);
  }, []);

  return { mutate, mutateAsync, isPending, isLoading: isPending, isError: error !== null, error, reset };
}

// ━━━━━━━━━━━━ الاستعلامات (الأسماء والمسارات كما كانت) ━━━━━━━━━━━━
export function useDashboardMetrics() {
  return useApiQuery<DashboardMetrics>(() => api.get<DashboardMetrics>('/dashboard/metrics'), [], {
    intervalMs: 30000,
  });
}

export function useSystemHealth() {
  return useApiQuery<SystemHealthComponent[]>(
    () => api.get<SystemHealthComponent[]>('/dashboard/health'),
    [],
    { intervalMs: 60000 }
  );
}

export function useAlerts(params?: { severity?: string; acknowledged?: boolean }) {
  const p = params ?? {};
  return useApiQuery<Alert[]>(() => api.get<Alert[]>('/dashboard/alerts', p), [p], {
    intervalMs: 15000,
  });
}

export function useRealtimeMetrics() {
  return useApiQuery<RealtimeMetrics>(() => api.get<RealtimeMetrics>('/dashboard/realtime'), [], {
    intervalMs: 5000,
  });
}

export function useUsers(
  params: {
    page?: number;
    size?: number;
    status?: string;
    role?: string;
    search?: string;
    sortBy?: string;
    sortDir?: 'asc' | 'desc';
  } = {}
) {
  return useApiQuery<PageResponse<User>>(() => api.get<PageResponse<User>>('/users', params), [params]);
}

export function useUserDetail(userId: string) {
  return useApiQuery<User>(() => api.get<User>(`/users/${userId}`), [userId], {
    enabled: !!userId,
  });
}

export function useUserSessions(userId: string) {
  return useApiQuery<Session[]>(() => api.get<Session[]>(`/users/${userId}/sessions`), [userId], {
    enabled: !!userId,
  });
}

export function useUserDevices(userId: string) {
  return useApiQuery<Device[]>(() => api.get<Device[]>(`/users/${userId}/devices`), [userId], {
    enabled: !!userId,
  });
}

export function useUserActivity(userId: string) {
  return useApiQuery<ActivityLogEntry[]>(
    () => api.get<ActivityLogEntry[]>(`/users/${userId}/activity`),
    [userId],
    { enabled: !!userId }
  );
}

export function useUserSecurityEvents(userId: string) {
  return useApiQuery<SecurityEvent[]>(
    () => api.get<SecurityEvent[]>(`/users/${userId}/security-events`),
    [userId],
    { enabled: !!userId }
  );
}

export function useModerationQueue(
  params: {
    page?: number;
    size?: number;
    status?: string;
    category?: string;
    priority?: string;
  } = {}
) {
  return useApiQuery<PageResponse<ReportedContent>>(
    () => api.get<PageResponse<ReportedContent>>('/content/moderation', params),
    [params],
    { intervalMs: 10000 }
  );
}

export function useReportedContent(contentId: string) {
  return useApiQuery<ReportedContent>(
    () => api.get<ReportedContent>(`/content/reported/${contentId}`),
    [contentId],
    { enabled: !!contentId }
  );
}

export function useChannels(
  params: {
    page?: number;
    size?: number;
    type?: string;
    search?: string;
  } = {}
) {
  return useApiQuery<PageResponse<Channel>>(
    () => api.get<PageResponse<Channel>>('/content/channels', params),
    [params]
  );
}

export function useChannelDetail(channelId: string) {
  return useApiQuery<Channel>(() => api.get<Channel>(`/content/channels/${channelId}`), [channelId], {
    enabled: !!channelId,
  });
}

export function useFeatureFlags() {
  return useApiQuery<FeatureFlag[]>(() => api.get<FeatureFlag[]>('/system/feature-flags'), []);
}

export function useConfig(params: { page?: number; size?: number; search?: string } = {}) {
  return useApiQuery<PageResponse<ConfigEntry>>(
    () => api.get<PageResponse<ConfigEntry>>('/system/config', params),
    [params]
  );
}

export function useAuditLog(
  params: {
    page?: number;
    size?: number;
    adminId?: string;
    action?: string;
    category?: string;
    severity?: string;
    startDate?: string;
    endDate?: string;
  } = {}
) {
  return useApiQuery<PageResponse<AuditLogEntry>>(
    () => api.get<PageResponse<AuditLogEntry>>('/system/audit', params),
    [params]
  );
}

export function useBackups() {
  return useApiQuery<Backup[]>(() => api.get<Backup[]>('/system/backups'), []);
}

export function useRoles() {
  return useApiQuery<Role[]>(() => api.get<Role[]>('/security/roles'), []);
}

export function useApiKeys() {
  return useApiQuery<ApiKey[]>(() => api.get<ApiKey[]>('/security/api-keys'), []);
}

export function useAllSessions() {
  return useApiQuery<Session[]>(() => api.get<Session[]>('/security/sessions'), [], {
    intervalMs: 30000,
  });
}

export function useNotifications(params?: { unreadOnly?: boolean }) {
  const p = params ?? {};
  return useApiQuery<Notification[]>(() => api.get<Notification[]>('/notifications', p), [p], {
    intervalMs: 30000,
  });
}

// ━━━━━━━━━━━━ الطفرات (الأسماء والتواقيع كما كانت) ━━━━━━━━━━━━
export const mutations = {
  useApproveUser: () =>
    useApiMutation((userId: string) => api.post(`/users/${userId}/approve`)),

  useBanUser: () =>
    useApiMutation(
      ({ userId, reason, durationDays }: { userId: string; reason: string; durationDays?: number }) =>
        api.post(`/users/${userId}/ban`, { reason, durationDays })
    ),

  useUnbanUser: () => useApiMutation((userId: string) => api.post(`/users/${userId}/unban`)),

  useBulkAction: () =>
    useApiMutation(({ action, userIds }: { action: string; userIds: string[] }) =>
      api.post('/users/bulk', { action, userIds })
    ),

  useModerateContent: () =>
    useApiMutation(
      ({
        contentId,
        action,
        notes,
      }: {
        contentId: string;
        action: 'APPROVE' | 'REJECT' | 'DELETE' | 'WARN' | 'SHADOW_BAN' | 'ESCALATE';
        notes?: string;
      }) => api.post(`/content/reported/${contentId}/moderate`, { action, notes })
    ),

  useUpdateFeatureFlag: () =>
    useApiMutation(({ key, data }: { key: string; data: Partial<FeatureFlag> }) =>
      api.put(`/system/feature-flags/${key}`, data)
    ),

  useCreateFeatureFlag: () =>
    useApiMutation((data: Omit<FeatureFlag, 'createdAt' | 'updatedAt' | 'createdBy'>) =>
      api.post('/system/feature-flags', data)
    ),

  useDeleteFeatureFlag: () =>
    useApiMutation((key: string) => api.delete(`/system/feature-flags/${key}`)),

  useUpdateConfig: () =>
    useApiMutation(
      ({ key, value, description }: { key: string; value: string; description?: string }) =>
        api.put(`/system/config/${key}`, { value, description })
    ),

  useCreateBackup: () =>
    useApiMutation((data: { type: Backup['type']; notes?: string }) =>
      api.post('/system/backups', data)
    ),

  useRestoreBackup: () =>
    useApiMutation(({ backupId, confirmCode }: { backupId: string; confirmCode: string }) =>
      api.post(`/system/backups/${backupId}/restore`, { confirmCode })
    ),

  useDeleteBackup: () =>
    useApiMutation((backupId: string) => api.delete(`/system/backups/${backupId}`)),

  useCreateRole: () =>
    useApiMutation((data: Omit<Role, 'id' | 'createdAt' | 'updatedAt'>) =>
      api.post('/security/roles', data)
    ),

  useUpdateRole: () =>
    useApiMutation(({ id, data }: { id: string; data: Partial<Role> }) =>
      api.put(`/security/roles/${id}`, data)
    ),

  useDeleteRole: () => useApiMutation((id: string) => api.delete(`/security/roles/${id}`)),

  useCreateApiKey: () =>
    useApiMutation((data: { name: string; scopes: string[]; expiresAt?: string }) =>
      api.post('/security/api-keys', data)
    ),

  useRevokeApiKey: () =>
    useApiMutation((id: string) => api.delete(`/security/api-keys/${id}`)),

  useRevokeSession: () =>
    useApiMutation(({ sessionId, reason }: { sessionId: string; reason: string }) =>
      api.post(`/security/sessions/${sessionId}/revoke`, { reason })
    ),

  useRevokeAllSessions: () =>
    useApiMutation((userId: string) => api.post(`/security/sessions/revoke-all/${userId}`)),

  useMarkNotificationRead: () =>
    useApiMutation((id: string) => api.put(`/notifications/${id}/read`)),

  useMarkAllNotificationsRead: () => useApiMutation(() => api.put('/notifications/read-all')),

  useExportData: () =>
    useApiMutation(
      ({
        type,
        format,
        params,
      }: {
        type: string;
        format: 'csv' | 'excel' | 'pdf';
        params: Record<string, unknown>;
      }) => api.post('/export', { type, format, params }, { responseType: 'blob' })
    ),
};

// NOTE: حُذفت الواجهتان المحليتان `Device` و`SecurityEvent` من هذا الملف —
// تُستوردان الآن من `@/types` (المصدر الوحيد). بقيت نسخة `Device` في
// `src/pages/tabs/AuthorityTab.tsx:6` خارج النطاق — يجب حذفها هناك
// واستيراد `Device` من `@/types` بدلها (سطر واحد، لم يُمسّ التزامًا بالنطاق).

// للحفاظ على التوافق مع أي مستورد لنوع Permission من هنا سابقًا:
export type { Permission };
