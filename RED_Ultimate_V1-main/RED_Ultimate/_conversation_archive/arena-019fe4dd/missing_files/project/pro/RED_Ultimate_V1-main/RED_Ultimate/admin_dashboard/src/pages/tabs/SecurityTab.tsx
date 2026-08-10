import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Button, Modal, Input, Alert, Tag, Space, Table, message } from 'antd';
import { SafetyOutlined, WarningOutlined, DeleteOutlined, LockOutlined, ExclamationCircleOutlined } from '@ant-design/icons';

import { apiFetch } from '../../api';
const SecurityTab: React.FC = () => {
    const [killSwitchModal, setKillSwitchModal] = useState(false);
    const [wipeModal, setWipeModal] = useState(false);
    const [targetUserId, setTargetUserId] = useState('');
    const [reason, setReason] = useState('');
    const [securityEvents, setSecurityEvents] = useState<any[]>([]);
    const loadAudit = async () => { const response = await apiFetch('/api/admin/audit'); if (response.ok) setSecurityEvents(await response.json()); };
    useEffect(() => { loadAudit(); }, []);

    const handleKillSwitch = async () => {
        if (!reason) { message.error('Reason required'); return; }
        const response = await apiFetch(`/api/admin/security/kill-switch?reason=${encodeURIComponent(reason)}`, { method: 'POST' });
        if (!response.ok) return message.error('Kill switch failed');
        message.success('Kill switch activated'); setKillSwitchModal(false); await loadAudit();
    };

    const handleWipe = async () => {
        if (!targetUserId) { message.error('User ID required'); return; }
        const response = await apiFetch(`/api/admin/security/wipe?userId=${encodeURIComponent(targetUserId)}`, { method: 'POST' });
        if (!response.ok) return message.error('Wipe failed');
        message.success('Wipe signal sent'); setWipeModal(false); await loadAudit();
    };

    return (
        <div>
            <Alert
                message="Security Operations Center"
                description="Manage device security, remote wipe, and emergency kill switch."
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
            />

            <Row gutter={[16, 16]}>
                <Col span={6}>
                    <Card>
                        <Statistic title="Threat Level" value="UNKNOWN" prefix={<SafetyOutlined />}
                            valueStyle={{ color: '#52c41a' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Blocked Devices" value="—" prefix={<LockOutlined />}
                            valueStyle={{ color: '#ff4d4f' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Active Sessions" value="—" prefix={<SafetyOutlined />}
                            valueStyle={{ color: '#1890ff' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Security Score" value="N/A"
                            prefix={<SafetyOutlined />} valueStyle={{ color: '#52c41a' }} />
                    </Card>
                </Col>
            </Row>

            <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
                <Col span={12}>
                    <Card title="⚡ Emergency Actions">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <Button danger block icon={<WarningOutlined />} size="large"
                                onClick={() => setKillSwitchModal(true)}>
                                🔴 KILL SWITCH — Wipe All Devices
                            </Button>
                            <Button type="primary" danger block icon={<DeleteOutlined />}
                                onClick={() => setWipeModal(true)}>
                                Remote Wipe — Single Device
                            </Button>
                        </Space>
                    </Card>
                </Col>
                <Col span={12}>
                    <Card title="Recent Security Events">
                        <Table
                            dataSource={securityEvents}
                            rowKey="id"
                            columns={[
                                { title: 'Action', dataIndex: 'action', render: (v: string) => <Tag color={v.includes('KILL') ? 'red' : 'blue'}>{v}</Tag> },
                                { title: 'Target', dataIndex: 'targetId', render: (v: string) => v || '—' },
                                { title: 'Administrator', dataIndex: 'actorId', render: (v: string) => v || 'SYSTEM' },
                                { title: 'Time', dataIndex: 'createdAt', render: (v: string) => new Date(v).toLocaleString('ar') },
                            ]}
                            locale={{emptyText:'لا توجد أحداث تدقيق مسجلة'}}
                            pagination={{pageSize:8}}
                            size="small"
                        />
                    </Card>
                </Col>
            </Row>

            <Modal title="⚠️ KILL SWITCH Confirmation" open={killSwitchModal}
                onOk={handleKillSwitch} onCancel={() => setKillSwitchModal(false)}
                okButtonProps={{ danger: true }}>
                <Alert message="This will WIPE ALL DEVICES immediately!" type="error" showIcon />
                <Input.TextArea style={{ marginTop: 16 }} placeholder="Reason for kill switch..."
                    value={reason} onChange={e => setReason(e.target.value)} rows={3} />
            </Modal>

            <Modal title="Remote Wipe — Single Device" open={wipeModal}
                onOk={handleWipe} onCancel={() => setWipeModal(false)}
                okButtonProps={{ danger: true }}>
                <Input placeholder="Target User ID" value={targetUserId}
                    onChange={e => setTargetUserId(e.target.value)} />
            </Modal>
        </div>
    );
};

export default SecurityTab;
