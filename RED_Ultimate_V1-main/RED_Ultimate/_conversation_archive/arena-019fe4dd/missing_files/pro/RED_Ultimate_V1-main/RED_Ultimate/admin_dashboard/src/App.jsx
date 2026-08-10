import React, { lazy, Suspense, useEffect, useState } from 'react';
import { Button, ConfigProvider, Layout, Menu, Spin, theme } from 'antd';
import { authStore } from './api';
import Login from './pages/Login';
import {
    DashboardOutlined,
    TeamOutlined,
    SettingOutlined,
    MonitorOutlined,
    MobileOutlined,
    SafetyOutlined
} from '@ant-design/icons';
const Dashboard = lazy(() => import('./pages/Dashboard'));
const MasterOverview = lazy(() => import('./pages/MasterOverview'));
const UserManagement = lazy(() => import('./pages/UserManagement'));
const MasterLayout = lazy(() => import('./pages/MasterLayout'));
const DinstarControl = lazy(() => import('./pages/DinstarControl'));
const Diagnostics = lazy(() => import('./pages/Diagnostics'));

const { Header, Sider, Content } = Layout;

const menuItems = [
    { key: 'dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
    { key: 'master', icon: <SafetyOutlined />, label: 'Master Control' },
    { key: 'users', icon: <TeamOutlined />, label: 'User Management' },
    { key: 'dinstar', icon: <MobileOutlined />, label: 'DINSTAR Control' },
    { key: 'monitor', icon: <MonitorOutlined />, label: 'Live Monitor' },
    { key: 'diagnostics', icon: <SettingOutlined />, label: 'Diagnostics' },
];

function App() {
    const [authenticated, setAuthenticated] = useState(Boolean(authStore.access() || authStore.refresh()));
    const [currentPage, setCurrentPage] = useState('dashboard');

    useEffect(() => {
        const expire = () => setAuthenticated(false);
        window.addEventListener('younes:auth-expired', expire);
        return () => window.removeEventListener('younes:auth-expired', expire);
    }, []);

    if (!authenticated) return <Login onSuccess={() => setAuthenticated(true)} />;

    const logout = () => { authStore.clear(); setAuthenticated(false); };

    const renderPage = () => {
        switch (currentPage) {
            case 'dashboard': return <Dashboard />;
            case 'master': return <MasterLayout />;
            case 'users': return <UserManagement />;
            case 'dinstar': return <DinstarControl />;
            case 'monitor': return <MasterOverview />;
            case 'diagnostics': return <Diagnostics />;
            default: return <Dashboard />;
        }
    };

    return (
      <ConfigProvider direction="rtl" theme={{ algorithm: theme.darkAlgorithm, token: { fontFamily: 'Cairo, Tajawal, Segoe UI, Tahoma, Arial, sans-serif', colorPrimary: '#00C896', colorInfo: '#35CBE0', colorSuccess: '#00C896', colorWarning: '#E8B84A', colorError: '#F43F5E', colorBgBase: '#050A16', colorBgContainer: '#0D1829', colorBorder: '#1D3850', borderRadius: 14 }, components: { Card: { colorBgContainer: '#101E2E' }, Menu: { darkItemBg: '#08111F', darkItemSelectedBg: '#0B3F38', darkItemHoverBg: '#10283A' }, Layout: { siderBg: '#08111F', headerBg: '#081525' } } }}>
        <Layout className="admin-shell">
            <Sider className="admin-sider" theme="dark" collapsible breakpoint="lg" collapsedWidth="0">
                <div className="admin-brand">
                    ◆ يونس — الإدارة
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[currentPage]}
                    items={menuItems}
                    onClick={({ key }) => setCurrentPage(key)}
                />
            </Sider>
            <Layout>
                <Header className="admin-header" style={{ color: '#F1F7FA', borderBottom: '1px solid #17344A', padding: '0 24px', fontSize: 16, fontWeight: 'bold' }}>
                    <span style={{color:'#E8B84A'}}>يونس السيادي — لوحة الإدارة</span>
                    <Button danger onClick={logout}>تسجيل الخروج</Button>
                </Header>
                <Content className="admin-content admin-page-surface" style={{ margin: 16, padding: 24, background: '#07111F', border: '1px solid #132B40', borderRadius: 18 }}>
                    <Suspense fallback={<div style={{display:'grid',placeItems:'center',minHeight:320}}><Spin size="large" /></div>}>
                        {renderPage()}
                    </Suspense>
                </Content>
            </Layout>
        </Layout>
      </ConfigProvider>
    );
}

export default App;
