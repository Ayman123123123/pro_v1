import React, { useState, useEffect } from 'react';
import { Card, Typography, Button, Space, Input, Tag, Select } from 'antd';
import { FileSearchOutlined, PlayCircleOutlined, PauseCircleOutlined, ClearOutlined } from '@ant-design/icons';

const { Title, Text } = Typography;
const { Option } = Select;

export default function SystemLogs() {
  const [logs, setLogs] = useState<any[]>([]);
  const [isStreaming, setIsStreaming] = useState(true);
  const [filterLevel, setFilterLevel] = useState('all');
  const [searchText, setSearchText] = useState('');

  // Simulate incoming logs
  useEffect(() => {
    if (!isStreaming) return;
    
    const levels = ['info', 'info', 'info', 'warn', 'error', 'debug'];
    const components = ['API', 'Auth', 'DB', 'WebRTC', 'Worker'];
    const messages = ['Request processed', 'Connection established', 'Query executed', 'User authenticated', 'Failed to connect', 'Timeout occurred'];

    const interval = setInterval(() => {
      const level = levels[Math.floor(Math.random() * levels.length)];
      const comp = components[Math.floor(Math.random() * components.length)];
      const msg = messages[Math.floor(Math.random() * messages.length)];
      
      const newLog = {
        id: Date.now(),
        time: new Date().toISOString().split('T')[1].split('.')[0],
        level,
        component: comp,
        message: `${msg} [req_id: ${Math.random().toString(36).substring(7)}]`
      };

      setLogs(prev => [newLog, ...prev].slice(0, 100)); // Keep last 100
    }, 1500);

    return () => clearInterval(interval);
  }, [isStreaming]);

  const getLevelColor = (level: string) => {
    switch(level) {
      case 'info': return '#3b82f6';
      case 'warn': return '#f59e0b';
      case 'error': return '#ef4444';
      case 'debug': return '#8b5cf6';
      default: return '#6b7280';
    }
  };

  const filteredLogs = logs.filter(l => 
    (filterLevel === 'all' || l.level === filterLevel) &&
    (l.message.toLowerCase().includes(searchText.toLowerCase()) || l.component.toLowerCase().includes(searchText.toLowerCase()))
  );

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
            <FileSearchOutlined style={{ color: '#D4B16A' }} /> 
            System Logs
          </Title>
          <Text type="secondary">Real-time streaming of system events and errors</Text>
        </div>
        <Space>
          <Button 
            type={isStreaming ? "default" : "primary"} 
            icon={isStreaming ? <PauseCircleOutlined /> : <PlayCircleOutlined />} 
            onClick={() => setIsStreaming(!isStreaming)}
          >
            {isStreaming ? 'Pause Stream' : 'Resume Stream'}
          </Button>
          <Button icon={<ClearOutlined />} onClick={() => setLogs([])}>
            Clear
          </Button>
        </Space>
      </div>

      <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }}>
        <div style={{ marginBottom: 16, display: 'flex', gap: 16 }}>
          <Input.Search 
            placeholder="Search logs..." 
            style={{ width: 300 }} 
            onChange={(e) => setSearchText(e.target.value)}
          />
          <Select defaultValue="all" style={{ width: 150 }} onChange={setFilterLevel}>
            <Option value="all">All Levels</Option>
            <Option value="info">Info</Option>
            <Option value="warn">Warning</Option>
            <Option value="error">Error</Option>
            <Option value="debug">Debug</Option>
          </Select>
        </div>

        <div style={{ 
          background: '#1e293b', 
          borderRadius: 8, 
          padding: 16, 
          height: '600px', 
          overflowY: 'auto',
          fontFamily: 'monospace',
          color: '#e2e8f0'
        }}>
          {filteredLogs.length === 0 ? (
            <div style={{ textAlign: 'center', color: '#64748b', marginTop: 100 }}>
              No logs matching criteria
            </div>
          ) : (
            filteredLogs.map(log => (
              <div key={log.id} style={{ display: 'flex', padding: '4px 0', borderBottom: '1px solid #334155' }}>
                <span style={{ color: '#94a3b8', width: 100, flexShrink: 0 }}>[{log.time}]</span>
                <span style={{ width: 80, flexShrink: 0 }}>
                  <Tag color={getLevelColor(log.level)} style={{ border: 'none' }}>{log.level.toUpperCase()}</Tag>
                </span>
                <span style={{ color: '#D4B16A', width: 100, flexShrink: 0 }}>[{log.component}]</span>
                <span style={{ flex: 1, wordBreak: 'break-all' }}>{log.message}</span>
              </div>
            ))
          )}
        </div>
      </Card>
    </div>
  );
}
