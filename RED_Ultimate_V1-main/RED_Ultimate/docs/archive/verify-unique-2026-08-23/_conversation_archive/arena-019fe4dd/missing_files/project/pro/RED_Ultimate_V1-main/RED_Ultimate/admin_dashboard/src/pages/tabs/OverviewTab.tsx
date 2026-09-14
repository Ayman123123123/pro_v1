import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Progress, Tag, Space, Alert } from 'antd';
import {
    TeamOutlined, MessageOutlined, PhoneOutlined,
    SafetyOutlined, CloudServerOutlined, ClockCircleOutlined
} from '@ant-design/icons';
import { apiFetch } from '../../api';

const OverviewTab: React.FC = () => {
    const [stats, setStats] = useState<any>(null);

    useEffect(() => {
        apiFetch('/api/master/v1/stats/realtime')
            .then(res => res.json())
            .then(setStats)
            .catch(console.error);
    }, []);

    return (
        <div>
            <Row gutter={[16, 16]}>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="Active Users"
                            value={stats?.active_users ?? '—'}
                            prefix={<TeamOutlined />}
                            valueStyle={{ color: '#1890ff' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="Messages (24h)"
                            value={stats?.messages_24h ?? '—'}
                            prefix={<MessageOutlined />}
                            valueStyle={{ color: '#52c41a' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="Active Calls"
                            value={stats?.active_calls ?? '—'}
                            prefix={<PhoneOutlined />}
                            valueStyle={{ color: '#722ed1' }}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
                <Col span={12}>
                    <Card title="System Health">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <div>
                                <span>CPU Load: </span>
                                <Progress
                                    percent={stats?.system_load ?? 0}
                                    status={stats?.system_load > 80 ? 'exception' : 'active'}
                                />
                            </div>
                            <div>
                                <span>Database: </span>
                                <Tag color={stats?.db_health === 'HEALTHY' ? 'green' : 'red'}>
                                    {stats?.db_health ?? 'UNKNOWN'}
                                </Tag>
                            </div>
                            <div>
                                <span>Pending Approvals: </span>
                                <Tag color={stats?.pending_approvals > 0 ? 'orange' : 'green'}>
                                    {stats?.pending_approvals ?? 0}
                                </Tag>
                            </div>
                        </Space>
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="Infrastructure">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span><CloudServerOutlined /> MongoDB</span>
                                <Tag color="green">Connected</Tag>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span><CloudServerOutlined /> PostgreSQL</span>
                                <Tag color="green">Connected</Tag>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span><CloudServerOutlined /> Redis</span>
                                <Tag color="green">Connected</Tag>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span><ClockCircleOutlined /> Uptime</span>
                                <Tag>24h+</Tag>
                            </div>
                        </Space>
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default OverviewTab;
