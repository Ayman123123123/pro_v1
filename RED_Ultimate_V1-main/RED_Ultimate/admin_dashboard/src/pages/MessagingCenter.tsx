import React, { useState } from 'react';
import { Card, Typography, Row, Col, Table, Badge, Button, Progress, Statistic, Input, Tag } from 'antd';
import { MessageOutlined, ReloadOutlined, LockOutlined, GlobalOutlined, SearchOutlined } from '@ant-design/icons';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, Legend } from 'recharts';

const { Title, Text } = Typography;

export default function MessagingCenter() {
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');

  const refresh = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 800);
  };

  const activeChannels = [
    { id: 'ch-1', name: 'Global Announcements', type: 'broadcast', activeUsers: 14200, msgsPerSec: 15, encryption: 'E2EE' },
    { id: 'ch-2', name: 'Tech Support', type: 'group', activeUsers: 340, msgsPerSec: 5, encryption: 'E2EE' },
    { id: 'ch-3', name: 'Project Alpha', type: 'private', activeUsers: 45, msgsPerSec: 2, encryption: 'E2EE' },
    { id: 'ch-4', name: 'Marketing Sync', type: 'group', activeUsers: 120, msgsPerSec: 1, encryption: 'E2EE' },
    { id: 'ch-5', name: 'HR Updates', type: 'broadcast', activeUsers: 5000, msgsPerSec: 8, encryption: 'E2EE' },
  ];

  const chartData = [
    { time: '10:00', sent: 4000, delivered: 3950, read: 2400 },
    { time: '10:05', sent: 3000, delivered: 2980, read: 1398 },
    { time: '10:10', sent: 2000, delivered: 1990, read: 9800 },
    { time: '10:15', sent: 2780, delivered: 2750, read: 3908 },
    { time: '10:20', sent: 1890, delivered: 1880, read: 4800 },
    { time: '10:25', sent: 2390, delivered: 2380, read: 3800 },
    { time: '10:30', sent: 3490, delivered: 3450, read: 4300 },
  ];

  const columns = [
    {
      title: 'Channel Name',
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => <strong style={{ color: '#D4B16A' }}>{text}</strong>
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const color = type === 'broadcast' ? 'magenta' : type === 'group' ? 'geekblue' : 'cyan';
        return <Tag color={color}>{type.toUpperCase()}</Tag>;
      }
    },
    {
      title: 'Active Users',
      dataIndex: 'activeUsers',
      key: 'activeUsers',
      render: (val: number) => val.toLocaleString()
    },
    {
      title: 'Traffic (msg/sec)',
      dataIndex: 'msgsPerSec',
      key: 'msgsPerSec',
      render: (val: number) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Progress percent={val * 5} showInfo={false} size="small" strokeColor="#3b82f6" style={{ width: 60 }} />
          <span>{val}</span>
        </div>
      )
    },
    {
      title: 'Encryption',
      dataIndex: 'encryption',
      key: 'encryption',
      render: (val: string) => <Tag icon={<LockOutlined />} color="green">{val}</Tag>
    }
  ];

  const filteredChannels = activeChannels.filter(c => c.name.toLowerCase().includes(searchText.toLowerCase()));

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
            <MessageOutlined style={{ color: '#D4B16A' }} /> 
            Messaging Center
          </Title>
          <Text type="secondary">Real-time encrypted message routing and delivery stats</Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
          Refresh Stats
        </Button>
      </div>

      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Messages Sent (24h)" value={15420390} prefix={<MessageOutlined />} valueStyle={{ color: '#3b82f6' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Delivery Rate" value={99.8} suffix="%" valueStyle={{ color: '#10b981' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Active Channels" value={1250} prefix={<GlobalOutlined />} valueStyle={{ color: '#8b5cf6' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Encrypted Sessions" value={893400} prefix={<LockOutlined />} valueStyle={{ color: '#D4B16A' }} />
          </Card>
        </Col>
      </Row>

      <Card title="Message Delivery Pipeline" bordered={false} style={{ marginBottom: 24, borderRadius: 12 }}>
        <div style={{ height: 300 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
              <XAxis dataKey="time" />
              <YAxis />
              <RechartsTooltip />
              <Legend />
              <Line type="monotone" dataKey="sent" stroke="#3b82f6" strokeWidth={2} name="Sent" />
              <Line type="monotone" dataKey="delivered" stroke="#10b981" strokeWidth={2} name="Delivered" />
              <Line type="monotone" dataKey="read" stroke="#D4B16A" strokeWidth={2} name="Read" />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card bordered={false} style={{ borderRadius: 12 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>Top Active Channels</Title>
          <Input 
            placeholder="Search channels..." 
            prefix={<SearchOutlined />} 
            style={{ width: 300, borderRadius: 8 }} 
            onChange={(e) => setSearchText(e.target.value)}
          />
        </div>
        <Table 
          columns={columns} 
          dataSource={filteredChannels} 
          rowKey="id" 
          pagination={false}
          loading={loading}
        />
      </Card>
    </div>
  );
}
