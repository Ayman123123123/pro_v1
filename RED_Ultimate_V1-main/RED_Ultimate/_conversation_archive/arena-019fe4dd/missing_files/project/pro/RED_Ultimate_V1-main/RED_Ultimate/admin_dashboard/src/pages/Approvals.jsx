import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message } from 'antd';

import { apiFetch } from '../api';
const Approvals = () => {
    const [pendingUsers, setPendingUsers] = useState([]);

    useEffect(() => {
        // جلب المستخدمين الذين ينتظرون الموافقة
        apiFetch('/api/admin/pending-users')
            .then(res => res.json())
            .then(data => setPendingUsers(data));
    }, []);

    const handleAction = (userId, status) => {
        apiFetch(`/api/admin/approve/${userId}?status=${status}`, { method: 'POST' })
            .then(() => {
                message.success(`User ${status} successfully`);
                setPendingUsers(pendingUsers.filter(u => u.id !== userId));
            });
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
