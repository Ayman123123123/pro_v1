import { lazy, Suspense, useEffect, useState } from 'react';
import { Button, ConfigProvider, Layout, Menu, Spin, theme } from 'antd';
import {
  DashboardOutlined,
  MobileOutlined,
  MonitorOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { adminLogin, authStore } from './api';
import Login from './pages/Login';
import './styles.css';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const MasterOverview = lazy(() => import('./pages/MasterOverview'));
const UserManagement = lazy(() => import('./pages/UserManagement'));
const MasterLayout = lazy(() => import('./pages/MasterLayout'));
const DinstarControl = lazy(() => import('./pages/DinstarControl'));
const Diagnostics = lazy(() => import('./pages/Diagnostics'));

const { Header, Sider, Content } = Layout;

type PageKey = 'dashboard' | 'master' | 'users' | 'dinstar' | 'monitor' | 'diagnostics';

const menuItems = [
  { key: 'dashboard', icon: <DashboardOutlined />, label: 'الرئيسية' },
  { key: 'master', icon: <SafetyOutlined />, label: 'التحكم السيادي' },
  { key: 'users', icon: <TeamOutlined />, label: 'المستخدمون والصلاحيات' },
  { key: 'dinstar', icon: <MobileOutlined />, label: 'بوابات DINSTAR' },
  { key: 'monitor', icon: <MonitorOutlined />, label: 'المراقبة الحية' },
  { key: 'diagnostics', icon: <SettingOutlined />, label: 'التشخيص' },
];

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

  const logout = () => {
    authStore.clear();
    setAuthenticated(false);
    setCurrentPage('dashboard');
  };

  if (!authenticated) {
    return <Login onLogin={login} isLoading={loginLoading} />;
  }

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard': return <Dashboard />;
      case 'master': return <MasterLayout />;
      case 'users': return <UserManagement />;
      case 'dinstar': return <DinstarControl />;
      case 'monitor': return <MasterOverview />;
      case 'diagnostics': return <Diagnostics />;
    }
  };

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
        <Sider theme="dark" collapsible>
          <div style={{ height: 42, margin: 16, color: '#fff', fontSize: 16, textAlign: 'center', lineHeight: '20px', paddingTop: 2 }}>
            <strong>يونس ماستر</strong><br />
            <span style={{ color: '#8A9FB2', fontSize: 11 }}>الإدارة السيادية المحلية</span>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[currentPage]}
            items={menuItems}
            onClick={({ key }) => setCurrentPage(key as PageKey)}
          />
        </Sider>
        <Layout>
          <Header style={{ background: '#081525', color: '#F1F7FA', borderBottom: '1px solid #17344A', padding: '0 20px', fontSize: 16, fontWeight: 'bold', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#E8B84A' }}>يونس السيادي — لوحة الإدارة</span>
            <Button danger onClick={logout}>تسجيل الخروج</Button>
          </Header>
          <Content style={{ margin: 16, padding: 24, background: '#07111F', border: '1px solid #132B40', borderRadius: 18 }}>
            <Suspense fallback={<div style={{ display: 'grid', placeItems: 'center', minHeight: 320 }}><Spin size="large" /></div>}>
              {renderPage()}
            </Suspense>
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
