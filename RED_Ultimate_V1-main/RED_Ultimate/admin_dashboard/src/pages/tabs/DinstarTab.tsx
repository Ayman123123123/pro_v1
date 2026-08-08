import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Progress, Tag, Button, Switch, Space, message } from 'antd';
import { MobileOutlined, SignalFilled, ReloadOutlined } from '@ant-design/icons';

import { apiFetch } from '../../api';
const DinstarTab: React.FC = () => {
    const [slots, setSlots] = useState<any[]>([]);

    const refresh = async () => {
        const resp = await apiFetch('/api/master/v1/hardware/dinstar/slots');
        if (resp.ok) setSlots(await resp.json());
    };

    useEffect(() => { refresh(); const it = setInterval(refresh, 5000); return () => clearInterval(it); }, []);

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
                <h2 style={{ color: '#fff' }}>🔴 DINSTAR UC2000-VE-8G (GSM Gateway)</h2>
                <Button icon={<ReloadOutlined />} onClick={refresh}>Manual Sync</Button>
            </div>
            <Row gutter={[16, 16]}>
                {slots.map(slot => (
                    <Col span={6} key={slot.index}>
                        <Card style={{ background: '#1f1f1f', border: '1px solid #333' }}>
                            <Space align="start">
                                <MobileOutlined style={{ fontSize: 32, color: slot.signal > 50 ? '#52c41a' : '#f5222d' }} />
                                <div>
                                    <b style={{ color: '#fff' }}>Slot {slot.index + 1}</b>
                                    <div style={{ fontSize: 11, color: '#888' }}>{slot.operator}</div>
                                </div>
                                <Tag color={slot.status === 'IDLE' ? 'green' : 'orange'}>{slot.status}</Tag>
                            </Space>
                            <div style={{ marginTop: 16 }}>
                                <div style={{ color: '#aaa', fontSize: 12 }}>Signal Strength: {slot.signal}%</div>
                                <Progress percent={slot.signal} showInfo={false} strokeColor="#f57c00" size="small" />
                            </div>
                            <Button type="link" danger size="small" style={{ padding: 0, marginTop: 10 }}>Restart SIM</Button>
                        </Card>
                    </Col>
                ))}
            </Row>
        </div>
    );
};

export default DinstarTab;
