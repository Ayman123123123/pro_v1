import { lazy, Suspense, useEffect, useState } from 'react';
import { Button, ConfigProvider, Layout, Menu, Spin, theme } from 'antd';
import {
  DashboardOutlined,
  MobileOutlined,
  MonitorOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  AlertOutlined,
  AuditOutlined,
  CloudUploadOutlined,
  ExperimentOutlined,
  FlagOutlined,
  NotificationOutlined,
} from '@ant-design/icons';
import { adminLogin, adminLogout, authStore } from './api';
import Login from './pages/Login';
import './styles.css';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const MasterOverview = lazy(() => import('./pages/MasterOverview'));
const UserManagement = lazy(() => import('./pages/UserManagement'));
const MasterLayout = lazy(() => import('./pages/MasterLayout'));
const DinstarControl = lazy(() => import('./pages/DinstarControl'));
const Diagnostics = lazy(() => import('./pages/Diagnostics'));
const Reports = lazy(() => import('./pages/Reports'));
const AuditLog = lazy(() => import('./pages/AuditLog'));
const Backups = lazy(() => import('./pages/Backups'));
const Announcements = lazy(() => import('./pages/Announcements'));
const FeatureFlags = lazy(() => import('./pages/FeatureFlags'));

const { Header, Sider, Content } = Layout;

type PageKey =
  | 'dashboard'
  | 'users'
  | 'reports'
  | 'audit'
  | 'announcements'
  | 'featureflags'
  | 'backups'
  | 'master'
  | 'dinstar'
  | 'monitor'
  | 'diagnostics';

const menuItems: { key: PageKey; icon: JSX.Element; label: string; group: string }[] = [
  // Operations
  { key: 'dashboard', icon: <DashboardOutlined />, label: 'الرئيسية', group: 'main' },
  { key: 'users', icon: <TeamOutlined />, label: 'المستخدمون', group: 'main' },
  { key: 'reports', icon: <AlertOutlined />, label: 'مراقبة المحتوى', group: 'main' },
  { key: 'audit', icon: <AuditOutlined />, label: 'سجل التدقيق', group: 'main' },
  // System
  { key: 'announcements', icon: <NotificationOutlined />, label: 'الإعلانات', group: 'system' },
  { key: 'featureflags', icon: <ExperimentOutlined />, label: 'أعلام الميزات', group: 'system' },
  { key: 'backups', icon: <CloudUploadOutlined />, label: 'النسخ الاحتياطية', group: 'system' },
  // Sovereign
  { key: 'master', icon: <SafetyOutlined />, label: 'التحكم السيادي', group: 'sovereign' },
  { key: 'dinstar', icon: <MobileOutlined />, label: 'بوابات DINSTAR', group: 'sovereign' },
  { key: 'monitor', icon: <MonitorOutlined />, label: 'المراقبة الحية', group: 'sovereign' },
  { key: 'diagnostics', icon: <SettingOutlined />, label: 'التشخيص', group: 'sovereign' },
];

const groupLabels: Record<string, string> = {
  main: 'العمليات',
  system: 'النظام',
  sovereign: 'السيادي',
};

/**
 * Real administrative shell. Authentication is delegated to /api/auth/login via adminLogin(),
 * and every feature page remains reachable after the TypeScript UI migration.
 */
export default function App() {
  const [authenticated, setAuthenticated] = useState(() => Boolean(authStore.access() || authStore.refresh()));
  const [currentPage, setCurrentPage] = useState<PageKey>('dashboard');
  const [loginLoading, setLoginLoading] = useState(false);

  useEffect(() => {
    const onExpired = () => setAuthenticated(false);
    window.addEventListener('younes:auth-expired', onExpired);
    return () => window.removeEventListener('younes:auth-expired', onExpired);
  }, []);

  const login = async (username: string, password: string) => {
    setLoginLoading(true);
    try {
      await adminLogin(username, password);
      setAuthenticated(true);
    } finally {
      setLoginLoading(false);
    }
  };

  const logout = async () => {
    await adminLogout();
    setAuthenticated(false);
    setCurrentPage('dashboard');
  };

  if (!authenticated) {
    return <Login onLogin={login} isLoading={loginLoading} />;
  }

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard': return <Dashboard />;
      case 'users': return <UserManagement />;
      case 'reports': return <Reports />;
      case 'audit': return <AuditLog />;
      case 'announcements': return <Announcements />;
      case 'featureflags': return <FeatureFlags />;
      case 'backups': return <Backups />;
      case 'master': return <MasterLayout />;
      case 'dinstar': return <DinstarControl />;
      case 'monitor': return <MasterOverview />;
      case 'diagnostics': return <Diagnostics />;
    }
  };

  // Group menu items
  const groupedMenu = ['main', 'system', 'sovereign'].map(group => ({
    key: group,
    type: 'group' as const,
    label: groupLabels[group],
    children: menuItems.filter(m => m.group === group).map(m => ({
      key: m.key,
      icon: m.icon,
      label: m.label,
    })),
  }));

  return (
    <ConfigProvider
      direction="rtl"
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#00C896',
          colorInfo: '#35CBE0',
          colorWarning: '#E8B84A',
          colorBgBase: '#050A16',
          borderRadius: 14,
        },
      }}
    >
      <Layout style={{ minHeight: '100vh', background: '#050A16' }}>
        <Sider theme="dark" collapsible width={240}>
          <div style={{
            height: 64, padding: 16, color: '#fff', textAlign: 'center',
            borderBottom: '1px solid #1A2F4A', marginBottom: 8
          }}>
            <strong style={{ fontSize: 16 }}>يونس ماستر</strong>
            <div style={{ color: '#8A9FB2', fontSize: 11, marginTop: 2 }}>
              الإدارة السيادية
            </div>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[currentPage]}
            items={groupedMenu}
            onClick={({ key }) => setCurrentPage(key as PageKey)}
            style={{ borderRight: 0 }}
          />
        </Sider>
        <Layout>
          <Header style={{
            background: '#081525', color: '#F1F7FA',
            borderBottom: '1px solid #17344A', padding: '0 20px',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center'
          }}>
            <span style={{ color: '#E8B84A', fontSize: 16, fontWeight: 'bold' }}>
              {menuItems.find(m => m.key === currentPage)?.label || 'يونس'}
            </span>
            <Button danger onClick={logout}>تسجيل الخروج</Button>
          </Header>
          <Content style={{
            margin: 16, padding: 24, background: '#07111F',
            border: '1px solid #132B40', borderRadius: 18, overflow: 'auto'
          }}>
            <Suspense fallback={
              <div style={{ display: 'grid', placeItems: 'center', minHeight: 320 }}>
                <Spin size="large" tip="جاري التحميل..." />
              </div>
            }>
              {renderPage()}
            </Suspense>
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
