import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message } from 'antd';
import { apiFetch } from '../api';

const Approvals = () => {
    const [pendingUsers, setPendingUsers] = useState([]);

    useEffect(() => {
        // ✅ Fixed: was /api/admin/pending-users → correct /api/admin/users/pending
        apiFetch('/api/admin/users/pending')
            .then(res => res.json())
            .then(data => setPendingUsers(Array.isArray(data) ? data : []))
            .catch(() => message.error('تعذر تحميل طلبات الموافقة'));
    }, []);

    const handleAction = (userId, status) => {
        // ✅ Fixed: was /api/admin/approve/${id}?status= → correct POST /api/admin/users/action
        apiFetch('/api/admin/users/action', {
            method: 'POST',
            body: JSON.stringify({ userId, action: status })
        }).then(res => {
            if (!res.ok) throw new Error('فشل الإجراء');
            message.success(`User ${status} successfully`);
            setPendingUsers(pendingUsers.filter(u => u.id !== userId));
        }).catch(e => message.error(e.message));
    };

    const columns = [
        { title: 'Name', dataIndex: 'name', key: 'name' },
        { title: 'Email', dataIndex: 'email', key: 'email' },
        { title: 'Status', key: 'status', render: () => <Tag color="orange">PENDING</Tag> },
        { title: 'Action', key: 'action', render: (_, record) => (
            <Space>
                <Button type="primary" onClick={() => handleAction(record.id, 'APPROVED')}>Approve</Button>
                <Button danger onClick={() => handleAction(record.id, 'REJECTED')}>Reject</Button>
                <Button type="text" danger onClick={() => handleAction(record.id, 'BANNED')}>Ban</Button>
            </Space>
        )},
    ];

    return (
        <div style={{ padding: '24px' }}>
            <h1>Pending User Approvals</h1>
            <Table dataSource={pendingUsers} columns={columns} rowKey="id" />
        </div>
    );
};

export default Approvals;
