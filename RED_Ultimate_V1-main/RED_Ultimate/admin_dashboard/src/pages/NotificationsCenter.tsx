import React, { useState } from 'react';
import { Card, Typography, List, Badge, Button, Tabs, Space, Tag, Avatar } from 'antd';
import { BellOutlined, CheckOutlined, InfoCircleOutlined, WarningOutlined, SecurityScanOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;
const { TabPane } = Tabs;

export default function NotificationsCenter() {
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('all');

  const notifications = [
    { id: 1, type: 'security', title: 'New Admin Login', desc: 'Login detected from IP 192.168.1.45', time: '10 mins ago', read: false },
    { id: 2, type: 'system', title: 'Database Backup Completed', desc: 'Automated backup completed successfully', time: '1 hour ago', read: false },
    { id: 3, type: 'alert', title: 'High CPU Usage', desc: 'Media Worker 1 CPU exceeded 85%', time: '2 hours ago', read: true },
    { id: 4, type: 'user', title: 'New User Registration Spike', desc: '100+ users registered in the last hour', time: '3 hours ago', read: true },
    { id: 5, type: 'security', title: 'Failed Login Attempts', desc: 'Multiple failed logins for user admin_2', time: '5 hours ago', read: true },
  ];

  const markAllRead = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 500);
  };

  const getIcon = (type: string) => {
    switch(type) {
      case 'security': return <SecurityScanOutlined style={{ color: '#ef4444' }} />;
      case 'alert': return <WarningOutlined style={{ color: '#f59e0b' }} />;
      case 'system': return <InfoCircleOutlined style={{ color: '#3b82f6' }} />;
      default: return <BellOutlined style={{ color: '#10b981' }} />;
    }
  };

  const filteredNotifs = activeTab === 'all' ? notifications : notifications.filter(n => n.type === activeTab);

  return (
    <div style={{ padding: '24px', maxWidth: '1000px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
            <BellOutlined style={{ color: '#D4B16A' }} /> 
            Notifications Center
          </Title>
          <Text type="secondary">System alerts, security warnings, and platform updates</Text>
        </div>
        <Button icon={<CheckOutlined />} onClick={markAllRead} loading={loading}>
          Mark All as Read
        </Button>
      </div>

      <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <TabPane tab="All" key="all" />
          <TabPane tab="Security" key="security" />
          <TabPane tab="System" key="system" />
          <TabPane tab="Alerts" key="alert" />
        </Tabs>

        <List
          itemLayout="horizontal"
          dataSource={filteredNotifs}
          renderItem={item => (
            <List.Item
              style={{
                padding: '16px 24px',
                background: item.read ? 'transparent' : 'rgba(212, 177, 106, 0.05)',
                borderLeft: item.read ? '4px solid transparent' : '4px solid #D4B16A',
                borderBottom: '1px solid #f0f0f0',
                transition: 'all 0.3s'
              }}
              actions={[
                !item.read && <Button type="text" size="small">Mark Read</Button>
              ]}
            >
              <List.Item.Meta
                avatar={<Avatar size={40} style={{ backgroundColor: '#f3f4f6' }} icon={getIcon(item.type)} />}
                title={
                  <Space>
                    <span style={{ fontWeight: item.read ? 500 : 700, fontSize: 15 }}>{item.title}</span>
                    {!item.read && <Badge status="processing" />}
                  </Space>
                }
                description={
                  <div>
                    <div style={{ color: '#666', marginTop: 4 }}>{item.desc}</div>
                    <div style={{ color: '#aaa', fontSize: 12, marginTop: 8 }}>{item.time}</div>
                  </div>
                }
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
}
