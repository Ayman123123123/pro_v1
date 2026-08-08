import React, { useState, useEffect } from 'react';
import { Layout, Menu, theme, Tag, Space, Badge, Button, Avatar, Dropdown } from 'antd';
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
  UserOutlined
} from '@ant-design/icons';
import OverviewTab from './tabs/OverviewTab';
import AuthorityTab from './tabs/AuthorityTab';
import DinstarTab from './tabs/DinstarTab';
import MessagingTab from './tabs/MessagingTab';
import SecurityTab from './tabs/SecurityTab';
import MediaTab from './tabs/MediaTab';
import InfrastructureTab from './tabs/InfrastructureTab';
import ModerationTab from './tabs/ModerationTab';
import { authStore } from '../api';

const { Header, Content, Sider } = Layout;

const MasterLayout: React.FC = () => {
  const [currentTab, setCurrentTab] = useState('1');
  const [collapsed, setCollapsed] = useState(false);
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

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
  };

  return (
    <Layout style={{ minHeight: '100vh', background: '#000' }}>
      <Sider breakpoint="lg" collapsedWidth="0" collapsed={collapsed} onCollapse={setCollapsed} theme="dark"
        style={{ borderRight: '1px solid #1a1a1a' }}>
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#000' }}>
          <b style={{ color: '#00C896', fontSize: collapsed ? 14 : 18, whiteSpace: 'nowrap' }}>
            {collapsed ? '◆' : '◆ YOUNES MASTER'}
          </b>
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[currentTab]} items={menuItems}
          onClick={({key}) => setCurrentTab(key)} />
      </Sider>
      <Layout>
        <Header style={{ background: '#0a0a0a', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #1a1a1a' }}>
          <Space>
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
          {tabContent[currentTab] || <OverviewTab />}
        </Content>
      </Layout>
    </Layout>
  );
};

export default MasterLayout;
