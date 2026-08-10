import React, { useState, useEffect } from 'react';
import { Table, Button, Tag, Space, message, Modal, Input, Typography, Card, Descriptions } from 'antd';
import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined, ReloadOutlined } from '@ant-design/icons';
import { getPendingApprovals, approveRejectUser } from '../api';

// 🔴 مدموج من AuthorityTab القديمة — بصمة الهوية + الأجهزة + السبب — بيانات حقيقية 100%
const Approvals = () => {
    const [pendingUsers, setPendingUsers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [rejecting, setRejecting] = useState(null);
    const [reason, setReason] = useState('');

    const load = async () => {
        setLoading(true);
        try {
            const data = await getPendingApprovals();
            setPendingUsers(Array.isArray(data) ? data : []);
        } catch { message.error('تعذر تحميل طلبات الموافقة — تأكد من /api/admin/users/pending'); }
        finally { setLoading(false); }
    };

    useEffect(() => { load(); }, []);

    const handleAction = async (user, status, rejectionReason) => {
        try {
            await approveRejectUser(user.id, status, rejectionReason);
            message.success(status === 'APPROVED' ? `تمت الموافقة على ${user.displayName} وإصدار شهادات ${user.devices?.length || 1} جهاز` : 'تم رفض الحساب');
            setRejecting(null); setReason('');
            load();
        } catch (e) { message.error(e.message || 'فشل الإجراء'); }
    };

    const columns = [
        { title: 'معرّف يونس', dataIndex: 'redId', width: 160, render: (v) => <Typography.Text copyable>{v}</Typography.Text> },
        { title: 'المستخدم', key: 'user', render: (_, u) => <><b>{u.displayName || u.name}</b><br/><span style={{color:'#888'}}>@{u.username}</span></> },
        { title: 'التسجيل', dataIndex: 'createdAt', render: (v) => v ? new Date(v).toLocaleString('ar') : '—' },
        { title: 'الأجهزة', dataIndex: 'devices', render: (devices) => devices ? <Space direction="vertical">{devices.map(d => <Tag key={d.id} icon={<SafetyCertificateOutlined />} color="gold">{d.deviceName || d.name || 'جهاز'} · {d.platform || '—'} · {d.status || 'PENDING'}</Tag>)}</Space> : <Tag>جهاز واحد</Tag> },
        { title: 'الحالة', key: 'status', render: () => <Tag color="orange">PENDING</Tag> },
        { title: 'إجراء', key: 'action', fixed: 'right', render: (_, record) => (
            <Space>
                <Button type="primary" icon={<CheckOutlined />} onClick={() => Modal.confirm({ title: `الموافقة على ${record.displayName || record.username}`, content: `سيتم اعتماد ${record.redId} وتوقيع ${record.devices?.length || 1} جهاز بمفتاح سلطة يونس.`, okText: 'موافقة وتوقيع', cancelText: 'إلغاء', onOk: () => handleAction(record, 'APPROVED') })}>موافقة</Button>
                <Button danger icon={<CloseOutlined />} onClick={() => { setRejecting(record); setReason(''); }}>رفض</Button>
            </Space>
        )},
    ];

    return (
        <Card title="سلطة اعتماد حسابات يونس — الموافقات المعلقة" extra={<Button icon={<ReloadOutlined />} onClick={load} loading={loading}>تحديث</Button>}>
            <Typography.Paragraph type="secondary">يعرض البصمة الحقيقية لمفتاح الهوية لكل جهاز — لا توافق قبل التحقق من البصمة عبر قناة موثوقة.</Typography.Paragraph>
            <Table dataSource={pendingUsers} columns={columns} rowKey="id" loading={loading} scroll={{x: 1050}}
                expandable={pendingUsers.some(u => u.devices?.length) ? { expandedRowRender: u => u.devices?.length ? <Descriptions bordered size="small" column={1}>{u.devices.map(d => <Descriptions.Item key={d.id} label={`${d.deviceName || d.name} — بصمة مفتاح الهوية`}><Typography.Text copyable code>{d.identityFingerprint || '—'}</Typography.Text></Descriptions.Item>)}</Descriptions> : null } : undefined}
                locale={{emptyText: 'لا توجد طلبات معلقة — كل الحسابات معالجة'}} />
            <Modal title={`رفض حساب ${rejecting?.redId || ''}`} open={Boolean(rejecting)} okText="تأكيد الرفض" cancelText="إلغاء" okButtonProps={{danger:true}} onCancel={() => setRejecting(null)} onOk={() => handleAction(rejecting, 'REJECTED', reason)}>
                <Input.TextArea value={reason} onChange={e => setReason(e.target.value)} rows={4} placeholder="سبب الرفض (يظهر لصاحب الحساب — اختياري لكن يفضل توضيحه)" />
            </Modal>
        </Card>
    );
};

export default Approvals;
