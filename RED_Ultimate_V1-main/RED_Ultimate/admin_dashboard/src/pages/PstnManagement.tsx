
import React, { useCallback, useEffect, useState } from 'react';
import {
  Button, Card, Col, Form, Input, InputNumber, Modal, Row, Select,
  Space, Switch, Table, Tag, Typography, message,
} from 'antd';
import {
  EditOutlined, PhoneOutlined, ReloadOutlined, SearchOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';
import { usePolling } from '../hooks/usePolling';

/**
 * إدارة خدمة PSTN — تفعيل/تعطيل المكالمات الهاتفية عبر بوابات DINSTAR
 * لكل مستخدم، مع ضبط الحد اليومي ومراقبة الاستهلاك.
 *
 * الواجهة تخاطب:
 *   GET    /api/master/v1/pstn/users              قائمة المستخدمين
 *   PATCH  /api/master/v1/pstn/users/{userId}     تحديث (تفعيل + حد يومي)
 *   POST   /api/master/v1/pstn/users/{userId}/toggle  تبديل سريع
 */

type PstnUser = {
  userId: string;
  redId: string;
  username: string;
  displayName: string;
  pstnEnabled: boolean;
  pstnDailyLimit: number;
  usedToday: number;
  accountStatus: string;
  role: string;
};

type Page = {
  content: PstnUser[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

const STATUS_BADGE: Record<string, { color: string; ar: string }> = {
  APPROVED: { color: 'green', ar: 'معتمد' },
  PENDING: { color: 'gold', ar: 'قيد المراجعة' },
  REJECTED: { color: 'red', ar: 'مرفوض' },
  SUSPENDED: { color: 'orange', ar: 'معلق' },
  BANNED: { color: 'red', ar: 'محظور' },
};

const ROLE_BADGE: Record<string, { color: string; ar: string }> = {
  ADMIN: { color: 'red', ar: 'مدير' },
  USER: { color: 'blue', ar: 'مستخدم' },
};

export default function PstnManagement() {
  const [data, setData] = useState<Page | null>(null);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [enabledFilter, setEnabledFilter] = useState<'all' | 'enabled' | 'disabled'>('all');
  const [toggling, setToggling] = useState<string | null>(null);
  const [editing, setEditing] = useState<PstnUser | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const json = async (r: Response) => {
    const b = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(b?.error || b?.message || `HTTP ${r.status}`);
    return b;
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        page: String(page),
        size: String(pageSize),
      });
      if (search.trim()) params.set('search', search.trim());
      if (enabledFilter !== 'all') params.set('pstnEnabled', String(enabledFilter === 'enabled'));
      const b: Page = await json(await apiFetch(`/api/master/v1/pstn/users?${params}`));
      setData(b);
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل قائمة المستخدمين');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, enabledFilter]);

  useEffect(() => { load(); }, [load]);
  // تحديث دوري خفيف كل 30 ثانية لالتقاط استهلاك اليوم المتغيّر.
  usePolling(load, 30000);

  const toggle = async (user: PstnUser) => {
    setToggling(user.userId);
    try {
      const b = await json(await apiFetch(`/api/master/v1/pstn/users/${user.userId}/toggle`, { method: 'POST' }));
      setData((prev) => prev && {
        ...prev,
        content: prev.content.map((u) => (u.userId === user.userId ? { ...u, pstnEnabled: b.pstnEnabled } : u)),
      });
      message.success(b.pstnEnabled
        ? `فُعّلت مكالمات PSTN للمستخدم ${user.username}`
        : `عُطّلت مكالمات PSTN للمستخدم ${user.username}`);
    } catch (e: any) {
      message.error(e.message);
    } finally {
      setToggling(null);
    }
  };

  const openEdit = (user: PstnUser) => {
    setEditing(user);
    form.setFieldsValue({ pstnDailyLimit: user.pstnDailyLimit });
  };

  const saveEdit = async () => {
    if (!editing) return;
    try {
      const { pstnDailyLimit } = await form.validateFields();
      setSaving(true);
      const b = await json(await apiFetch(`/api/master/v1/pstn/users/${editing.userId}`, {
        method: 'PATCH',
        body: JSON.stringify({ pstnEnabled: editing.pstnEnabled, pstnDailyLimit }),
      }));
      message.success(`حُدّث الحد اليومي إلى ${b.pstnDailyLimit}`);
      setEditing(null);
      load();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.message);
    } finally {
      setSaving(false);
    }
  };

  const enabledCount = data?.content.filter((u) => u.pstnEnabled).length ?? 0;

  const columns = [
    {
      title: 'المستخدم',
      render: (_: any, u: PstnUser) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{u.displayName}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>@{u.username} · {u.redId}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'حالة الحساب',
      dataIndex: 'accountStatus',
      width: 110,
      render: (v: string) => {
        const s = STATUS_BADGE[v] || { color: 'default', ar: v };
        return <Tag color={s.color}>{s.ar}</Tag>;
      },
    },
    {
      title: 'الدور',
      dataIndex: 'role',
      width: 90,
      render: (v: string) => {
        const r = ROLE_BADGE[v] || { color: 'default', ar: v };
        return <Tag color={r.color}>{r.ar}</Tag>;
      },
    },
    {
      title: 'خدمة PSTN',
      dataIndex: 'pstnEnabled',
      width: 110,
      render: (enabled: boolean, u: PstnUser) => (
        <Switch
          checked={enabled}
          loading={toggling === u.userId}
          onChange={() => toggle(u)}
        />
      ),
    },
    {
      title: 'الحد اليومي',
      dataIndex: 'pstnDailyLimit',
      width: 100,
      align: 'center' as const,
      render: (v: number) => (v > 0 ? <Tag color="blue">{v}</Tag> : <Tag>غير محدود</Tag>),
    },
    {
      title: 'استهلاك اليوم',
      width: 120,
      align: 'center' as const,
      render: (_: any, u: PstnUser) => {
        if (!u.pstnEnabled) return <Tag>—</Tag>;
        const ratio = u.pstnDailyLimit > 0 ? u.usedToday / u.pstnDailyLimit : 0;
        const color = ratio >= 1 ? 'red' : ratio >= 0.8 ? 'orange' : 'green';
        return <Tag color={color}>{u.usedToday} / {u.pstnDailyLimit > 0 ? u.pstnDailyLimit : '∞'}</Tag>;
      },
    },
    {
      title: 'إجراءات',
      key: 'actions',
      width: 120,
      render: (_: any, u: PstnUser) => (
        <Button
          size="small"
          icon={<EditOutlined />}
          disabled={!u.pstnEnabled}
          onClick={() => openEdit(u)}
        >
          الحد اليومي
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 20 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <div>
          <Typography.Title level={2} style={{ marginBottom: 4 }}>
            <PhoneOutlined /> إدارة خدمة PSTN
          </Typography.Title>
          <Typography.Text type="secondary">
            تفعيل المكالمات الهاتفية عبر بوابات DINSTAR GSM لكل مستخدم، مع حدود يومية ومراقبة الاستهلاك.
          </Typography.Text>
        </div>
        <Button loading={loading} icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
      </Row>

      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={10}>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="بحث بالاسم أو اسم المستخدم أو Red ID..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          />
        </Col>
        <Col xs={12} md={7}>
          <Select
            style={{ width: '100%' }}
            value={enabledFilter}
            onChange={(v) => { setEnabledFilter(v); setPage(0); }}
            options={[
              { value: 'all', label: 'كل المستخدمين' },
              { value: 'enabled', label: 'PSTN مفعّل' },
              { value: 'disabled', label: 'PSTN معطّل' },
            ]}
          />
        </Col>
        <Col xs={12} md={7} style={{ textAlign: 'left' }}>
          <Typography.Text type="secondary">
            مفعّل في هذه الصفحة: {enabledCount} / {data?.content.length ?? 0}
          </Typography.Text>
        </Col>
      </Row>

      <Card>
        <Table
          rowKey="userId"
          size="middle"
          loading={loading}
          dataSource={data?.content ?? []}
          columns={columns}
          locale={{ emptyText: 'لا توجد نتائج' }}
          pagination={{
            current: (data?.number ?? 0) + 1,
            pageSize: data?.size ?? pageSize,
            total: data?.totalElements ?? 0,
            showSizeChanger: true,
            showTotal: (total) => `إجمالي ${total} مستخدم`,
            onChange: (p, s) => { setPage(p - 1); setPageSize(s); },
          }}
        />
      </Card>

      <Modal
        title={`الحد اليومي للمكالمات — ${editing?.displayName ?? ''}`}
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={saveEdit}
        confirmLoading={saving}
        okText="حفظ"
        cancelText="إلغاء"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="pstnDailyLimit"
            label="عدد المكالمات المسموح بها يوميًا"
            extra="0 يعني غير محدود (حسب سياسة الخادم الافتراضية)."
            rules={[{ required: true, message: 'أدخل الحد اليومي' }]}
          >
            <InputNumber min={0} max={1000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
