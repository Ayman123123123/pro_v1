import {
  createRootRoute,
  Outlet,
  Link,
  useLoaderData,
  Scripts,
} from '@tanstack/react-router';
import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import { Toaster } from '@/components/ui/Toaster';
import { CommandPalette } from '@/components/ui/CommandPalette';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { AuthProvider } from '@/components/providers/AuthProvider';
import { SocketProvider } from '@/components/providers/SocketProvider';
import { SidebarProvider } from '@/components/providers/SidebarProvider';
import i18n from '@/i18n';
import { getAuthLoader } from '@/api/auth';
import '@/styles/globals.css';

interface RootLoaderData {
  user: {
    id: string;
    username: string;
    displayName?: string;
    role: string;
    redId?: string;
  } | null;
}

export const Route = createRootRoute<RootLoaderData>()({
  loader: async () => {
    const auth = await getAuthLoader();
    return { user: auth };
  },
  component: () => (
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <ThemeProvider>
          <AuthProvider>
            <SocketProvider>
              <SidebarProvider>
                <div className="min-h-screen bg-background font-sans antialiased">
                  <Outlet />
                  <Toaster />
                  <CommandPalette />
                  <Scripts />
                </div>
              </SidebarProvider>
            </SocketProvider>
          </AuthProvider>
        </ThemeProvider>
      </I18nextProvider>
    </QueryClientProvider>
  ),
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

declare module '@tanstack/react-router' {
  interface Register {
    rootRoute: typeof Route;
  }
}