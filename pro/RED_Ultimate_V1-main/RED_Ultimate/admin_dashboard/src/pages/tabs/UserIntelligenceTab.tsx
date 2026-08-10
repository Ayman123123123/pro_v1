import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Drawer, Empty, Input, Modal, Row, Space, Spin, Statistic, Table, Tag, Typography, message } from 'antd';
import { KeyOutlined, ReloadOutlined, SafetyOutlined, UserDeleteOutlined } from '@ant-design/icons';
import { apiFetch } from '../../api';

type User = { id: string; redId: string; username: string; displayName: string; status: string; role: string; createdAt: string; updatedAt: string; lastSeen?: number; pstnEnabled: boolean; pstnDailyLimit: number; devices: unknown[] };
type Overview = {
  user: User;
  online: boolean;
  messagesSent: number;
  messagesReceived: number;
  messages24h: number;
  callsMade: number;
  callsReceived: number;
  redCalls: number;
  pstnCalls: number;
  passwordResetRequired: boolean;
  remoteWipeStatus: string;
  managedDeviceWipeAllowed: boolean;
  securityEvents: { action: string; targetId?: string; createdAt: string }[];
};

async function readJson(response: Response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(String(body.error || body.message || `HTTP ${response.status}`));
  return body;
}

const accountTag = (status: string) => <Tag color={status === 'APPROVED' ? 'success' : status === 'BANNED' || status === 'SUSPENDED' ? 'error' : 'warning'}>{status}</Tag>;

export default function UserIntelligenceTab() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [temporaryFor, setTemporaryFor] = useState<User | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState('');

  const loadUsers = async () => {
    setLoading(true);
    try { setUsers(await readJson(await apiFetch('/api/admin/users'))); }
    catch (failure) { message.error(failure instanceof Error ? failure.message : 'تعذر تحميل المستخدمين'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void loadUsers(); }, []);

  const openOverview = async (user: User) => {
    setDrawerOpen(true);
    setOverview(null);
    try { setOverview(await readJson(await apiFetch(`/api/admin/users/${user.id}/overview`))); }
    catch (failure) { message.error(failure instanceof Error ? failure.message : 'تعذر تحميل ملف المستخدم'); }
  };

  const setPassword = async () => {
    if (!temporaryFor) return;
    if (temporaryPassword.length < 12) return message.error('كلمة المرور المؤقتة يجب أن تكون 12 محرفاً على الأقل');
    try {
      const response = await apiFetch(`/api/admin/users/${temporaryFor.id}/temporary-password`, { method: 'POST', body: JSON.stringify({ temporaryPassword }) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      message.success('تم تعيين كلمة مرور مؤقتة وإلغاء كل الجلسات السابقة.');
      setTemporaryFor(null); setTemporaryPassword('');
      if (overview?.user.id === temporaryFor.id) await openOverview(temporaryFor);
    } catch (failure) { message.error(failure instanceof Error ? failure.message : 'تعذر تعيين كلمة المرور المؤقتة'); }
  };

  const requestWipe = (user: User) => Modal.confirm({
    title: `طلب مسح تطبيق يونس لـ ${user.displayName}`,
    content: 'سيتم إبطال الجلسات فوراً وإرسال أمر Remote App Wipe. لا يتم تنفيذ Factory Reset إلا للأجهزة المسجلة مسبقاً كأجهزة مؤسسة MDM.',
    okText: 'إبطال وإرسال أمر المسح',
    okType: 'danger',
    onOk: async () => {
      const response = await apiFetch(`/api/admin/users/${user.id}/remote-app-wipe`, { method: 'POST' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      message.success('تم إرسال أمر Remote App Wipe وتوثيقه.');
      if (overview?.user.id === user.id) await openOverview(user);
    },
  });

  return <Card title="مركز معلومات المستخدمين" extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={() => void loadUsers()}>تحديث</Button>}>
    <Typography.Paragraph type="secondary">يعرض بيانات تشغيلية مهمة دون كشف محتوى الرسائل أو مفاتيح التشفير أو أرقام الهواتف الكاملة.</Typography.Paragraph>
    <Table
      rowKey="id"
      loading={loading}
      dataSource={users}
      scroll={{ x: 1000 }}
      columns={[
        { title: 'المستخدم', render: (_: unknown, user: User) => <><Typography.Text strong>{user.displayName}</Typography.Text><br /><Typography.Text type="secondary">@{user.username}</Typography.Text></> },
        { title: 'RED ID', dataIndex: 'redId', render: (value: string) => <Typography.Text copyable>{value}</Typography.Text> },
        { title: 'الحساب', dataIndex: 'status', render: accountTag },
        { title: 'الأجهزة', dataIndex: 'devices', render: (devices: unknown[]) => devices.length },
        { title: 'PSTN', render: (_: unknown, user: User) => user.pstnEnabled ? <Tag color="blue">{user.pstnDailyLimit}/يوم</Tag> : <Tag>معطل</Tag> },
        { title: 'الإجراء', fixed: 'right', render: (_: unknown, user: User) => <Space><Button onClick={() => void openOverview(user)}>الملف التشغيلي</Button><Button icon={<KeyOutlined />} disabled={user.role === 'ADMIN'} onClick={() => { setTemporaryFor(user); setTemporaryPassword(''); }}>كلمة مؤقتة</Button><Button danger icon={<UserDeleteOutlined />} onClick={() => requestWipe(user)}>مسح التطبيق</Button></Space> },
      ]}
    />

    <Drawer title="ملف المستخدم التشغيلي" width={720} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
      {!overview ? <Spin /> : <Space direction="vertical" size={18} style={{ width: '100%' }}>
        <Alert type={overview.online ? 'success' : 'info'} showIcon message={overview.online ? 'المستخدم متصل حالياً' : 'المستخدم غير متصل حالياً'} />
        <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
          <Descriptions.Item label="RED ID">{overview.user.redId}</Descriptions.Item>
          <Descriptions.Item label="الحساب">{accountTag(overview.user.status)}</Descriptions.Item>
          <Descriptions.Item label="الدور">{overview.user.role}</Descriptions.Item>
          <Descriptions.Item label="أنشئ في">{new Date(overview.user.createdAt).toLocaleString('ar-SA')}</Descriptions.Item>
          <Descriptions.Item label="آخر ظهور">{overview.user.lastSeen ? new Date(overview.user.lastSeen).toLocaleString('ar-SA') : 'غير معروف'}</Descriptions.Item>
          <Descriptions.Item label="الأجهزة المعتمدة">{overview.user.devices.length}</Descriptions.Item>
          <Descriptions.Item label="PSTN">{overview.user.pstnEnabled ? `${overview.user.pstnDailyLimit}/يوم` : 'معطل'}</Descriptions.Item>
          <Descriptions.Item label="كلمة المرور">{overview.passwordResetRequired ? <Tag color="warning">تغيير مطلوب</Tag> : <Tag color="success">طبيعية</Tag>}</Descriptions.Item>
          <Descriptions.Item label="Remote App Wipe">{overview.remoteWipeStatus}</Descriptions.Item>
          <Descriptions.Item label="MDM Factory Reset">{overview.managedDeviceWipeAllowed ? 'مسموح لجهاز مؤسسة مسجل' : 'غير متاح لهذا الحساب'}</Descriptions.Item>
        </Descriptions>
        <Row gutter={[12, 12]}>
          <Col span={12}><Statistic title="رسائل مرسلة" value={overview.messagesSent} /></Col>
          <Col span={12}><Statistic title="رسائل مستلمة" value={overview.messagesReceived} /></Col>
          <Col span={12}><Statistic title="رسائل 24 ساعة" value={overview.messages24h} /></Col>
          <Col span={12}><Statistic title="مكالمات RED" value={overview.redCalls} /></Col>
          <Col span={12}><Statistic title="مكالمات PSTN" value={overview.pstnCalls} /></Col>
          <Col span={12}><Statistic title="مكالمات صادرة/واردة" value={`${overview.callsMade}/${overview.callsReceived}`} /></Col>
        </Row>
        <Card size="small" title={<><SafetyOutlined /> أحداث الأمان والتدقيق</>}>
          {overview.securityEvents.length ? <Table size="small" pagination={false} rowKey={(event) => `${event.action}-${event.createdAt}`} dataSource={overview.securityEvents} columns={[
            { title: 'الإجراء', dataIndex: 'action' }, { title: 'الهدف', dataIndex: 'targetId', render: (value: string) => value || '—' }, { title: 'الوقت', dataIndex: 'createdAt', render: (value: string) => new Date(value).toLocaleString('ar-SA') },
          ]} /> : <Empty description="لا توجد أحداث مرتبطة بهذا المستخدم" />}
        </Card>
      </Space>}
    </Drawer>

    <Modal title={`كلمة مرور مؤقتة — ${temporaryFor?.displayName || ''}`} open={temporaryFor !== null} onCancel={() => setTemporaryFor(null)} onOk={() => void setPassword()} okText="تعيين وإلغاء الجلسات">
      <Alert type="warning" showIcon message="المدير يعرف كلمة المرور المؤقتة؛ يجب تسليمها للمستخدم عبر قناة موثوقة وإجباره على تغييرها عند أول دخول." />
      <Input.Password style={{ marginTop: 16 }} value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} placeholder="12 محرفاً على الأقل" autoComplete="new-password" />
    </Modal>
  </Card>;
}
