export interface User {
  id: string;
  redId: string;
  username: string;
  displayName: string;
  email?: string;
  phone?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'BANNED';
  role: 'USER' | 'ADMIN' | 'MODERATOR' | 'SUPPORT' | 'VIEWER' | 'CUSTOM';
  createdAt: string;
  approvedAt?: string;
  lastSeen?: number;
  avatarUrl?: string;
  riskScore?: number;
  isOnline?: boolean;
  deviceCount?: number;
  sessionCount?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DashboardMetrics {
  users: {
    total: number;
    approved: number;
    pending: number;
    banned: number;
    new24h: number;
    approvalRate: number;
  };
  messages: {
    total: number;
    last24h: number;
    perSecond: number;
  };
  calls: {
    total: number;
    active: number;
    avgDuration: number;
  };
  channels: {
    total: number;
    active: number;
  };
  revenue: {
    total: number;
    last30d: number;
    currency: string;
  };
  health: {
    cpu: number;
    memory: number;
    disk: number;
    network: number;
    dbConnections: number;
    queueDepth: number;
  };
}

export interface SystemHealthComponent {
  id: string;
  component: string;
  status: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  cpuUsage?: number;
  memoryUsage?: number;
  diskUsage?: number;
  activeConnections?: number;
  requestsPerSecond?: number;
  averageResponseMs?: number;
  errorRate?: number;
  details?: string;
  lastCheckAt: string;
}

export interface Alert {
  id: string;
  severity: 'P0' | 'P1' | 'P2' | 'P3';
  title: string;
  message: string;
  component: string;
  acknowledged: boolean;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  createdAt: string;
  metadata?: Record<string, unknown>;
}

export interface TimeRange {
  label: string;
  value: string;
  start: Date;
  end: Date;
}

export const TIME_RANGES: TimeRange[] = [
  { label: '1 Hour', value: '1h', start: new Date(Date.now() - 3600000), end: new Date() },
  { label: '6 Hours', value: '6h', start: new Date(Date.now() - 21600000), end: new Date() },
  { label: '24 Hours', value: '24h', start: new Date(Date.now() - 86400000), end: new Date() },
  { label: '7 Days', value: '7d', start: new Date(Date.now() - 604800000), end: new Date() },
  { label: '30 Days', value: '30d', start: new Date(Date.now() - 2592000000), end: new Date() },
  { label: '90 Days', value: '90d', start: new Date(Date.now() - 7776000000), end: new Date() },
];

export interface ReportedContent {
  id: string;
  type: 'POST' | 'COMMENT' | 'MESSAGE' | 'MEDIA' | 'CHANNEL' | 'USER';
  contentId: string;
  reporterId: string;
  reason: string;
  category: 'SPAM' | 'HARASSMENT' | 'VIOLENCE' | 'ILLEGAL' | 'CSAM' | 'COPYRIGHT' | 'MISINFORMATION' | 'OTHER';
  status: 'PENDING' | 'REVIEWING' | 'RESOLVED' | 'DISMISSED' | 'ESCALATED';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  createdAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  actionTaken?: string;
  notes?: string;
}

export interface Channel {
  id: string;
  name: string;
  description?: string;
  type: 'PUBLIC' | 'PRIVATE' | 'PROTECTED';
  memberCount: number;
  messageCount: number;
  createdAt: string;
  ownerId: string;
  isVerified: boolean;
  riskScore?: number;
}

export interface FeatureFlag {
  key: string;
  name: string;
  description?: string;
  enabled: boolean;
  rolloutPercentage: number;
  targetRules: TargetRule[];
  schedule?: {
    startAt?: string;
    endAt?: string;
  };
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

export interface TargetRule {
  attribute: string;
  operator: 'equals' | 'not_equals' | 'contains' | 'not_contains' | 'in' | 'not_in' | 'greater_than' | 'less_than';
  values: string[];
}

export interface ConfigEntry {
  key: string;
  value: string;
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON';
  description?: string;
  version: number;
  updatedAt: string;
  updatedBy: string;
}

export interface AuditLogEntry {
  id: string;
  adminId: string;
  adminUsername: string;
  action: string;
  category: string;
  severity: 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
  targetType?: string;
  targetId?: string;
  details?: Record<string, unknown>;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

export interface Backup {
  id: string;
  name: string;
  type: 'FULL' | 'INCREMENTAL' | 'CONFIG_ONLY' | 'USER_DATA';
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  size: number;
  startedAt: string;
  completedAt?: string;
  notes?: string;
}

export interface Role {
  id: string;
  name: string;
  description?: string;
  permissions: Permission[];
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Permission {
  resource: string;
  actions: string[];
}

export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  scopes: string[];
  expiresAt?: string;
  lastUsedAt?: string;
  createdAt: string;
  createdBy: string;
  isActive: boolean;
}

export interface Session {
  id: string;
  userId: string;
  deviceId: string;
  deviceName: string;
  ipAddress: string;
  location?: string;
  userAgent: string;
  createdAt: string;
  lastActivityAt: string;
  isCurrent: boolean;
  isTrusted: boolean;
  riskScore: number;
}

export interface Notification {
  id: string;
  type: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  actionUrl?: string;
}

export interface WebSocketMessage<T = unknown> {
  type: string;
  payload: T;
  timestamp: string;
}

export interface RealtimeMetrics {
  users: number;
  messages: number;
  calls: number;
  channels: number;
  cpu: number;
  memory: number;
  disk: number;
  network: number;
  timestamp: string;
  health?: Record<string, SystemHealthComponent>;
}

export type ThemeMode = 'light' | 'dark' | 'system';

export interface Device {
  id: string;
  userId: string;
  deviceId: string;
  deviceName: string;
  platform: string;
  status: string;
  identityFingerprint?: string;
  lastSeen?: string;
  createdAt: string;
}

export interface SecurityEvent {
  id: string;
  userId: string;
  type: string;
  severity: string;
  message: string;
  ipAddress?: string;
  createdAt: string;
}

export interface ActivityLogEntry {
  id: string;
  userId: string;
  action: string;
  category: string;
  message: string;
  ipAddress?: string;
  createdAt: string;
}