import React, { useState, useEffect } from 'react';
import { Alert, Card, Col, Row, Statistic, Tag } from 'antd';
import {
    TeamOutlined, MessageOutlined, PhoneOutlined,
    SafetyOutlined, CloudServerOutlined, ClockCircleOutlined
} from '@ant-design/icons';
import { apiFetch } from '../../api';

const OverviewTab: React.FC = () => {
    const [stats, setStats] = useState<any>(null);
    const [health, setHealth] = useState<any>(null);
    const [calls, setCalls] = useState<any>(null);
    const [error, setError] = useState('');

    useEffect(() => {
        const load = async () => {
            const [statsResponse, healthResponse, callsResponse] = await Promise.allSettled([
                apiFetch('/api/master/v1/stats/realtime'), apiFetch('/health'), apiFetch('/api/master/v1/media/active-calls')
            ]);
            const failures: string[] = [];
            if (statsResponse.status === 'fulfilled' && statsResponse.value.ok) setStats(await statsResponse.value.json()); else failures.push('المقاييس');
            if (healthResponse.status === 'fulfilled' && healthResponse.value.ok) setHealth(await healthResponse.value.json()); else failures.push('الصحة');
            if (callsResponse.status === 'fulfilled' && callsResponse.value.ok) setCalls(await callsResponse.value.json()); else failures.push('الوسائط');
            setError(failures.length ? `تعذر تحديث: ${failures.join('، ')}` : '');
        };
        load(); const timer = setInterval(load, 5000); return () => clearInterval(timer);
    }, []);

    const serviceTag = (name: string) => {
        const status = health?.services?.[name];
        return <Tag color={status === 'UP' ? 'green' : status === 'DOWN' ? 'red' : 'default'}>{status || 'CHECKING'}</Tag>;
    };

    return (
        <div>
            <Row gutter={[16, 16]}>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="المستخدمون المتصلون"
                            value={stats?.active_users ?? '—'}
                            prefix={<TeamOutlined />}
                            valueStyle={{ color: '#1890ff' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="الرسائل (24 ساعة)"
                            value={stats?.messages_24h ?? '—'}
                            prefix={<MessageOutlined />}
                            valueStyle={{ color: '#52c41a' }}
                        />
                    </Card>
                </Col>
                <Col span={8}>
                    <Card>
                        <Statistic
                            title="المكالمات النشطة"
                            value={calls?.active_calls ?? '—'}
                            prefix={<PhoneOutlined />}
                            valueStyle={{ color: '#722ed1' }}
                        />
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
                <Col xs={24} lg={12}>
                    <Card title="صحة النظام" extra={<Tag color={health?.status === 'UP' ? 'green' : health?.status === 'DOWN' ? 'red' : 'default'}>{health?.status || 'CHECKING'}</Tag>}>
                        <div style={{ display: 'grid', gap: 12 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}><span><CloudServerOutlined /> MongoDB</span>{serviceTag('mongodb')}</div>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}><span><CloudServerOutlined /> PostgreSQL</span>{serviceTag('postgresql')}</div>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}><span><CloudServerOutlined /> Redis</span>{serviceTag('redis')}</div>
                        </div>
                    </Card>
                </Col>
                <Col xs={24} lg={12}>
                    <Card title="المراجعة والإتاحة">
                        <Statistic title="طلبات الموافقة المعلقة" value={stats?.pending_approvals ?? '—'} prefix={<SafetyOutlined />} />
                        <div style={{ marginTop: 12 }}><ClockCircleOutlined /> تحديث آلي كل 5 ثوانٍ</div>
                    </Card>
                </Col>
            </Row>
            {error && <Alert type="warning" showIcon message={error} style={{ marginTop: 16 }} />}
        </div>
    );
};

export default OverviewTab;
