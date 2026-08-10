import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Space, Progress } from 'antd';
import { MessageOutlined, SendOutlined, ClockCircleOutlined } from '@ant-design/icons';

import { apiFetch } from '../../api';
const MessagingTab: React.FC = () => {
    const [messageStats, setMessageStats] = useState<any>(null);

    useEffect(() => {
        apiFetch('/api/master/v1/stats/realtime')
            .then(res => res.json())
            .then(setMessageStats)
            .catch(console.error);
    }, []);

    const recentMessages = [
        { key: '1', conversationId: 'conv-001', sender: 'user-123', type: 'TEXT', status: 'DELIVERED', time: '2 min ago' },
        { key: '2', conversationId: 'conv-002', sender: 'user-456', type: 'IMAGE', status: 'READ', time: '5 min ago' },
        { key: '3', conversationId: 'conv-001', sender: 'user-123', type: 'VOICE', status: 'SENT', time: '8 min ago' },
    ];

    const columns = [
        { title: 'Conversation', dataIndex: 'conversationId', key: 'conversationId' },
        { title: 'Sender', dataIndex: 'sender', key: 'sender' },
        { title: 'Type', dataIndex: 'type', key: 'type',
          render: (t: string) => <Tag>{t}</Tag> },
        { title: 'Status', dataIndex: 'status', key: 'status',
          render: (s: string) => <Tag color={s === 'READ' ? 'green' : s === 'DELIVERED' ? 'blue' : 'orange'}>{s}</Tag> },
        { title: 'Time', dataIndex: 'time', key: 'time' },
    ];

    return (
        <div>
            <Row gutter={[16, 16]}>
                <Col span={6}>
                    <Card>
                        <Statistic title="Messages Today" value={messageStats?.messages_24h ?? '—'}
                            prefix={<MessageOutlined />} valueStyle={{ color: '#1890ff' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Delivery Rate" value={98.5} suffix="%"
                            prefix={<SendOutlined />} valueStyle={{ color: '#52c41a' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Avg Latency" value={45} suffix="ms"
                            prefix={<ClockCircleOutlined />} valueStyle={{ color: '#722ed1' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Active Conversations" value={messageStats?.active_conversations ?? '—'}
                            prefix={<MessageOutlined />} valueStyle={{ color: '#fa8c16' }} />
                    </Card>
                </Col>
            </Row>

            <Card title="Recent Messages" style={{ marginTop: 16 }}>
                <Table dataSource={recentMessages} columns={columns} pagination={{ pageSize: 5 }} />
            </Card>
        </div>
    );
};

export default MessagingTab;
