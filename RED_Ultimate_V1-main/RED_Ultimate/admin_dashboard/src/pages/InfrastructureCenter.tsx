import React, { useState } from 'react';
import { Card, Typography, Row, Col, Table, Badge, Button, Space, Progress, Statistic, Input, Tag } from 'antd';
import { CloudServerOutlined, ReloadOutlined, CheckCircleOutlined, WarningOutlined, SearchOutlined } from '@ant-design/icons';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer } from 'recharts';

const { Title, Text } = Typography;

export default function InfrastructureCenter() {
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');

  const refresh = () => {
    setLoading(true);
    setTimeout(() => setLoading(false), 800);
  };

  const chartData = Array.from({ length: 20 }).map((_, i) => ({
    time: `10:${i < 10 ? '0'+i : i}`,
    cpu: 30 + Math.random() * 40,
    ram: 50 + Math.random() * 20
  }));

  const servers = [
    { id: 'srv-master', name: 'Master Node', role: 'API / Auth', ip: '192.168.1.10', status: 'healthy', cpu: 45, ram: 65, uptime: '45d 12h' },
    { id: 'srv-db-1', name: 'Database Primary', role: 'PostgreSQL', ip: '192.168.1.11', status: 'healthy', cpu: 25, ram: 80, uptime: '90d 5h' },
    { id: 'srv-redis', name: 'Redis Cache', role: 'Cache', ip: '192.168.1.12', status: 'healthy', cpu: 15, ram: 40, uptime: '45d 12h' },
    { id: 'srv-media-1', name: 'Media Worker 1', role: 'WebRTC SFU', ip: '192.168.1.15', status: 'warning', cpu: 85, ram: 70, uptime: '12d 3h' },
    { id: 'srv-storage', name: 'S3 MinIO', role: 'Object Storage', ip: '192.168.1.20', status: 'healthy', cpu: 10, ram: 35, uptime: '120d 1h' },
  ];

  const columns = [
    {
      title: 'Server Name',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: any) => (
        <div>
          <div style={{ fontWeight: 600 }}>{text}</div>
          <div style={{ fontSize: '12px', color: '#888' }}>{record.ip}</div>
        </div>
      )
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => <Tag color="blue">{role}</Tag>
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge 
          status={status === 'healthy' ? 'success' : status === 'warning' ? 'warning' : 'error'} 
          text={status.toUpperCase()} 
        />
      )
    },
    {
      title: 'CPU Usage',
      dataIndex: 'cpu',
      key: 'cpu',
      render: (cpu: number) => (
        <div style={{ width: 120 }}>
          <Progress percent={cpu} size="small" status={cpu > 80 ? 'exception' : 'normal'} />
        </div>
      )
    },
    {
      title: 'RAM Usage',
      dataIndex: 'ram',
      key: 'ram',
      render: (ram: number) => (
        <div style={{ width: 120 }}>
          <Progress percent={ram} size="small" strokeColor="#D4B16A" />
        </div>
      )
    },
    {
      title: 'Uptime',
      dataIndex: 'uptime',
      key: 'uptime',
    }
  ];

  const filteredServers = servers.filter(s => s.name.toLowerCase().includes(searchText.toLowerCase()) || s.role.toLowerCase().includes(searchText.toLowerCase()));

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
            <CloudServerOutlined style={{ color: '#D4B16A' }} /> 
            Infrastructure Center
          </Title>
          <Text type="secondary">Real-time cluster monitoring and node management</Text>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
          Refresh Cluster State
        </Button>
      </div>

      <Row gutter={[24, 24]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
            <Statistic title="Total Nodes" value={5} prefix={<CloudServerOutlined />} valueStyle={{ color: '#10b981' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
            <Statistic title="Global CPU" value={36} suffix="%" valueStyle={{ color: '#3b82f6' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
            <Statistic title="Global RAM" value={58} suffix="%" valueStyle={{ color: '#D4B16A' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
            <Statistic title="Cluster Health" value="Warning" prefix={<WarningOutlined />} valueStyle={{ color: '#f59e0b' }} />
          </Card>
        </Col>
      </Row>

      <Card title="Cluster Resource Utilization" bordered={false} style={{ marginBottom: 24, borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        <div style={{ height: 300 }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="colorCpu" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                </linearGradient>
                <linearGradient id="colorRam" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#D4B16A" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#D4B16A" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} opacity={0.3} />
              <XAxis dataKey="time" />
              <YAxis />
              <RechartsTooltip />
              <Area type="monotone" dataKey="cpu" stroke="#3b82f6" fillOpacity={1} fill="url(#colorCpu)" name="CPU %" />
              <Area type="monotone" dataKey="ram" stroke="#D4B16A" fillOpacity={1} fill="url(#colorRam)" name="RAM %" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>Active Nodes</Title>
          <Input 
            placeholder="Search nodes..." 
            prefix={<SearchOutlined />} 
            style={{ width: 300, borderRadius: 8 }} 
            onChange={(e) => setSearchText(e.target.value)}
          />
        </div>
        <Table 
          columns={columns} 
          dataSource={filteredServers} 
          rowKey="id" 
          pagination={false}
          loading={loading}
        />
      </Card>
    </div>
  );
}
