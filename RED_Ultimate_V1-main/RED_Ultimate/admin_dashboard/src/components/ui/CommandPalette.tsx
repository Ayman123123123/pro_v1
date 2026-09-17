'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator, CommandShortcut } from '@/components/ui/command';
import { Search, Loader2, Keyboard, ChevronRight } from 'lucide-react';
import { useRouter, useRoutes } from '@tanstack/react-router';
import { useAuth } from '@/components/providers/AuthProvider';
import { useSidebar } from '@/components/providers/SidebarProvider';
import { useTheme } from '@/components/providers/ThemeProvider';
import { useToast } from '@/hooks/useToast';
import { cn } from '@/utils/cn';

interface CommandItemData {
  label: string;
  description?: string;
  shortcut?: string;
  action: () => void;
  keywords?: string[];
  icon?: React.ReactNode;
  section: string;
}

const getCommandItems = (
  router: ReturnType<typeof useRouter>,
  auth: ReturnType<typeof useAuth>,
  sidebar: ReturnType<typeof useSidebar>,
  theme: ReturnType<typeof useTheme>,
  toast: ReturnType<typeof useToast>
): CommandItemData[] => {
  const items: CommandItemData[] = [
    {
      section: 'Navigation',
      label: 'Dashboard',
      description: 'Go to main dashboard',
      shortcut: '⌘ 1',
      icon: <span className="text-green-500">📊</span>,
      action: () => router.navigate({ to: '/dashboard' }),
      keywords: ['dashboard', 'home', 'main', 'overview'],
    },
    {
      section: 'Navigation',
      label: 'Users',
      description: 'Manage users',
      shortcut: '⌘ 2',
      icon: <span className="text-blue-500">👥</span>,
      action: () => router.navigate({ to: '/users' }),
      keywords: ['users', 'people', 'accounts'],
    },
    {
      section: 'Navigation',
      label: 'Analytics',
      description: 'View analytics and reports',
      shortcut: '⌘ 3',
      icon: <span className="text-purple-500">📈</span>,
      action: () => router.navigate({ to: '/analytics' }),
      keywords: ['analytics', 'reports', 'charts', 'metrics'],
    },
    {
      section: 'Navigation',
      label: 'Content Moderation',
      description: 'Moderate content',
      shortcut: '⌘ 4',
      icon: <span className="text-orange-500">🛡️</span>,
      action: () => router.navigate({ to: '/moderation' }),
      keywords: ['moderation', 'content', 'reports', 'review'],
    },
    {
      section: 'Navigation',
      label: 'System Admin',
      description: 'System administration',
      shortcut: '⌘ 5',
      icon: <span className="text-red-500">⚙️</span>,
      action: () => router.navigate({ to: '/system' }),
      keywords: ['system', 'admin', 'settings', 'config'],
    },
    {
      section: 'Navigation',
      label: 'Security',
      description: 'Security center',
      shortcut: '⌘ 6',
      icon: <span className="text-yellow-500">🔒</span>,
      action: () => router.navigate({ to: '/security' }),
      keywords: ['security', 'auth', 'roles', 'permissions'],
    },
  ];

  const userActions: CommandItemData[] = [
    {
      section: 'Actions',
      label: 'Toggle Sidebar',
      description: 'Show/hide sidebar',
      shortcut: '⌘ B',
      icon: <span>☰</span>,
      action: () => sidebar.toggleSidebar(),
      keywords: ['sidebar', 'menu', 'navigation', 'toggle'],
    },
    {
      section: 'Actions',
      label: 'Collapse Sidebar',
      description: 'Collapse/expand sidebar',
      shortcut: '⌘ Shift B',
      icon: <span>◀▶</span>,
      action: () => sidebar.toggleCollapse(),
      keywords: ['sidebar', 'collapse', 'compact'],
    },
    {
      section: 'Actions',
      label: 'Toggle Theme',
      description: 'Switch between light/dark/system',
      shortcut: '⌘ Shift T',
      icon: theme.resolvedTheme === 'dark' ? <span>☀️</span> : <span>🌙</span>,
      action: () => {
        const themes: ('light' | 'dark' | 'system')[] = ['light', 'dark', 'system'];
        const currentIndex = themes.indexOf(theme.theme);
        const nextTheme = themes[(currentIndex + 1) % themes.length];
        theme.setTheme(nextTheme);
      },
      keywords: ['theme', 'dark', 'light', 'mode', 'appearance'],
    },
    {
      section: 'Actions',
      label: 'Refresh Data',
      description: 'Refresh all queries',
      shortcut: '⌘ R',
      icon: <Loader2 className="h-4 w-4 animate-spin" />,
      action: () => {
        router.invalidate();
        toast.info('Refreshing', 'All data is being refreshed');
      },
      keywords: ['refresh', 'reload', 'update', 'sync'],
    },
  ];

  if (auth.isAuthenticated) {
    userActions.push(
      {
        section: 'Account',
        label: 'Profile',
        description: 'View your profile',
        shortcut: '⌘ P',
        icon: <span>👤</span>,
        action: () => router.navigate({ to: '/profile' }),
        keywords: ['profile', 'account', 'settings', 'user'],
      },
      {
        section: 'Account',
        label: 'Logout',
        description: 'Sign out of your account',
        shortcut: '⌘ Shift L',
        icon: <span>🚪</span>,
        action: () => auth.logout(),
        keywords: ['logout', 'signout', 'exit', 'leave'],
      }
    );
  }

  return [...items, ...userActions];
};

export function CommandPalette() {
  const [isOpen, setIsOpen] = useState(false);
  const router = useRouter();
  const auth = useAuth();
  const sidebar = useSidebar();
  const theme = useTheme();
  const toast = useToast();
  const commandItems = getCommandItems(router, auth, sidebar, theme, toast);
  const [filteredItems, setFilteredItems] = useState(commandItems);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setIsOpen(true);
      }
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [isOpen]);

  const handleFilter = useCallback((value: string) => {
    if (!value.trim()) {
      setFilteredItems(commandItems);
      return;
    }
    const lowerValue = value.toLowerCase();
    setFilteredItems(
      commandItems.filter(
        (item) =>
          item.label.toLowerCase().includes(lowerValue) ||
          item.description?.toLowerCase().includes(lowerValue) ||
          item.keywords?.some((k) => k.toLowerCase().includes(lowerValue))
      )
    );
  }, [commandItems]);

  const groupedItems = filteredItems.reduce((acc, item) => {
    if (!acc[item.section]) acc[item.section] = [];
    acc[item.section].push(item);
    return acc;
  }, {} as Record<string, CommandItemData[]>);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-start justify-center pt-16 px-4">
      <div className="relative w-full max-w-2xl">
        <Command>
          <CommandInput
            ref={inputRef}
            placeholder="Type a command or search... (⌘K)"
            onValueChange={handleFilter}
            className="bg-background/95 backdrop-blur-sm border border-border rounded-lg px-4 py-3 text-lg shadow-xl"
          />
          <CommandList className="max-h-[60vh] overflow-auto mt-2 bg-background/95 backdrop-blur-sm border border-border rounded-lg shadow-xl">
            {Object.entries(groupedItems).map(([section, items]) => (
              <CommandGroup key={section} heading={section}>
                {items.map((item) => (
                  <CommandItem
                    key={item.label}
                    onSelect={item.action}
                    className="flex items-center gap-3 px-3 py-2"
                  >
                    <span className="flex h-8 w-8 items-center justify-center text-muted-foreground">
                      {item.icon}
                    </span>
                    <div className="flex-1 text-left">
                      <div className="font-medium">{item.label}</div>
                      {item.description && (
                        <div className="text-sm text-muted-foreground">{item.description}</div>
                      )}
                    </div>
                    {item.shortcut && (
                      <CommandShortcut className="text-xs text-muted-foreground font-mono">
                        {item.shortcut}
                      </CommandShortcut>
                    )}
                    <ChevronRight className="h-4 w-4 text-muted-foreground" />
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
            {filteredItems.length === 0 && (
              <CommandEmpty className="py-8 text-center text-muted-foreground">
                No commands found
              </CommandEmpty>
            )}
          </CommandList>
        </Command>
        <div className="absolute -bottom-10 left-1/2 -translate-x-1/2 text-xs text-muted-foreground flex items-center gap-2">
          <Keyboard className="h-3 w-3" />
          <span>⌘K to open</span>
        </div>
      </div>
    </div>
  );
}