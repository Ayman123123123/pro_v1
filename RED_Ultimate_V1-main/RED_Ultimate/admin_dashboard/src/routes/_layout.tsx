import { createFileRoute, Outlet, Link, useLoaderData } from '@tanstack/react-router';
import { LayoutDashboard, Users, BarChart3, Shield, Settings, Lock, LogOut, User, Menu, X, Sun, Moon, Monitor, Bell, ChevronDown, ChevronRight } from 'lucide-react';
import { useAuth } from '@/components/providers/AuthProvider';
import { useSidebar } from '@/components/providers/SidebarProvider';
import { useTheme } from '@/components/providers/ThemeProvider';
import { useTranslation } from 'react-i18next';
import { cn } from '@/utils/cn';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Badge } from '@/components/ui/badge';

const navItems = [
  { path: '/dashboard', label: 'navigation.dashboard', icon: LayoutDashboard, roles: ['SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT', 'VIEWER'] },
  { path: '/users', label: 'navigation.users', icon: Users, roles: ['SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT'] },
  { path: '/analytics', label: 'navigation.analytics', icon: BarChart3, roles: ['SUPER_ADMIN', 'ADMIN', 'ANALYST'] },
  { path: '/moderation', label: 'navigation.moderation', icon: Shield, roles: ['SUPER_ADMIN', 'ADMIN', 'MODERATOR'] },
  { path: '/system', label: 'navigation.system', icon: Settings, roles: ['SUPER_ADMIN', 'ADMIN'] },
  { path: '/security', label: 'navigation.security', icon: Lock, roles: ['SUPER_ADMIN', 'ADMIN', 'SECURITY'] },
];

export const Route = createFileRoute('/_layout')({
  component: () => {
    const { user, isAuthenticated, logout } = useAuth();
    const { isOpen, isCollapsed, toggleSidebar, setSidebarOpen, toggleCollapse } = useSidebar();
    const { theme, resolvedTheme, setTheme } = useTheme();
    const { t, i18n } = useTranslation();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

    const filteredNavItems = navItems.filter((item) =>
      !item.roles || !user?.role || item.roles.includes(user.role)
    );

    const handleLogout = () => {
      logout();
      setMobileMenuOpen(false);
    };

    const toggleTheme = () => {
      const themes: ('light' | 'dark' | 'system')[] = ['light', 'dark', 'system'];
      const currentIndex = themes.indexOf(theme);
      const nextTheme = themes[(currentIndex + 1) % themes.length];
      setTheme(nextTheme);
    };

    const themeIcons = {
      light: <Sun className="h-4 w-4" />,
      dark: <Moon className="h-4 w-4" />,
      system: <Monitor className="h-4 w-4" />,
    };

    return (
      <TooltipProvider>
        <div className={cn('min-h-screen transition-colors', 'bg-background')}>
          <aside
            className={cn(
              'fixed left-0 top-0 z-40 h-full border-r border-border bg-card transition-all duration-300',
              isCollapsed ? 'w-16' : 'w-64',
              !isOpen && 'translate-x-[-100%] lg:translate-x-0',
              mobileMenuOpen && 'translate-x-0'
            )}
            aria-label="Sidebar"
          >
            <div className="flex h-full flex-col">
              <div className="flex h-16 items-center justify-between border-b border-border px-4">
                {!isCollapsed && (
                  <Link to="/dashboard" className="flex items-center gap-2 font-bold text-xl text-primary">
                    <span className="text-primary">ي</span>
                    <span>UNES</span>
                  </Link>
                )}
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  onClick={toggleCollapse}
                  aria-label={isCollapsed ? t('common.expand') : t('common.collapse')}
                >
                  {isCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </Button>
              </div>

              <ScrollArea className="flex-1 py-4">
                <nav className={cn('px-2', isCollapsed && 'px-1')} aria-label="Main navigation">
                  <ul className="space-y-1" role="list">
                    {filteredNavItems.map((item) => (
                      <li key={item.path}>
                        <Link
                          to={item.path}
                          className={cn(
                            'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                            'hover:bg-accent hover:text-accent-foreground',
                            'focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2'
                          )}
                          onClick={() => setMobileMenuOpen(false)}
                        >
                          <item.icon className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                          {!isCollapsed && <span>{t(item.label)}</span>}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </nav>
              </ScrollArea>

              <div className="border-t border-border p-4">
                {!isCollapsed && user && (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" className="w-full justify-start gap-3 h-10 px-3">
                        <Avatar className="h-8 w-8">
                          <AvatarImage src={user.avatarUrl} alt={user.displayName || user.username} />
                          <AvatarFallback>{(user.displayName || user.username).charAt(0).toUpperCase()}</AvatarFallback>
                        </Avatar>
                        <div className="text-left flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{user.displayName || user.username}</p>
                          <p className="text-xs text-muted-foreground truncate">@{user.username}</p>
                        </div>
                        <ChevronDown className="h-4 w-4 text-muted-foreground" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent className="w-56" align="end" forceMount>
                      <DropdownMenuLabel className="font-normal">{t('navigation.profile')}</DropdownMenuLabel>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem asChild>
                        <Link to="/profile" className="flex items-center gap-2">
                          <User className="h-4 w-4" />
                          {t('navigation.profile')}
                        </Link>
                      </DropdownMenuItem>
                      <DropdownMenuItem asChild>
                        <Link to="/settings" className="flex items-center gap-2">
                          <Settings className="h-4 w-4" />
                          {t('navigation.settings')}
                        </Link>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem onClick={handleLogout} className="text-destructive focus:text-destructive">
                        <LogOut className="h-4 w-4 mr-2" />
                        {t('navigation.logout')}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
                {isCollapsed && user && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="ghost" size="icon" className="h-8 w-8 mx-auto">
                        <Avatar className="h-8 w-8">
                          <AvatarImage src={user.avatarUrl} alt={user.displayName || user.username} />
                          <AvatarFallback>{(user.displayName || user.username).charAt(0).toUpperCase()}</AvatarFallback>
                        </Avatar>
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="right">{user.displayName || user.username}</TooltipContent>
                  </Tooltip>
                )}
              </div>
            </div>
          </aside>

          <div className={cn('transition-all duration-300', isCollapsed ? 'lg:pl-16' : 'lg:pl-64')}>
            <header className="sticky top-0 z-30 border-b border-border bg-background/95 backdrop-blur-sm">
              <div className="flex h-16 items-center justify-between px-4">
                <div className="flex items-center gap-4">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="lg:hidden"
                    onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                    aria-label="Toggle menu"
                  >
                    <Menu className="h-5 w-5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={toggleSidebar}
                    className="hidden lg:flex"
                    aria-label={isOpen ? t('common.closeSidebar') : t('common.openSidebar')}
                  >
                    {isOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
                  </Button>
                </div>

                <div className="flex items-center gap-2">
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label="Toggle theme">
                        {themeIcons[theme]}
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="bottom" align="center">
                      {t(`theme.${theme}`)}
                    </TooltipContent>
                  </Tooltip>

                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button variant="ghost" size="icon" className="relative">
                        <Bell className="h-5 w-5" />
                        <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-destructive text-xs text-destructive-foreground flex items-center justify-center">3</span>
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent side="bottom" align="center">
                      Notifications
                    </TooltipContent>
                  </Tooltip>
                </div>
              </div>
            </header>

            <main className="p-4 lg:p-6" id="main-content" tabIndex={-1}>
              <Outlet />
            </main>
          </div>

          {mobileMenuOpen && (
            <div
              className="fixed inset-0 z-30 bg-background/50 lg:hidden"
              onClick={() => setMobileMenuOpen(false)}
              aria-hidden="true"
            />
          )}
        </div>
      </TooltipProvider>
    );
  },
});

import { useState } from 'react';