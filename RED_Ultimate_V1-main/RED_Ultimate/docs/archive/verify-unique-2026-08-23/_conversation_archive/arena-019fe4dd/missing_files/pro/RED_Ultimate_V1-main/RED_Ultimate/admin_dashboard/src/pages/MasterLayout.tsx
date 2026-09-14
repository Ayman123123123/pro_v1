import React, { useState } from 'react';
import { Layout, Menu, theme, Tag, Space, Badge } from 'antd';
import {
  DashboardOutlined,
  SafetyCertificateOutlined,
  MessageOutlined,
  PhoneOutlined,
  VideoCameraOutlined,
  SecurityScanOutlined,
  CloudServerOutlined,
  AlertOutlined
} from '@ant-design/icons';
import OverviewTab from './tabs/OverviewTab';
import AuthorityTab from './tabs/AuthorityTab';
import DinstarTab from './tabs/DinstarTab';
import MessagingTab from './tabs/MessagingTab';
import SecurityTab from './tabs/SecurityTab';
import MediaTab from './tabs/MediaTab';
import InfrastructureTab from './tabs/InfrastructureTab';
import ModerationTab from './tabs/ModerationTab';

const { Header, Content, Sider } = Layout;

const MasterLayout: React.FC = () => {
  const [currentTab, setCurrentTab] = useState('1');
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  const menuItems = [
    { key: '1', icon: <DashboardOutlined />, label: 'System Overview' },
    { key: '2', icon: <SafetyCertificateOutlined />, label: 'User Authority' },
    { key: '3', icon: <MessageOutlined />, label: 'Messaging Center' },
    { key: '4', icon: <PhoneOutlined />, label: 'Dinstar PSTN' },
    { key: '5', icon: <VideoCameraOutlined />, label: 'Media SFU' },
    { key: '6', icon: <SecurityScanOutlined />, label: 'Sovereign Security' },
    { key: '7', icon: <CloudServerOutlined />, label: 'Infrastructure' },
    { key: '8', icon: <AlertOutlined />, label: 'Trust & Safety' },
  ];

  return (
    <Layout style={{ minHeight: '100vh', background: '#000' }}>
      <Sider breakpoint="lg" collapsedWidth="0" theme="dark">
        <div style={{ height: 64, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#000' }}>
          <b style={{ color: '#00C896', fontSize: 18 }}>◆ YOUNES MASTER</b>
        </div>
        <Menu theme="dark" mode="inline" defaultSelectedKeys={['1']} items={menuItems} onClick={({key}) => setCurrentTab(key)} />
      </Sider>
      <Layout>
        <Header style={{ background: '#0a0a0a', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
             <Tag color="blue">LOCAL MODE</Tag>
             <Tag color="gold">YOUNES ID AUTHORITY</Tag>
          </Space>
          <Badge dot={false}><AlertOutlined style={{ color: '#fff', fontSize: 20 }} /></Badge>
        </Header>
        <Content style={{ margin: '24px 16px', padding: 24, background: '#141414', borderRadius: borderRadiusLG, overflow: 'initial' }}>
          {currentTab === '1' && <OverviewTab />}
          {currentTab === '2' && <AuthorityTab />}
          {currentTab === '3' && <MessagingTab />}
          {currentTab === '4' && <DinstarTab />}
          {currentTab === '5' && <MediaTab />}
          {currentTab === '6' && <SecurityTab />}
          {currentTab === '7' && <InfrastructureTab />}
          {currentTab === '8' && <ModerationTab />}
        </Content>
      </Layout>
    </Layout>
  );
};

export default MasterLayout;
