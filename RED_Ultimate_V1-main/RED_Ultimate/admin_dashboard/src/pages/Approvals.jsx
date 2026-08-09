import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Tag, Space, message, Modal, Input, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined } from '@ant-design/icons';

import { apiFetch } from '../api';

/**
 * قائمة موافقات الحسابات — متوافقة مع عقد لوحة RED:
 *   GET  /api/admin/users/pending        (قائمة الانتظار)
 *   POST /api/admin/users/action         { userId, action, reason }
 */
const Approvals = () => {
    const [pendingUsers, setPendingUsers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [rejecting, setRejecting] = useState(null);
    const [reason, setReason] = useState('');

    const fetchPending = useCallback(async () => {
        setLoading(true);
        try {
            const response = await apiFetch('/api/admin/users/pending');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            setPendingUsers(await response.json());
        } catch {
            message.error('تعذر تحميل طلبات الموافقة');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchPending(); }, [fetchPending]);

    const handleAction = async (userId, status, rejectionReason = null) => {
        const response = await apiFetch('/api/admin/users/action', {
            method: 'POST',
            body: JSON.stringify({ userId, action: status, reason: rejectionReason })
        });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.error || `HTTP ${response.status}`);
        }
        message.success(status === 'APPROVED' ? 'تمت الموافقة وتوقيع الأجهزة' : 'تم تحديث حالة الحساب');
        await fetchPending();
    };

    const approve = (record) => Modal.confirm({
        title: `الموافقة على ${record.displayName || record.username}`,
        okText: 'موافقة', cancelText: 'إلغاء',
        onOk: () => handleAction(record.id, 'APPROVED').catch(e => message.error(e.message))
    });

    const columns = [
        { title: 'معرّف يونس', dataIndex: 'redId', render: (v) => <Typography.Text copyable>{v}</Typography.Text> },
        { title: 'المستخدم', render: (_, u) => <><b>{u.displayName}</b><br /><small>@{u.username}</small></> },
        { title: 'التسجيل', dataIndex: 'createdAt', render: (v) => new Date(v).toLocaleString('ar') },
        { title: 'الأجهزة', dataIndex: 'devices', render: (devices = []) => <Space>{devices.map(d => <Tag key={d.id} icon={<SafetyCertificateOutlined />} color="gold">{d.deviceName}</Tag>)}</Space> },
        { title: 'الحالة', dataIndex: 'status', render: (v) => <Tag color="orange">{v}</Tag> },
        {
            title: 'الإجراء', key: 'action', render: (_, record) => (
                <Space>
                    <Button type="primary" icon={<CheckOutlined />} onClick={() => approve(record)}>موافقة</Button>
                    <Button danger icon={<CloseOutlined />} onClick={() => { setRejecting(record); setReason(''); }}>رفض</Button>
                </Space>
            )
        },
    ];

    return (
        <div style={{ padding: '24px' }}>
            <h1>قائمة موافقات الحسابات</h1>
            <Table dataSource={pendingUsers} columns={columns} rowKey="id" loading={loading} />
            <Modal
                title={`رفض حساب ${rejecting?.redId || ''}`}
                open={Boolean(rejecting)}
                okText="تأكيد الرفض" cancelText="إلغاء" okButtonProps={{ danger: true }}
                onCancel={() => setRejecting(null)}
                onOk={async () => {
                    if (!rejecting) return;
                    try { await handleAction(rejecting.id, 'REJECTED', reason || null); setRejecting(null); }
                    catch (e) { message.error(e.message); }
                }}
            >
                <Input.TextArea value={reason} onChange={e => setReason(e.target.value)} rows={4} placeholder="سبب الرفض (اختياري)" />
            </Modal>
        </div>
    );
};

export default Approvals;
