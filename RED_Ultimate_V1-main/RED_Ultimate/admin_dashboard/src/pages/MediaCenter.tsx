import React, { useState } from 'react';
import { Card, Typography, Row, Col, Table, Badge, Button, Progress, Statistic, Input, Tag, Space, Avatar } from 'antd';
import { VideoCameraOutlined, AudioOutlined, ReloadOutlined, DesktopOutlined, SearchOutlined } from '@ant-design/icons';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, Legend } from 'recharts';

const { Title, Text } = Typography;

export default function MediaCenter() {
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');

  const refresh = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 800);
  };

  const activeCalls = [
    { id: 'call-1', room: 'Project Alpha Sync', type: 'video', participants: 12, bandwidth: '4.2 Mbps', duration: '45m 12s', quality: 98 },
    { id: 'call-2', room: 'Marketing Weekly', type: 'audio', participants: 4, bandwidth: '128 kbps', duration: '1h 15m', quality: 100 },
    { id: 'call-3', room: 'Client Presentation', type: 'screenshare', participants: 25, bandwidth: '8.5 Mbps', duration: '12m 05s', quality: 85 },
    { id: 'call-4', room: '1-on-1: Tech Lead', type: 'video', participants: 2, bandwidth: '1.2 Mbps', duration: '5m 30s', quality: 95 },
    { id: 'call-5', room: 'Design Review', type: 'video', participants: 6, bandwidth: '2.8 Mbps', duration: '55m 10s', quality: 92 },
  ];

  const chartData = [
    { name: '08:00', video: 4000, audio: 2400 },
    { name: '10:00', video: 3000, audio: 1398 },
    { name: '12:00', video: 2000, audio: 9800 },
    { name: '14:00', video: 2780, audio: 3908 },
    { name: '16:00', video: 1890, audio: 4800 },
    { name: '18:00', video: 2390, audio: 3800 },
    { name: '20:00', video: 3490, audio: 4300 },
  ];

  const columns = [
    {
      title: 'Room Name',
      dataIndex: 'room',
      key: 'room',
      render: (text: string) => <strong style={{ color: '#D4B16A' }}>{text}</strong>
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const icon = type === 'video' ? <VideoCameraOutlined /> : type === 'audio' ? <AudioOutlined /> : <DesktopOutlined />;
        const color = type === 'video' ? 'blue' : type === 'audio' ? 'green' : 'purple';
        return <Tag icon={icon} color={color}>{type.toUpperCase()}</Tag>;
      }
    },
    {
      title: 'Participants',
      dataIndex: 'participants',
      key: 'participants',
      render: (p: number) => <Badge count={p} style={{ backgroundColor: '#52c41a' }} />
    },
    {
      title: 'Bandwidth',
      dataIndex: 'bandwidth',
      key: 'bandwidth',
    },
    {
      title: 'Duration',
      dataIndex: 'duration',
      key: 'duration',
    },
    {
      title: 'Connection Quality',
      dataIndex: 'quality',
      key: 'quality',
      render: (q: number) => (
        <Progress percent={q} size="small" status={q > 90 ? 'success' : q > 70 ? 'normal' : 'exception'} />
      )
    },
    {
      title: 'Action',
      key: 'action',
      render: () => (
        <Button size="small" danger>End Call</Button>
      )
    }
  ];

  const filteredCalls = activeCalls.filter(c => c.room.toLowerCase().includes(searchText.toLowerCase()));

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
            <VideoCameraOutlined style={{ color: '#D4B16A' }} /> 
            Media Center
          </Title>
          <Text type="secondary">WebRTC SFU active sessions and bandwidth usage</Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
          Refresh Sessions
        </Button>
      </div>

      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Active Calls" value={145} prefix={<AudioOutlined />} valueStyle={{ color: '#10b981' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Total Participants" value={892} prefix={<Badge status="processing" />} valueStyle={{ color: '#3b82f6' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Current Bandwidth" value={1.2} suffix="Gbps" valueStyle={{ color: '#8b5cf6' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12 }}>
            <Statistic title="Average Quality" value={94} suffix="%" valueStyle={{ color: '#D4B16A' }} />
          </Card>
        </Col>
      </Row>

      <Card title="Bandwidth Usage (MB/s)" bordered={false} style={{ marginBottom: 24, borderRadius: 12 }}>
        <div style={{ height: 300 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
              <XAxis dataKey="name" />
              <YAxis />
              <RechartsTooltip />
              <Legend />
              <Bar dataKey="video" stackId="a" fill="#3b82f6" name="Video Data" />
              <Bar dataKey="audio" stackId="a" fill="#10b981" name="Audio Data" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card bordered={false} style={{ borderRadius: 12 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>Active Media Sessions</Title>
          <Input 
            placeholder="Search rooms..." 
            prefix={<SearchOutlined />} 
            style={{ width: 300, borderRadius: 8 }} 
            onChange={(e) => setSearchText(e.target.value)}
          />
        </div>
        <Table 
          columns={columns} 
          dataSource={filteredCalls} 
          rowKey="id" 
          pagination={false}
          loading={loading}
        />
      </Card>
    </div>
  );
}
