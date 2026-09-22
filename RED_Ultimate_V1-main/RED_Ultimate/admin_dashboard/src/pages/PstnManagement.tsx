
import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert, Button, Card, Col, Form, Input, InputNumber, Modal, Row, Select,
  Space, Switch, Table, Tag, Typography, message,
} from 'antd';
import {
  EditOutlined, LinkOutlined, PhoneOutlined, ReloadOutlined, SearchOutlined,
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

type SimBinding = {
  userId: string;
  redId: string;
  username: string;
  gatewayId: string | null;
  gatewayHost: string | null;
  portIndex: number | null;
  number: string | null;
  bound: boolean;
};

type Gateway = {
  id: string;
  host?: string;
  name?: string;
  model?: string;
  portCount?: number;
};

type Page = {
  content: PstnUser[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

const STATUS_BADGE: Record<string, { color: string; ar: string }> = {
  APPROVED: { color: 'gold', ar: 'معتمد' },
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
  // نسخة الخادم العاملة بلا /api/master/v1/pstn/* — نسقط على /api/admin/users
  // (قراءة فقط: التبديل والحد اليومي معطّلان مع شرح السبب).
  const [fallbackMode, setFallbackMode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  // ── ربط SIM ──────────────────────────────────────────────
  const [bindings, setBindings] = useState<Record<string, SimBinding>>({});
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [bindingUser, setBindingUser] = useState<PstnUser | null>(null);
  const [bindingSaving, setBindingSaving] = useState(false);
  const [unbinding, setUnbinding] = useState<string | null>(null);
  const [bindForm] = Form.useForm();

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
      try {
        const b: Page = await json(await apiFetch(`/api/master/v1/pstn/users?${params}`));
        setData(b);
        setFallbackMode(false);
        return;
      } catch (masterErr) {
        // مسارات master/pstn غير موجودة في نسخة الخادم — بديل القراءة.
        const up = new URLSearchParams({ page: String(page), size: String(pageSize) });
        if (search.trim()) up.set('search', search.trim());
        const ub = await json(await apiFetch(`/api/admin/users?${up}`));
        const items = (Array.isArray(ub) ? ub : ub.content ?? []).map((u: any) => ({
          userId: u.id,
          redId: u.redId,
          username: u.username,
          displayName: u.displayName,
          pstnEnabled: !!u.pstnEnabled,
          pstnDailyLimit: 0,
          usedToday: 0,
          accountStatus: u.status,
          role: u.role,
        }));
        const content = enabledFilter === 'all'
          ? items
          : items.filter((u: PstnUser) => enabledFilter === 'enabled' ? u.pstnEnabled : !u.pstnEnabled);
        setData({
          content,
          totalElements: Number(ub.totalElements ?? content.length),
          totalPages: Number(ub.totalPages ?? 1),
          number: Number(ub.page ?? page),
          size: Number(ub.size ?? pageSize),
        });
        setFallbackMode(true);
      }
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل قائمة المستخدمين');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, search, enabledFilter]);

  useEffect(() => { load(); }, [load]);
  // تحديث دوري خفيف كل 30 ثانية لالتقاط استهلاك اليوم المتغيّر.
  usePolling(load, 30000);

  const loadBindings = useCallback(async () => {
    try {
      const [b, g] = await Promise.all([
        json(await apiFetch('/api/admin/dinstar/bindings')).catch(() => []),
        json(await apiFetch('/api/admin/dinstar/fleet')).catch(() => []),
      ]);
      const map: Record<string, SimBinding> = {};
      (Array.isArray(b) ? b : []).forEach((x: SimBinding) => { map[x.userId] = x; });
      setBindings(map);
      setGateways(Array.isArray(g) ? g : []);
    } catch {
      // الربط غير متاح في نسخ قديمة — تبقى الأزرار معطلة بهدوء
    }
  }, []);

  useEffect(() => { loadBindings(); }, [loadBindings]);

  const openBind = (user: PstnUser) => {
    setBindingUser(user);
    const cur = bindings[user.userId];
    bindForm.setFieldsValue({
      gatewayId: cur?.gatewayId,
      portIndex: cur?.portIndex ?? 0,
      number: cur?.number ?? '',
    });
  };

  const saveBind = async () => {
    if (!bindingUser) return;
    try {
      const v = await bindForm.validateFields();
      setBindingSaving(true);
      const b = await json(await apiFetch('/api/admin/dinstar/bindings', {
        method: 'POST',
        body: JSON.stringify({
          userId: bindingUser.userId,
          gatewayId: v.gatewayId,
          portIndex: v.portIndex,
          ...(v.number?.trim() ? { number: v.number.trim() } : {}),
        }),
      }));
      message.success(`رُبط ${bindingUser.username} بالمنفذ ${b.portIndex} على ${b.gatewayHost ?? ''}`);
      setBindingUser(null);
      loadBindings();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.message || 'تعذر الربط — قد يكون المنفذ محجوزاً');
    } finally {
      setBindingSaving(false);
    }
  };

  const unbind = async (user: PstnUser) => {
    setUnbinding(user.userId);
    try {
      await json(await apiFetch(`/api/admin/dinstar/bindings/${user.userId}`, { method: 'DELETE' }));
      message.success(`فُك ربط ${user.username}`);
      loadBindings();
    } catch (e: any) {
      message.error(e.message || 'تعذر فك الربط');
    } finally {
      setUnbinding(null);
    }
  };

  const toggle = async (user: PstnUser) => {
    if (fallbackMode) {
      message.warning('التبديل غير مدعوم في نسخة الخادم الحالية (بلا /api/master/v1/pstn/*) — وضع القراءة فقط');
      return;
    }
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
    if (fallbackMode) {
      message.warning('حفظ الحد اليومي غير مدعوم في نسخة الخادم الحالية (بلا /api/master/v1/pstn/*) — وضع القراءة فقط');
      return;
    }
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
          disabled={fallbackMode}
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
        const color = ratio >= 1 ? 'red' : ratio >= 0.8 ? 'orange' : 'gold';
        return <Tag color={color}>{u.usedToday} / {u.pstnDailyLimit > 0 ? u.pstnDailyLimit : '∞'}</Tag>;
      },
    },
    {
      title: 'ربط SIM',
      width: 170,
      render: (_: any, u: PstnUser) => {
        const b = bindings[u.userId];
        if (!u.pstnEnabled) return <Tag>—</Tag>;
        if (b?.bound) {
          return (
            <Space direction="vertical" size={2}>
              <Tag color="green">منفذ {b.portIndex} · {b.gatewayHost ?? ''}</Tag>
              {b.number && <Typography.Text type="secondary" style={{ fontSize: 11 }}>{b.number}</Typography.Text>}
            </Space>
          );
        }
        return <Tag color="default">غير مربوط</Tag>;
      },
    },
    {
      title: 'إجراءات',
      key: 'actions',
      width: 210,
      render: (_: any, u: PstnUser) => (
        <Space size={4}>
          <Button
            size="small"
            icon={<EditOutlined />}
            disabled={!u.pstnEnabled || fallbackMode}
            onClick={() => openEdit(u)}
          >
            الحد اليومي
          </Button>
          <Button
            size="small"
            icon={<LinkOutlined />}
            disabled={!u.pstnEnabled}
            loading={unbinding === u.userId}
            onClick={() => (bindings[u.userId]?.bound ? unbind(u) : openBind(u))}
          >
            {bindings[u.userId]?.bound ? 'فك الربط' : 'ربط SIM'}
          </Button>
        </Space>
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

      {fallbackMode && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="وضع القراءة فقط"
          description="نسخة الخادم العاملة لا توفر مسارات /api/master/v1/pstn/* — تُعرض القائمة من /api/admin/users (حالة PSTN فقط بلا استهلاك يومي)، والتبديل وتعديل الحد اليومي معطّلان حتى ترقية الخادم."
        />
      )}

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

      <Modal
        title={`ربط شريحة — ${bindingUser?.displayName ?? ''} (@${bindingUser?.username ?? ''})`}
        open={!!bindingUser}
        onCancel={() => setBindingUser(null)}
        onOk={saveBind}
        confirmLoading={bindingSaving}
        okText="ربط"
        cancelText="إلغاء"
        destroyOnClose
      >
        <Form form={bindForm} layout="vertical">
          <Form.Item
            name="gatewayId"
            label="البوابة"
            rules={[{ required: true, message: 'اختر البوابة' }]}
          >
            <Select
              placeholder={gateways.length ? 'اختر البوابة' : 'لا توجد بوابات مسجلة'}
              options={gateways.map((g) => ({
                value: g.id,
                label: `${g.host ?? g.id}${g.model ? ` · ${g.model}` : ''}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="portIndex"
            label="رقم المنفذ (0-31)"
            rules={[{ required: true, message: 'أدخل رقم المنفذ' }]}
          >
            <InputNumber min={0} max={31} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="number"
            label="رقم الشريحة (اختياري)"
            extra="6-20 رقماً — يظهر كهوية المتصل الصادرة."
            rules={[{ pattern: /^\d{6,20}$/, message: '6-20 رقماً فقط' }]}
          >
            <Input placeholder="7120XXXXX" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
