import React, { useEffect, useState } from 'react';
import { Layout, Menu, theme, Tag, Space, Badge, Button, Avatar, Dropdown, Spin, Alert } from 'antd';
import {
  DashboardOutlined,
  SafetyCertificateOutlined,
  MessageOutlined,
  PhoneOutlined,
  VideoCameraOutlined,
  SecurityScanOutlined,
  CloudServerOutlined,
  AlertOutlined,
  LogoutOutlined,
  UserOutlined,
  BellOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import OverviewTab from './tabs/OverviewTab';
import AuthorityTab from './tabs/AuthorityTab';
import DinstarTab from './tabs/DinstarTab';
import MessagingTab from './tabs/MessagingTab';
import SecurityTab from './tabs/SecurityTab';
import MediaTab from './tabs/MediaTab';
import InfrastructureTab from './tabs/InfrastructureTab';
import ModerationTab from './tabs/ModerationTab';
import NotificationsTab from './tabs/NotificationsTab';
import { authStore, getUnreadCount, apiFetch } from '../api';

const { Header, Content, Sider } = Layout;

const MasterLayout: React.FC = () => {
  const [currentTab, setCurrentTab] = useState('1');
  const [collapsed, setCollapsed] = useState(false);
  const [unreadNotifCount, setUnreadNotifCount] = useState(0);
  const [healthStatus, setHealthStatus] = useState<'UP'|'DOWN'|'LOADING'>('LOADING');
  const [backendVersion, setBackendVersion] = useState('');
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  // ─── تحميل حالة الخدمة + إشعارات ───
  useEffect(() => {
    const loadStatus = async () => {
      try {
        const res = await fetch('/health');
        const data = await res.json();
        setHealthStatus(data.status === 'UP' ? 'UP' : 'DOWN');
        setBackendVersion(data.version || '');
      } catch {
        setHealthStatus('DOWN');
      }
      try {
        const data = await getUnreadCount();
        setUnreadNotifCount(data.count || 0);
      } catch {}
    };
    loadStatus();
    const timer = setInterval(loadStatus, 15000);
    return () => clearInterval(timer);
  }, []);

  const handleLogout = () => {
    authStore.clear();
    window.location.reload();
  };

  const userMenuItems = [
    { key: 'logout', icon: <LogoutOutlined />, label: 'تسجيل الخروج', danger: true as const }
  ];

  const menuItems = [
    { key: '1', icon: <DashboardOutlined />, label: 'نظرة عامة' },
    { key: '2', icon: <SafetyCertificateOutlined />, label: 'سلطة المستخدمين' },
    { key: '3', icon: <MessageOutlined />, label: 'مركز الرسائل' },
    { key: '4', icon: <PhoneOutlined />, label: 'DINSTAR PSTN' },
    { key: '5', icon: <VideoCameraOutlined />, label: 'وسائط SFU' },
    { key: '6', icon: <SecurityScanOutlined />, label: 'الأمان السيادي' },
    { key: '7', icon: <CloudServerOutlined />, label: 'البنية التحتية' },
    { key: '8', icon: <AlertOutlined />, label: 'الثقة والسلامة' },
    { key: '9', icon: <BellOutlined />, label: unreadNotifCount > 0 ? `الإشعارات (${unreadNotifCount})` : 'الإشعارات' },
  ];

  const tabContent: Record<string, React.ReactNode> = {
    '1': <OverviewTab />,
    '2': <AuthorityTab />,
    '3': <MessagingTab />,
    '4': <DinstarTab />,
    '5': <MediaTab />,
    '6': <SecurityTab />,
    '7': <InfrastructureTab />,
    '8': <ModerationTab />,
    '9': <NotificationsTab />,
  };

  const statusColor = healthStatus === 'UP' ? '#52c41a' : healthStatus === 'DOWN' ? '#f5222d' : '#faad14';
  const statusText = healthStatus === 'UP' ? 'متصل' : healthStatus === 'DOWN' ? 'غير متصل' : 'جاري التحميل';

  return (
    <Layout style={{ minHeight: '100vh', background: '#000' }}>
      <Sider breakpoint="lg" collapsedWidth="0" collapsed={collapsed} onCollapse={setCollapsed} theme="dark"
        style={{ borderRight: '1px solid #1a1a1a' }}>
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#000' }}>
          <b style={{ color: '#00C896', fontSize: collapsed ? 14 : 18, whiteSpace: 'nowrap' }}>
            {collapsed ? '◆' : '◆ YOUNES MASTER'}
          </b>
        </div>
        {backendVersion && (
          <div style={{ textAlign: 'center', color: '#666', fontSize: 10, padding: '0 8px 8px' }}>
            {backendVersion}
          </div>
        )}
        <Menu theme="dark" mode="inline" selectedKeys={[currentTab]} items={menuItems}
          onClick={({key}) => setCurrentTab(key)} />
      </Sider>
      <Layout>
        <Header style={{ background: '#0a0a0a', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #1a1a1a' }}>
          <Space>
            <Badge status={healthStatus === 'UP' ? 'success' : healthStatus === 'DOWN' ? 'error' : 'warning'} text={<span style={{ color: '#ccc', fontSize: 12 }}>{statusText}</span>} />
            <Tag color="blue">LOCAL MODE</Tag>
            <Tag color="gold">YOUNES ID AUTHORITY</Tag>
          </Space>
          <Dropdown menu={{ items: userMenuItems, onClick: ({key}) => { if (key === 'logout') handleLogout(); } }}
            placement="bottomRight" trigger={['click']}>
            <Space style={{ cursor: 'pointer' }}>
              <Avatar icon={<UserOutlined />} style={{ background: '#00C896' }} />
              <span style={{ color: '#ccc' }}>المسؤول</span>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ margin: '24px 16px', padding: 24, background: '#141414', borderRadius: borderRadiusLG, overflow: 'initial' }}>
          <Alert type="info" showIcon style={{marginBottom:16}} message="واجهة موحدة — تم نقل كل ميزات هذه التبويبات إلى القائمة الرئيسية الجديدة (الموافقات، مركز الأمان، الإشعارات، السجل الحي) — هذه الواجهة للتوافق فقط — البيانات هنا حقيقية وموحدة مع الجديدة" description="استخدم القائمة الجانبية الرئيسية (16 صفحة) للوصول السريع — هذه التبويبات التسعة ستبقى لكنها تعرض نفس البيانات الحقيقية عبر نفس apiFetch" />
          {tabContent[currentTab] || <OverviewTab />}
        </Content>
      </Layout>
    </Layout>
  );
};

export default MasterLayout;
