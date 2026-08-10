import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message, Input, Modal, Avatar } from 'antd';
import { CheckCircleOutlined, StopOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons';

import { apiFetch } from '../api';
const UserApproval: React.FC = () => {
    const [users, setUsers] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);

    const fetchPending = async () => {
        setLoading(true);
        try {
            const resp = await apiFetch('/api/admin/users/pending');
            const data = await resp.json();
            setUsers(data);
        } catch (e) { message.error("YOUNES: Connection to Master Server failed."); }
        setLoading(false);
    };

    useEffect(() => { fetchPending(); }, []);

    const handleAction = (userId: string, action: string) => {
        Modal.confirm({
            title: `Confirm ${action}`,
            content: `Are you sure you want to ${action} this user?`,
            onOk: async () => {
                await apiFetch(`/api/admin/users/update-status?userId=${userId}&status=${action}`, { method: 'POST' });
                message.success(`User ${action} successfully.`);
                fetchPending();
            }
        });
    };

    const columns = [
        { title: 'User', dataIndex: 'name', key: 'name', render: (text: string) => <><Avatar icon={<UserOutlined />} /> {text}</> },
        { title: 'Email', dataIndex: 'email', key: 'email' },
        { title: 'Registered At', dataIndex: 'date', key: 'date' },
        { title: 'Status', dataIndex: 'status', key: 'status', render: (s: string) => <Tag color="orange">{s}</Tag> },
        { title: 'Actions', key: 'actions', render: (_: any, record: any) => (
            <Space>
                <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => handleAction(record.id, 'APPROVED')}>Approve</Button>
                <Button danger icon={<StopOutlined />} onClick={() => handleAction(record.id, 'REJECTED')}>Reject</Button>
                <Button type="text" danger icon={<DeleteOutlined />} onClick={() => handleAction(record.id, 'BANNED')}>Ban</Button>
            </Space>
        ) },
    ];

    return (
        <div style={{ padding: '24px', background: '#fff', borderRadius: '12px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
                <h2>🔴 Account Approval Queue</h2>
                <Input.Search placeholder="Search users..." style={{ width: 300 }} />
            </div>
            <Table dataSource={users} columns={columns} loading={loading} rowKey="id" />
        </div>
    );
};

export default UserApproval;
