import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import { ApiOutlined, DatabaseOutlined, MessageOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * RED Admin Live Monitor — يقرأ المقاييس الحقيقية من:
 *   GET /api/admin/monitor/stats  -> { active_users, total_messages, jvm_memory_percent, uptime_ms, cpu_cores, ... }
 */
const LiveMonitor = () => {
    const [stats, setStats] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        const load = async () => {
            try {
                const response = await apiFetch('/api/admin/monitor/stats');
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                setStats(await response.json());
                setError('');
            } catch (e) {
                setError(e?.message || 'network');
            }
        };
        load();
        const interval = setInterval(load, 5000);
        return () => clearInterval(interval);
    }, []);

    return (
        <Row gutter={[16, 16]}>
            <Col span={6}><Card><Statistic title="المستخدمون المتصلون (5 دقائق)" value={stats?.active_users ?? '—'} prefix={<ThunderboltOutlined />} /></Card></Col>
            <Col span={6}><Card><Statistic title="إجمالي الرسائل" value={stats?.total_messages ?? '—'} prefix={<MessageOutlined />} /></Card></Col>
            <Col span={6}><Card><Statistic title="ذاكرة JVM" value={stats?.jvm_memory_percent ?? '—'} suffix="%" prefix={<ApiOutlined />} /></Card></Col>
            <Col span={6}><Card><Statistic title="مدة التشغيل" value={Math.round((stats?.uptime_ms ?? 0) / 1000)} suffix="ث" prefix={<DatabaseOutlined />} /></Card></Col>
            {error && <Col span={24}><Card size="small" style={{ color: '#ff7875' }}>تعذر قراءة المقاييس: {error}</Card></Col>}
        </Row>
    );
};

export default LiveMonitor;
