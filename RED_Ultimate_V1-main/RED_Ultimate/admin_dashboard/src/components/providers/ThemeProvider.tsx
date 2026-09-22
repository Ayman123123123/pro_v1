'use client';

import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { ThemeMode } from '@/types';

interface ThemeContextType {
  theme: ThemeMode;
  resolvedTheme: 'light' | 'dark';
  setTheme: (theme: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>(() => {
    if (typeof window !== 'undefined') {
      return (localStorage.getItem('yns-theme') as ThemeMode) || 'system';
    }
    return 'system';
  });
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');
  const [mounted, setMounted] = useState(false);

  const setTheme = (newTheme: ThemeMode) => {
    setThemeState(newTheme);
    localStorage.setItem('yns-theme', newTheme);
  };

  const resolveTheme = (mode: ThemeMode): 'light' | 'dark' => {
    if (mode !== 'system') return mode;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  };

  useEffect(() => {
    setMounted(true);
    const resolved = resolveTheme(theme);
    setResolvedTheme(resolved);
    document.documentElement.dataset.theme = resolved;
    document.documentElement.classList.toggle('dark', resolved === 'dark');
  }, [theme]);

  useEffect(() => {
    if (theme !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => {
      const resolved = mq.matches ? 'dark' : 'light';
      setResolvedTheme(resolved);
      document.documentElement.dataset.theme = resolved;
      document.documentElement.classList.toggle('dark', resolved === 'dark');
    };
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [theme]);

  // ✅ FIX 2026-09-22: كان العيب القاتل: عند !mounted كانت تُعرض children
  // بدون ThemeContext.Provider → useTheme() داخل _layout يرمي
  // "useTheme must be used within a ThemeProvider" عند أول render.
  // الحل: الـ Provider يُعرض دائماً، وmounted يُستخدم فقط لتجنب وميض الثيم.
  const value = { theme, resolvedTheme: mounted ? resolvedTheme : (theme === 'dark' ? 'dark' : theme === 'light' ? 'light' : 'dark'), setTheme };

  return (
    <ThemeContext.Provider value={value}>
      <div className="min-h-screen" data-theme={value.resolvedTheme}>
        {children}
      </div>
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}