import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message, Input, Modal, Avatar, Typography } from 'antd';
import { CheckCircleOutlined, StopOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons';

import { apiFetch } from '../api';

interface Device { id: string; deviceName: string; platform: string; status: string; }
interface PendingUser {
  id: string; redId: string; username: string; displayName: string;
  status: string; createdAt: string; devices: Device[];
}

const UserApproval: React.FC = () => {
    const [users, setUsers] = useState<PendingUser[]>([]);
    const [loading, setLoading] = useState(false);

    const fetchPending = async () => {
        setLoading(true);
        try {
            const resp = await apiFetch('/api/admin/users/pending');
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            setUsers(await resp.json());
        } catch (e: any) { message.error(`YOUNES: تعذر الاتصال بخادم السيادة. (${e?.message || ''})`); }
        setLoading(false);
    };

    useEffect(() => { fetchPending(); }, []);

    const handleAction = (userId: string, action: string) => {
        Modal.confirm({
            title: `تأكيد ${action === 'APPROVED' ? 'الموافقة' : action === 'REJECTED' ? 'الرفض' : 'الحظر'}`,
            content: 'هل أنت متأكد من تنفيذ هذا الإجراء على الحساب؟',
            onOk: async () => {
                const response = await apiFetch('/api/admin/users/action', {
                    method: 'POST',
                    body: JSON.stringify({ userId, action, reason: null })
                });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                message.success('تم تحديث حالة الحساب.');
                fetchPending();
            }
        });
    };

    const columns = [
        { title: 'معرّف يونس', dataIndex: 'redId', render: (v: string) => <Typography.Text copyable>{v}</Typography.Text> },
        { title: 'المستخدم', dataIndex: 'displayName', render: (text: string, u: PendingUser) => <><Avatar icon={<UserOutlined />} /> <b>{text}</b><br /><small>@{u.username}</small></> },
        { title: 'تاريخ التسجيل', dataIndex: 'createdAt', render: (v: string) => new Date(v).toLocaleString('ar') },
        { title: 'الحالة', dataIndex: 'status', render: (s: string) => <Tag color="orange">{s}</Tag> },
        { title: 'الأجهزة', dataIndex: 'devices', render: (devices: Device[] = []) => <Space wrap>{devices.map(d => <Tag key={d.id}>{d.deviceName} · {d.platform} · {d.status}</Tag>)}</Space> },
        { title: 'الإجراءات', key: 'actions', render: (_: any, record: any) => (
            <Space>
                <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => handleAction(record.id, 'APPROVED')}>موافقة</Button>
                <Button danger icon={<StopOutlined />} onClick={() => handleAction(record.id, 'REJECTED')}>رفض</Button>
                <Button type="text" danger icon={<DeleteOutlined />} onClick={() => handleAction(record.id, 'BANNED')}>حظر</Button>
            </Space>
        ) },
    ];

    return (
        <div style={{ padding: '24px', background: '#fff', borderRadius: '12px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
                <h2>قائمة موافقات الحسابات</h2>
                <Input.Search placeholder="بحث..." style={{ width: 300 }} />
            </div>
            <Table dataSource={users} columns={columns} loading={loading} rowKey="id" />
        </div>
    );
};

export default UserApproval;
