import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { User, Session, Notification, ThemeMode } from '@/types';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  setAuth: (user: User, accessToken: string, refreshToken?: string) => void;
  clearAuth: () => void;
  updateUser: (user: Partial<User>) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      setAuth: (user, accessToken, refreshToken) =>
        set({
          user,
          accessToken,
          refreshToken: refreshToken || null,
          isAuthenticated: true,
        }),
      clearAuth: () =>
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
        }),
      updateUser: (userData) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...userData } : null,
        })),
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

export function getAccessToken(): string | null {
  return useAuthStore.getState().accessToken;
}

export function clearAuth(): void {
  useAuthStore.getState().clearAuth();
}

interface UIState {
  theme: ThemeMode;
  sidebarOpen: boolean;
  sidebarCollapsed: boolean;
  commandPaletteOpen: boolean;
  notifications: Notification[];
  unreadCount: number;
  toasts: Toast[];
  setTheme: (theme: ThemeMode) => void;
  toggleSidebar: () => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleCommandPalette: () => void;
  addNotification: (notification: Notification) => void;
  markNotificationRead: (id: string) => void;
  markAllNotificationsRead: () => void;
  addToast: (toast: Omit<Toast, 'id'>) => string;
  removeToast: (id: string) => void;
}

interface Toast {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message?: string;
  duration?: number;
  action?: { label: string; onClick: () => void };
}

export const useUIStore = create<UIState>()(
  persist(
    (set, get) => ({
      theme: 'system',
      sidebarOpen: true,
      sidebarCollapsed: false,
      commandPaletteOpen: false,
      notifications: [],
      unreadCount: 0,
      toasts: [],
      setTheme: (theme) => set({ theme }),
      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
      setSidebarCollapsed: (collapsed) => set({ sidebarCollapsed: collapsed }),
      toggleCommandPalette: () => set((state) => ({ commandPaletteOpen: !state.commandPaletteOpen })),
      addNotification: (notification) =>
        set((state) => ({
          notifications: [notification, ...state.notifications].slice(0, 100),
          unreadCount: state.unreadCount + (notification.read ? 0 : 1),
        })),
      markNotificationRead: (id) =>
        set((state) => ({
          notifications: state.notifications.map((n) =>
            n.id === id ? { ...n, read: true } : n
          ),
          unreadCount: Math.max(0, state.unreadCount - 1),
        })),
      markAllNotificationsRead: () =>
        set((state) => ({
          notifications: state.notifications.map((n) => ({ ...n, read: true })),
          unreadCount: 0,
        })),
      addToast: (toast) => {
        const id = crypto.randomUUID();
        set((state) => ({
          toasts: [...state.toasts, { ...toast, id }],
        }));
        return id;
      },
      removeToast: (id) =>
        set((state) => ({
          toasts: state.toasts.filter((t) => t.id !== id),
        })),
    }),
    {
      name: 'ui-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        theme: state.theme,
        sidebarCollapsed: state.sidebarCollapsed,
      }),
    }
  )
);

interface ModerationState {
  selectedContentIds: Set<string>;
  filter: {
    status?: string;
    category?: string;
    priority?: string;
    dateRange?: { start: Date; end: Date };
  };
  viewMode: 'list' | 'grid';
  selectContent: (id: string) => void;
  deselectContent: (id: string) => void;
  toggleContent: (id: string) => void;
  selectAll: (ids: string[]) => void;
  clearSelection: () => void;
  setFilter: (filter: Partial<ModerationState['filter']>) => void;
  setViewMode: (mode: 'list' | 'grid') => void;
}

export const useModerationStore = create<ModerationState>((set) => ({
  selectedContentIds: new Set(),
  filter: {},
  viewMode: 'list',
  selectContent: (id) =>
    set((state) => {
      const newSet = new Set(state.selectedContentIds);
      newSet.add(id);
      return { selectedContentIds: newSet };
    }),
  deselectContent: (id) =>
    set((state) => {
      const newSet = new Set(state.selectedContentIds);
      newSet.delete(id);
      return { selectedContentIds: newSet };
    }),
  toggleContent: (id) =>
    set((state) => {
      const newSet = new Set(state.selectedContentIds);
      if (newSet.has(id)) newSet.delete(id);
      else newSet.add(id);
      return { selectedContentIds: newSet };
    }),
  selectAll: (ids) => set({ selectedContentIds: new Set(ids) }),
  clearSelection: () => set({ selectedContentIds: new Set() }),
  setFilter: (filter) =>
    set((state) => ({ filter: { ...state.filter, ...filter } })),
  setViewMode: (mode) => set({ viewMode: mode }),
}));

interface ReportBuilderState {
  dimensions: string[];
  metrics: string[];
  filters: Record<string, unknown>;
  visualizations: VisualizationConfig[];
  dragItem: { type: 'dimension' | 'metric'; key: string } | null;
  addDimension: (dimension: string) => void;
  removeDimension: (dimension: string) => void;
  addMetric: (metric: string) => void;
  removeMetric: (metric: string) => void;
  setFilters: (filters: Record<string, unknown>) => void;
  addVisualization: (config: VisualizationConfig) => void;
  updateVisualization: (index: number, config: Partial<VisualizationConfig>) => void;
  removeVisualization: (index: number) => void;
  reorderVisualizations: (from: number, to: number) => void;
  setDragItem: (item: ReportBuilderState['dragItem']) => void;
  reset: () => void;
}

interface VisualizationConfig {
  id: string;
  type: 'table' | 'bar' | 'line' | 'pie' | 'area' | 'heatmap' | 'funnel' | 'cohort';
  title: string;
  dimensions: string[];
  metrics: string[];
  filters: Record<string, unknown>;
  options: Record<string, unknown>;
}

export const useReportBuilderStore = create<ReportBuilderState>((set) => ({
  dimensions: [],
  metrics: [],
  filters: {},
  visualizations: [],
  dragItem: null,
  addDimension: (dimension) =>
    set((state) => ({
      dimensions: state.dimensions.includes(dimension) ? state.dimensions : [...state.dimensions, dimension],
    })),
  removeDimension: (dimension) =>
    set((state) => ({
      dimensions: state.dimensions.filter((d) => d !== dimension),
    })),
  addMetric: (metric) =>
    set((state) => ({
      metrics: state.metrics.includes(metric) ? state.metrics : [...state.metrics, metric],
    })),
  removeMetric: (metric) =>
    set((state) => ({
      metrics: state.metrics.filter((m) => m !== metric),
    })),
  setFilters: (filters) => set({ filters }),
  addVisualization: (config) =>
    set((state) => ({
      visualizations: [...state.visualizations, { ...config, id: crypto.randomUUID() }],
    })),
  updateVisualization: (index, config) =>
    set((state) => {
      const newVisualizations = [...state.visualizations];
      newVisualizations[index] = { ...newVisualizations[index], ...config };
      return { visualizations: newVisualizations };
    }),
  removeVisualization: (index) =>
    set((state) => ({
      visualizations: state.visualizations.filter((_, i) => i !== index),
    })),
  reorderVisualizations: (from, to) =>
    set((state) => {
      const newVisualizations = [...state.visualizations];
      const [removed] = newVisualizations.splice(from, 1);
      newVisualizations.splice(to, 0, removed);
      return { visualizations: newVisualizations };
    }),
  setDragItem: (item) => set({ dragItem: item }),
  reset: () =>
    set({
      dimensions: [],
      metrics: [],
      filters: {},
      visualizations: [],
      dragItem: null,
    }),
}));

interface FeatureFlagState {
  flags: Record<string, boolean>;
  setFlag: (key: string, value: boolean) => void;
  setFlags: (flags: Record<string, boolean>) => void;
}

export const useFeatureFlagStore = create<FeatureFlagState>()(
  persist(
    (set) => ({
      flags: {},
      setFlag: (key, value) =>
        set((state) => ({ flags: { ...state.flags, [key]: value } })),
      setFlags: (flags) => set({ flags }),
    }),
    {
      name: 'feature-flags',
      storage: createJSONStorage(() => localStorage),
    }
  )
);