'use client';

import { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { User } from '@/types';
import { authStore, adminLogin, adminLogout } from '@/api';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/**
 * ✅ FIX 2026-09-22:
 * - useEffect لم يكن مستورداً (TS2304)
 * - getAccessToken غير موجود في @/api (TS2305)
 * - authStore.user() يرجع حقول optionals → نحولها إلى User بقيم افتراضية
 */
function toUser(raw: ReturnType<typeof authStore.user>): User | null {
  if (!raw) return null;
  return {
    id: raw.id || raw.username || 'unknown',
    redId: raw.redId || '',
    username: raw.username || '',
    displayName: raw.displayName || raw.username || '',
    status: 'APPROVED',
    role: (raw.role as User['role']) || 'ADMIN',
    createdAt: new Date(0).toISOString(),
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(toUser(authStore.user()));
  const [isAuthenticated, setIsAuthenticated] = useState(authStore.isAuthenticated());
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    setUser(toUser(authStore.user()));
    setIsAuthenticated(authStore.isAuthenticated());
    setIsLoading(false);
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const login = async (username: string, password: string) => {
    setIsLoading(true);
    try {
      await adminLogin(username, password);
      setUser(toUser(authStore.user()));
      setIsAuthenticated(true);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    await adminLogout();
    setUser(null);
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
