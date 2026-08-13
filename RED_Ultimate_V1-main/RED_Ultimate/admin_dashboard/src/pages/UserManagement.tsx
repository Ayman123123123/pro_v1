import { useCallback, useEffect, useState } from 'react';
import {
  Table, Button, Space, Input, Select, Tag, Modal, Form, message,
  Card, Avatar, Tooltip, Statistic, Row, Col, Typography, Drawer, Empty, Descriptions, Alert, Spin
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  UserOutlined, SearchOutlined, CheckOutlined, CloseOutlined, StopOutlined,
  CheckCircleOutlined, CrownOutlined, ReloadOutlined,
  UserAddOutlined, TeamOutlined, KeyOutlined, UserDeleteOutlined, SafetyOutlined,
  EyeOutlined, LockOutlined
} from '@ant-design/icons';
import {
  getUsers, approveUser, rejectUser, banUser, unbanUser,
  getUserOverview, createTemporaryPassword, requestRemoteWipe,
  getOperationsOverview, authStore,
  type UserRecord,
} from '../api';

const { Title, Text, Paragraph } = Typography;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold', APPROVED: 'green', REJECTED: 'red', SUSPENDED: 'orange', BANNED: 'red',
};
const STATUS_LABELS: Record<string, string> = {
  PENDING: 'في الانتظار', APPROVED: 'مقبول', REJECTED: 'مرفوض', SUSPENDED: 'موقوف', BANNED: 'محظور',
};

function formatWhen(value: unknown): string {
  if (value == null || value === '') return 'غير معروف';
  const date = typeof value === 'number' ? new Date(value) : new Date(String(value));
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('ar');
}

function initialOf(user: UserRecord): string {
  const source = user.displayName || user.username || '?';
  return source.trim().charAt(0).toUpperCase() || '?';
}

export default function UserManagement() {
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [roleFilter, setRoleFilter] = useState<string | undefined>();
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [banModalOpen, setBanModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [rejectForm] = Form.useForm();
  const [banForm] = Form.useForm();
  const [stats, setStats] = useState({ total: 0, pending: 0, approved: 0, banned: 0 });
  const [overview, setOverview] = useState<any>(null);
  const [overviewError, setOverviewError] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [temporaryFor, setTemporaryFor] = useState<UserRecord | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const me = authStore.user();

  const refreshStats = useCallback(async () => {
    try {
      const overviewData = await getOperationsOverview();
      const section = overviewData?.users || {};
      setStats({
        total: Number(section.total ?? 0),
        pending: Number(section.pending ?? 0),
        approved: Number(section.approved ?? 0),
        banned: Number(section.banned ?? 0),
      });
    } catch {
      /* البطاقات تبقى على آخر قيمة ناجحة */
    }
  }, []);

  const load = useCallback(async (nextPage = page, nextSearch = search) => {
    setLoading(true);
    setError('');
    try {
      const result = await getUsers({
        page: nextPage,
        size,
        search: nextSearch.trim() || undefined,
        status: statusFilter,
        role: roleFilter,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      const rows = Array.isArray(result?.content) ? result.content : [];
      setUsers(rows);
      setPage(nextPage);
      setTotal(Number(result?.totalElements ?? rows.length));
    } catch (e: any) {
      setUsers([]);
      setError(e?.message || 'تعذر تحميل المستخدمين');
    } finally {
      setLoading(false);
    }
  }, [page, search, size, statusFilter, roleFilter]);

  useEffect(() => {
    void load(0, search);
    void refreshStats();
  }, [statusFilter, roleFilter]);

  const afterChange = async () => {
    await load(page, search);
    await refreshStats();
  };

  const handleApprove = async (user: UserRecord) => {
    try {
      await approveUser(user.id);
      message.success(`تمت الموافقة على ${user.username} وإصدار الشهادات`);
      await afterChange();
    } catch (e: any) {
      message.error(e.message || 'فشل الاعتماد');
    }
  };

  const submitReject = async () => {
    try {
      const values = await rejectForm.validateFields();
      if (!selectedUser) return;
      await rejectUser(selectedUser.id, values.reason);
      message.success(`تم رفض ${selectedUser.username}`);
      setRejectModalOpen(false);
      await afterChange();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(e.message || 'فشل الرفض');
    }
  };

  const submitBan = async () => {
    try {
      const values = await banForm.validateFields();
      if (!selectedUser) return;
      await banUser(selectedUser.id, values.reason, values.durationDays ? Number(values.durationDays) : undefined);
      message.success(`تم حظر ${selectedUser.username}`);
      setBanModalOpen(false);
      await afterChange();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(e.message || 'فشل الحظر');
    }
  };

  const handleUnban = async (user: UserRecord) => {
    try {
      await unbanUser(user.id);
      message.success(`رُفع الحظر عن ${user.username}. الحساب في الانتظار حتى يسجّل جهازاً جديداً.`);
      await afterChange();
    } catch (e: any) {
      message.error(e.message || 'رفع الحظر يتطلب إعادة تسجيل الجهاز ثم موافقة صريحة');
    }
  };

  const openOverview = async (user: UserRecord) => {
    setDrawerOpen(true);
    setOverview(null);
    setOverviewError('');
    try {
      setOverview(await getUserOverview(user.id));
    } catch (e: any) {
      setOverviewError(e.message || 'تعذر تحميل ملف المستخدم التشغيلي');
    }
  };

  const setPassword = async () => {
    if (!temporaryFor) return;
    if (temporaryPassword.length < 12) return message.error('كلمة المرور المؤقتة يجب أن تكون 12 محرفاً على الأقل');
    try {
      await createTemporaryPassword(temporaryFor.id, temporaryPassword);
      message.success('تم تعيين كلمة مرور مؤقتة وإلغاء الجلسات السابقة');
      const target = temporaryFor;
      setTemporaryFor(null);
      setTemporaryPassword('');
      if (overview?.user?.id === target.id) await openOverview(target);
    } catch (e: any) {
      message.error(e.message || 'تعذر تعيين كلمة المرور المؤقتة');
    }
  };

  const requestWipe = (user: UserRecord) => Modal.confirm({
    title: `طلب مسح تطبيق يونس لـ ${user.displayName || user.username}`,
    content: 'سيتم إبطال الجلسات فوراً وإرسال أمر Remote App Wipe. لا يتم تنفيذ Factory Reset إلا للأجهزة المسجّلة كأجهزة مؤسسة.',
    okText: 'إبطال وإرسال أمر المسح',
    okType: 'danger',
    onOk: async () => {
      try {
        await requestRemoteWipe(user.id);
        message.success('تم إرسال أمر المسح وتوثيقه.');
        if (overview?.user?.id === user.id) await openOverview(user);
      } catch (e: any) {
        message.error(e.message || 'فشل إرسال أمر المسح');
      }
    },
  });

  const isSelf = (user: UserRecord) => !!me?.id && user.id === me.id;
  const isAdmin = (user: UserRecord) => user.role === 'ADMIN';

  const columns: ColumnsType<UserRecord> = [
    {
      title: 'المستخدم',
      key: 'user',
      render: (_value, user) => (
        <Space>
          <Avatar style={{ backgroundColor: isAdmin(user) ? '#722ED1' : '#1890FF' }}>{initialOf(user)}</Avatar>
          <div>
            <div>
              <Text strong>{user.displayName || user.username}</Text>
              {isAdmin(user) && <Tooltip title="مسؤول"><CrownOutlined style={{ color: '#722ED1', marginInlineStart: 6 }} /></Tooltip>}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>@{user.username || '—'} · {user.redId || '—'}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      render: (status: string) => <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status] ?? status}</Tag>,
    },
    {
      title: 'الدور',
      dataIndex: 'role',
      render: (role: string) => <Tag color={role === 'ADMIN' ? 'purple' : 'blue'}>{role === 'ADMIN' ? 'مسؤول' : 'مستخدم'}</Tag>,
    },
    {
      title: 'PSTN',
      dataIndex: 'pstnEnabled',
      render: (enabled: boolean) => enabled ? <Tag color="cyan">مفعل</Tag> : <Tag>معطل</Tag>,
    },
    {
      title: 'تاريخ التسجيل',
      dataIndex: 'createdAt',
      render: (value: string) => formatWhen(value),
    },
    {
      title: 'إجراءات',
      key: 'actions',
      width: 360,
      render: (_value, user) => (
        <Space size="small" wrap>
          {user.status === 'PENDING' && (
            <>
              <Tooltip title="موافقة">
                <Button type="primary" size="small" icon={<CheckOutlined />} onClick={() => void handleApprove(user)} />
              </Tooltip>
              <Tooltip title="رفض">
                <Button danger size="small" icon={<CloseOutlined />} onClick={() => { setSelectedUser(user); rejectForm.resetFields(); setRejectModalOpen(true); }} />
              </Tooltip>
            </>
          )}
          <Tooltip title="الملف التشغيلي">
            <Button size="small" icon={<EyeOutlined />} onClick={() => void openOverview(user)}>الملف</Button>
          </Tooltip>
          <Tooltip title={isAdmin(user) ? 'لا تُعيَّن كلمة مؤقتة لحساب مسؤول من هنا' : 'كلمة مرور مؤقتة'}>
            <Button size="small" icon={<KeyOutlined />} disabled={isAdmin(user)} onClick={() => { setTemporaryFor(user); setTemporaryPassword(''); }} />
          </Tooltip>
          <Tooltip title="مسح التطبيق عن بُعد">
            <Button size="small" danger icon={<UserDeleteOutlined />} disabled={isSelf(user)} onClick={() => requestWipe(user)} />
          </Tooltip>
          {user.status !== 'BANNED' && !isAdmin(user) && !isSelf(user) && (
            <Tooltip title="حظر">
              <Button danger size="small" icon={<StopOutlined />} onClick={() => { setSelectedUser(user); banForm.resetFields(); setBanModalOpen(true); }} />
            </Tooltip>
          )}
          {user.status === 'BANNED' && (
            <Tooltip title="رفع الحظر (قد يتطلب إعادة تسجيل الجهاز)">
              <Button type="primary" size="small" icon={<CheckCircleOutlined />} onClick={() => void handleUnban(user)} />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  const accountTag = (status: string) => (
    <Tag color={status === 'APPROVED' ? 'success' : status === 'BANNED' || status === 'SUSPENDED' ? 'error' : 'warning'}>
      {STATUS_LABELS[status] || status}
    </Tag>
  );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <TeamOutlined /> إدارة المستخدمين
        </Title>
        <Text type="secondary">قائمة حية من الخادم — موافقة ورفض وحظر وملف تشغيلي وكلمة مؤقتة ومسح عن بُعد</Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}><Card><Statistic title="إجمالي" value={stats.total} prefix={<UserOutlined />} valueStyle={{ color: '#1890ff' }} /></Card></Col>
        <Col xs={24} sm={6}><Card><Statistic title="في الانتظار" value={stats.pending} prefix={<UserAddOutlined />} valueStyle={{ color: '#E8B84A' }} /></Card></Col>
        <Col xs={24} sm={6}><Card><Statistic title="مقبول" value={stats.approved} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#00C896' }} /></Card></Col>
        <Col xs={24} sm={6}><Card><Statistic title="محظور" value={stats.banned} prefix={<StopOutlined />} valueStyle={{ color: '#FF6B6B' }} /></Card></Col>
      </Row>

      {error && (
        <Alert type="error" showIcon closable message="تعذر تحميل المستخدمين" description={error}
          action={<Button size="small" onClick={() => void load(page, search)}>إعادة المحاولة</Button>} />
      )}

      <Card>
        <Space wrap>
          <Input.Search
            placeholder="بحث بالاسم، اليوزر، أو معرّف يونس"
            allowClear
            defaultValue={search}
            onSearch={(value) => { setSearch(value); void load(0, value); }}
            style={{ width: 320 }}
            prefix={<SearchOutlined />}
          />
          <Select allowClear placeholder="الحالة" style={{ width: 150 }} value={statusFilter} onChange={setStatusFilter}
            options={Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))} />
          <Select allowClear placeholder="الدور" style={{ width: 150 }} value={roleFilter} onChange={setRoleFilter}
            options={[{ value: 'USER', label: 'مستخدم' }, { value: 'ADMIN', label: 'مسؤول' }]} />
          <Button icon={<ReloadOutlined />} onClick={() => { void load(page, search); void refreshStats(); }}>تحديث</Button>
        </Space>
      </Card>

      <Card>
        <Table<UserRecord>
          rowKey={(row) => row.id}
          columns={columns}
          dataSource={users}
          loading={loading}
          locale={{ emptyText: error ? 'تعذر التحميل' : 'لا يوجد مستخدمون مطابقون' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            showSizeChanger: false,
            showTotal: (count) => `${count} مستخدم`,
            onChange: (next) => { void load(next - 1, search); },
          }}
          scroll={{ x: 1100 }}
        />
      </Card>

      <Modal title={`رفض ${selectedUser?.username || ''}`} open={rejectModalOpen} onCancel={() => setRejectModalOpen(false)} onOk={() => void submitReject()} okText="رفض" cancelText="إلغاء" okButtonProps={{ danger: true }}>
        <Paragraph>سيتم رفض تسجيل المستخدم وإرسال السبب إليه.</Paragraph>
        <Form form={rejectForm} layout="vertical">
          <Form.Item name="reason" label="سبب الرفض" rules={[{ required: true, message: 'الرجاء إدخال السبب' }]}>
            <Input.TextArea rows={3} placeholder="مثال: معلومات غير صحيحة" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`حظر ${selectedUser?.username || ''}`} open={banModalOpen} onCancel={() => setBanModalOpen(false)} onOk={() => void submitBan()} okText="حظر" cancelText="إلغاء" okButtonProps={{ danger: true }}>
        <Paragraph type="warning">سيتم حظر المستخدم من الوصول إلى المنصة.</Paragraph>
        <Form form={banForm} layout="vertical">
          <Form.Item name="reason" label="سبب الحظر" rules={[{ required: true, message: 'الرجاء إدخال السبب' }]}>
            <Input.TextArea rows={3} placeholder="مثال: انتهاك شروط الاستخدام" />
          </Form.Item>
          <Form.Item name="durationDays" label="مدة الحظر بالأيام (اختياري)">
            <Input type="number" placeholder="اتركه فارغاً للحظر الدائم" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title="ملف المستخدم التشغيلي" width={720} open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        {overviewError && <Alert type="error" showIcon message={overviewError} />}
        {!overview?.user && !overviewError && <div style={{ display: 'grid', placeItems: 'center', height: 300 }}><Spin tip="جاري تحميل الملف التشغيلي..." /></div>}
        {overview?.user && (
          <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <Alert type={overview.online ? 'success' : 'info'} showIcon message={overview.online ? 'المستخدم متصل حالياً' : 'المستخدم غير متصل حالياً'} />
            <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
              <Descriptions.Item label="معرّف يونس">{overview.user.redId}</Descriptions.Item>
              <Descriptions.Item label="الحساب">{accountTag(overview.user.status)}</Descriptions.Item>
              <Descriptions.Item label="الدور">{overview.user.role}</Descriptions.Item>
              <Descriptions.Item label="أنشئ في">{formatWhen(overview.user.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="آخر ظهور">{formatWhen(overview.user.lastSeen ?? overview.lastSeen)}</Descriptions.Item>
              <Descriptions.Item label="الأجهزة">{overview.user.devices?.length ?? overview.devices?.length ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="PSTN">{overview.user.pstnEnabled || overview.pstnEnabled ? `${overview.user.pstnDailyLimit ?? overview.pstnDailyLimit}/يوم` : 'معطل'}</Descriptions.Item>
              <Descriptions.Item label="كلمة المرور">{overview.passwordResetRequired ? <Tag color="warning">تغيير مطلوب</Tag> : <Tag color="success">طبيعية</Tag>}</Descriptions.Item>
              <Descriptions.Item label="Remote App Wipe">{overview.remoteWipeStatus || 'NONE'}</Descriptions.Item>
              <Descriptions.Item label="MDM Factory Reset">{overview.managedDeviceWipeAllowed ? 'مسموح لجهاز مؤسسة مسجل' : 'غير متاح لهذا الحساب'}</Descriptions.Item>
            </Descriptions>
            <Row gutter={[12, 12]}>
              <Col span={12}><Statistic title="رسائل مرسلة" value={overview.messagesSent ?? 0} prefix={<SafetyOutlined />} /></Col>
              <Col span={12}><Statistic title="رسائل مستلمة" value={overview.messagesReceived ?? 0} /></Col>
              <Col span={12}><Statistic title="رسائل 24 ساعة" value={overview.messages24h ?? 0} /></Col>
              <Col span={12}><Statistic title="مكالمات RED" value={overview.redCalls ?? 0} /></Col>
              <Col span={12}><Statistic title="مكالمات PSTN" value={overview.pstnCalls ?? 0} /></Col>
              <Col span={12}><Statistic title="صادرة/واردة" value={`${overview.callsMade ?? 0}/${overview.callsReceived ?? 0}`} /></Col>
            </Row>
            <Card size="small" title={<><SafetyOutlined /> أحداث الأمان والتدقيق</>}>
              {Array.isArray(overview.securityEvents) && overview.securityEvents.length ? (
                <Table
                  size="small"
                  pagination={false}
                  rowKey={(event: any, index) => `${event.action || 'evt'}-${event.createdAt || index}`}
                  dataSource={overview.securityEvents}
                  columns={[
                    { title: 'الإجراء', dataIndex: 'action' },
                    { title: 'الهدف', dataIndex: 'targetId', render: (value) => value || '—' },
                    { title: 'الوقت', dataIndex: 'createdAt', render: (value) => formatWhen(value) },
                  ]}
                />
              ) : <Empty description="لا توجد أحداث مرتبطة بهذا المستخدم" />}
            </Card>
            <Alert type="info" showIcon message="الخصوصية محفوظة" description="الملف يعرض بيانات تشغيلية فقط — لا يكشف محتوى الرسائل المشفّرة أو مفاتيحها." />
          </Space>
        )}
      </Drawer>

      <Modal title={`كلمة مرور مؤقتة — ${temporaryFor?.displayName || temporaryFor?.username || ''}`} open={temporaryFor !== null} onCancel={() => setTemporaryFor(null)} onOk={() => void setPassword()} okText="تعيين وإلغاء الجلسات">
        <Alert type="warning" showIcon message="سلّم الكلمة عبر قناة موثوقة. تُلغى الجلسات السابقة فور التعيين." />
        <Input.Password style={{ marginTop: 16 }} value={temporaryPassword} onChange={(event) => setTemporaryPassword(event.target.value)} placeholder="12 محرفاً على الأقل" autoComplete="new-password" prefix={<LockOutlined />} />
      </Modal>
    </Space>
  );
}
