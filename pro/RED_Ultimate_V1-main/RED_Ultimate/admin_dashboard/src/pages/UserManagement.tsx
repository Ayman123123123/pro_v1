import { useEffect, useState } from 'react';
import {
  Table, Button, Space, Input, Select, Tag, Modal, Form, message, Popconfirm,
  Card, Avatar, Tooltip, Badge, Statistic, Row, Col, Typography
} from 'antd';
import {
  UserOutlined, SearchOutlined, CheckOutlined, CloseOutlined, StopOutlined,
  CheckCircleOutlined, DeleteOutlined, CrownOutlined, ReloadOutlined,
  UserAddOutlined, TeamOutlined
} from '@ant-design/icons';
import {
  getUsers, approveUser, rejectUser, banUser, unbanUser, promoteUser, deleteUser,
  type UserRecord, type PageResponse
} from '../api';

const { Title, Text, Paragraph } = Typography;
const { Search } = Input;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold',
  APPROVED: 'green',
  REJECTED: 'red',
  SUSPENDED: 'orange',
  BANNED: 'red',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'في الانتظار',
  APPROVED: 'مقبول',
  REJECTED: 'مرفوض',
  SUSPENDED: 'موقوف',
  BANNED: 'محظور',
};

const ROLE_COLORS: Record<string, string> = {
  USER: 'blue',
  ADMIN: 'purple',
};

export default function UserManagement() {
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [roleFilter, setRoleFilter] = useState<string | undefined>();
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [banModalOpen, setBanModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserRecord | null>(null);
  const [rejectForm] = Form.useForm();
  const [banForm] = Form.useForm();
  const [stats, setStats] = useState({ total: 0, pending: 0, approved: 0, banned: 0 });

  const load = async (p = page) => {
    setLoading(true);
    try {
      const result: PageResponse<UserRecord> = await getUsers({
        page: p,
        size,
        search: search || undefined,
        status: statusFilter,
        role: roleFilter,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setUsers(result.content);
      setTotal(result.totalElements);
      // Calculate stats from current page + initial
      setStats({
        total: result.totalElements,
        pending: result.content.filter(u => u.status === 'PENDING').length,
        approved: result.content.filter(u => u.status === 'APPROVED').length,
        banned: result.content.filter(u => u.status === 'BANNED').length,
      });
    } catch (e: any) {
      message.error('تعذر تحميل المستخدمين: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(0);
  }, [statusFilter, roleFilter]);

  const handleApprove = async (user: UserRecord) => {
    try {
      await approveUser(user.id);
      message.success(`تمت الموافقة على ${user.username}`);
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleReject = (user: UserRecord) => {
    setSelectedUser(user);
    rejectForm.resetFields();
    setRejectModalOpen(true);
  };

  const handleBan = (user: UserRecord) => {
    setSelectedUser(user);
    banForm.resetFields();
    setBanModalOpen(true);
  };

  const handleUnban = async (user: UserRecord) => {
    try {
      await unbanUser(user.id);
      message.success(`تم رفع الحظر عن ${user.username}`);
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handlePromote = async (user: UserRecord, newRole: 'USER' | 'ADMIN') => {
    try {
      await promoteUser(user.id, newRole);
      message.success(`تم تغيير دور ${user.username} إلى ${newRole}`);
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (user: UserRecord) => {
    try {
      await deleteUser(user.id, false);
      message.success(`تم حظر ${user.username}`);
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const submitReject = async () => {
    try {
      const values = await rejectForm.validateFields();
      if (!selectedUser) return;
      await rejectUser(selectedUser.id, values.reason);
      message.success(`تم رفض ${selectedUser.username}`);
      setRejectModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const submitBan = async () => {
    try {
      const values = await banForm.validateFields();
      if (!selectedUser) return;
      await banUser(selectedUser.id, values.reason, values.durationDays);
      message.success(`تم حظر ${selectedUser.username}`);
      setBanModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'المستخدم',
      key: 'user',
      render: (r: UserRecord) => (
        <Space>
          <Avatar style={{ backgroundColor: r.role === 'ADMIN' ? '#722ED1' : '#1890FF' }}>
            {r.username.charAt(0).toUpperCase()}
          </Avatar>
          <div>
            <div>
              <Text strong>{r.displayName}</Text>
              {r.role === 'ADMIN' && (
                <Tooltip title="مسؤول">
                  <CrownOutlined style={{ color: '#722ED1', marginInlineStart: 6 }} />
                </Tooltip>
              )}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>@{r.username} · {r.redId}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status] ?? status}</Tag>,
      filters: Object.keys(STATUS_LABELS).map(s => ({ text: STATUS_LABELS[s], value: s })),
    },
    {
      title: 'الدور',
      dataIndex: 'role',
      key: 'role',
      render: (role: string) => <Tag color={ROLE_COLORS[role]}>{role === 'ADMIN' ? 'مسؤول' : 'مستخدم'}</Tag>,
    },
    {
      title: 'PSTN',
      dataIndex: 'pstnEnabled',
      key: 'pstnEnabled',
      render: (enabled: boolean) => enabled ? <Tag color="cyan">مفعل</Tag> : <Tag>معطل</Tag>,
    },
    {
      title: 'تاريخ التسجيل',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: string) => new Date(d).toLocaleDateString('ar-EG'),
      sorter: true,
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: UserRecord) => (
        <Space size="small" wrap>
          {r.status === 'PENDING' && (
            <>
              <Tooltip title="موافقة">
                <Button
                  type="primary"
                  size="small"
                  icon={<CheckOutlined />}
                  onClick={() => handleApprove(r)}
                />
              </Tooltip>
              <Tooltip title="رفض">
                <Button
                  danger
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => handleReject(r)}
                />
              </Tooltip>
            </>
          )}
          {r.status === 'APPROVED' && r.role === 'USER' && (
            <Tooltip title="ترقية لمسؤول">
              <Button
                size="small"
                icon={<CrownOutlined />}
                onClick={() => handlePromote(r, 'ADMIN')}
              />
            </Tooltip>
          )}
          {r.role === 'ADMIN' && r.status === 'APPROVED' && (
            <Tooltip title="إلغاء صلاحية المسؤول">
              <Button
                size="small"
                icon={<CrownOutlined />}
                onClick={() => handlePromote(r, 'USER')}
              />
            </Tooltip>
          )}
          {r.status !== 'BANNED' && (
            <Tooltip title="حظر">
              <Button
                danger
                size="small"
                icon={<StopOutlined />}
                onClick={() => handleBan(r)}
              />
            </Tooltip>
          )}
          {r.status === 'BANNED' && (
            <Tooltip title="رفع الحظر">
              <Button
                type="primary"
                size="small"
                icon={<CheckCircleOutlined />}
                onClick={() => handleUnban(r)}
              />
            </Tooltip>
          )}
          <Popconfirm
            title="حظر المستخدم؟"
            description={`هل تريد حظر ${r.username} نهائياً؟`}
            onConfirm={() => handleDelete(r)}
            okText="نعم"
            cancelText="إلغاء"
          >
            <Tooltip title="حظر">
              <Button danger size="small" icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <TeamOutlined /> إدارة المستخدمين
        </Title>
        <Text type="secondary">إدارة شاملة للمستخدمين والموافقات والصلاحيات</Text>
      </div>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="إجمالي"
              value={stats.total}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="في الانتظار"
              value={stats.pending}
              prefix={<UserAddOutlined />}
              valueStyle={{ color: '#E8B84A' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="مقبول"
              value={stats.approved}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="محظور"
              value={stats.banned}
              prefix={<StopOutlined />}
              valueStyle={{ color: '#FF6B6B' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Filters */}
      <Card>
        <Space wrap>
          <Search
            placeholder="بحث بالاسم، اليوزر، أو RED ID"
            allowClear
            onSearch={(v) => { setSearch(v); load(0); }}
            style={{ width: 300 }}
            prefix={<SearchOutlined />}
          />
          <Select
            placeholder="الحالة"
            allowClear
            style={{ width: 150 }}
            onChange={setStatusFilter}
            value={statusFilter}
            options={Object.entries(STATUS_LABELS).map(([v, l]) => ({ value: v, label: l }))}
          />
          <Select
            placeholder="الدور"
            allowClear
            style={{ width: 150 }}
            onChange={setRoleFilter}
            value={roleFilter}
            options={[
              { value: 'USER', label: 'مستخدم' },
              { value: 'ADMIN', label: 'مسؤول' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()}>تحديث</Button>
        </Space>
      </Card>

      {/* Table */}
      <Card>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize: size,
            total,
            onChange: (p) => { setPage(p - 1); load(p - 1); },
            showSizeChanger: false,
          }}
          scroll={{ x: 1000 }}
        />
      </Card>

      {/* Reject Modal */}
      <Modal
        title={`رفض ${selectedUser?.username}`}
        open={rejectModalOpen}
        onCancel={() => setRejectModalOpen(false)}
        onOk={submitReject}
        okText="رفض"
        cancelText="إلغاء"
        okButtonProps={{ danger: true }}
      >
        <Paragraph>سيتم رفض تسجيل المستخدم وإرسال إشعار له.</Paragraph>
        <Form form={rejectForm} layout="vertical">
          <Form.Item name="reason" label="سبب الرفض" rules={[{ required: true, message: 'الرجاء إدخال السبب' }]}>
            <Input.TextArea rows={3} placeholder="مثال: معلومات غير صحيحة" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Ban Modal */}
      <Modal
        title={`حظر ${selectedUser?.username}`}
        open={banModalOpen}
        onCancel={() => setBanModalOpen(false)}
        onOk={submitBan}
        okText="حظر"
        cancelText="إلغاء"
        okButtonProps={{ danger: true }}
      >
        <Paragraph type="warning">
          سيتم حظر المستخدم من الوصول إلى المنصة.
        </Paragraph>
        <Form form={banForm} layout="vertical">
          <Form.Item name="reason" label="سبب الحظر" rules={[{ required: true, message: 'الرجاء إدخال السبب' }]}>
            <Input.TextArea rows={3} placeholder="مثال: انتهاك شروط الاستخدام" />
          </Form.Item>
          <Form.Item name="durationDays" label="مدة الحظر (بالأيام، اختياري)">
            <Input type="number" placeholder="اتركه فارغاً للحظر الدائم" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
