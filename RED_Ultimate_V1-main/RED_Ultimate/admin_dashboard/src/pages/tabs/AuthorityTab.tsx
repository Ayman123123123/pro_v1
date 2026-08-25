import React, { useEffect, useState } from 'react';
import { Button, Card, Descriptions, Input, message, Modal, Space, Table, Tag, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { apiFetch } from '../../api';

interface Device { id: string; deviceName: string; platform: string; identityFingerprint: string; status: string; }
interface PendingUser { id: string; redId: string; username: string; displayName: string; status: string; createdAt: string; devices: Device[]; }

export default function AuthorityTab() {
  const [users, setUsers] = useState<PendingUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [rejecting, setRejecting] = useState<PendingUser | null>(null);
  const [reason, setReason] = useState('');

  const fetchPending = async () => {
    setLoading(true);
    try {
      const response = await apiFetch('/api/admin/users/pending');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      setUsers(await response.json());
    } catch { message.error('تعذر تحميل طلبات التسجيل'); }
    finally { setLoading(false); }
  };
  useEffect(() => { fetchPending(); }, []);

  const action = async (user: PendingUser, status: 'APPROVED' | 'REJECTED', rejectionReason?: string) => {
    const response = await apiFetch('/api/admin/users/action', {
      method: 'POST', body: JSON.stringify({ userId: user.id, action: status, reason: rejectionReason || null })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || `HTTP ${response.status}`);
    }
    message.success(status === 'APPROVED' ? 'تمت الموافقة وإصدار شهادات الأجهزة' : 'تم رفض الحساب');
    await fetchPending();
  };

  const approve = (user: PendingUser) => Modal.confirm({
    title: `الموافقة على ${user.displayName}`,
    content: `سيتم اعتماد ${user.redId} وتوقيع ${user.devices.length} جهاز/أجهزة بمفتاح سلطة يونس.`,
    okText: 'موافقة وتوقيع', cancelText: 'إلغاء',
    onOk: () => action(user, 'APPROVED').catch(e => message.error(e.message))
  });

  const columns: any[] = [
    { title: 'معرّف يونس', dataIndex: 'redId', width: 160, render: (v: string) => <Typography.Text copyable>{v}</Typography.Text> },
    { title: 'المستخدم', render: (_: any, u: PendingUser) => <><b>{u.displayName}</b><br/><span style={{color:'#888'}}>@{u.username}</span></> },
    { title: 'التسجيل', dataIndex: 'createdAt', render: (v: string) => new Date(v).toLocaleString('ar') },
    { title: 'الأجهزة', dataIndex: 'devices', render: (devices: Device[]) => <Space direction="vertical">{devices.map(d => <Tag key={d.id} icon={<SafetyCertificateOutlined />} color="gold">{d.deviceName} · {d.platform} · {d.status}</Tag>)}</Space> },
    { title: 'الحالة', dataIndex: 'status', render: (v: string) => <Tag color="orange">{v}</Tag> },
    { title: 'الإجراء', fixed: 'right', render: (_: any, u: PendingUser) => <Space>
      <Button type="primary" icon={<CheckOutlined />} onClick={() => approve(u)}>موافقة</Button>
      <Button danger icon={<CloseOutlined />} onClick={() => { setRejecting(u); setReason(''); }}>رفض</Button>
    </Space> }
  ];

  return <Card title="سلطة اعتماد حسابات يونس" extra={<Button onClick={fetchPending}>تحديث</Button>}>
    <Table dataSource={users} columns={columns} rowKey="id" loading={loading} scroll={{x: 1050}}
      expandable={{ expandedRowRender: u => <Descriptions bordered size="small" column={1}>
        {u.devices.map(d => <Descriptions.Item key={d.id} label={`${d.deviceName} — بصمة مفتاح الهوية`}><Typography.Text copyable code>{d.identityFingerprint}</Typography.Text></Descriptions.Item>)}
      </Descriptions> }} />
    <Modal title={`رفض حساب ${rejecting?.redId || ''}`} open={Boolean(rejecting)} okText="تأكيد الرفض" cancelText="إلغاء" okButtonProps={{danger:true}}
      onCancel={() => setRejecting(null)} onOk={async () => { if (!rejecting) return; try { await action(rejecting, 'REJECTED', reason); setRejecting(null); } catch(e:any) { message.error(e.message); } }}>
      <Input.TextArea value={reason} onChange={e => setReason(e.target.value)} rows={4} placeholder="سبب الرفض (اختياري ويظهر لصاحب الحساب)" />
    </Modal>
  </Card>;
}
