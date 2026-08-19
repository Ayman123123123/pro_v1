import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Descriptions, Empty, Form, Input, Modal,
  Popconfirm, Row, Select, Space, Table, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  CheckCircleOutlined, CloseCircleOutlined, EditOutlined, ExclamationCircleOutlined,
  ReloadOutlined, SafetyCertificateOutlined, SearchOutlined, SignalFilled,
} from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * صفحة جرد شرائح SIM — إدارة تسميات الشرائح وحالة التحقق.
 *
 * تتيح للمسؤول:
 * 1. رؤية كل شريحة في كل بوابة مع حالتها الحالية
 * 2. تعيين تسمية للمشغل واسم الشريحة (بدون تخزين MSISDN كامل)
 * 3. التحقق من الشريحة عبر USSD أو SMS أو مكالمة
 * 4. تخزين آخر 4 أرقام فقط من الرقم (مع قناع ••••)
 */

type SimPort = {
  gatewayId: string;
  gatewayName: string;
  gatewayModel: string;
  gatewayHost: string;
  portIndex: number;
  radioType: string | null;
  registrationState: string | null;
  callState: string | null;
  signalPercent: number | null;
  operatorLabel: string | null;
  simLabel: string | null;
  verificationState: string;
  verificationMethod: string | null;
  msisdnMasked: string | null;
  verifiedAt: string | null;
};

const VERIFICATION_COLORS: Record<string, string> = {
  UNKNOWN: 'default',
  PENDING: 'processing',
  VERIFIED: 'success',
  MISMATCH: 'error',
  NOT_PRESENT: 'warning',
};

const VERIFICATION_LABELS: Record<string, string> = {
  UNKNOWN: 'غير محقّق',
  PENDING: 'قيد التحقق',
  VERIFIED: 'محقّق',
  MISMATCH: 'عدم تطابق',
  NOT_PRESENT: 'غير موجودة',
};

const METHOD_LABELS: Record<string, string> = {
  MANUAL: 'يدوي',
  USSD: 'USSD',
  SMS: 'SMS',
  CALL: 'مكالمة',
};

const YEMEN_OPERATORS = [
  { value: 'Sabafon', label: 'سبأفون' },
  { value: 'YOU', label: 'يو (MTN سابقًا)' },
  { value: 'YemenMobile', label: 'يمن موبايل' },
  { value: 'YTelecom', label: 'واي (Y Telecom)' },
];

export default function SimInventory() {
  const [ports, setPorts] = useState<SimPort[]>([]);
  const [loading, setLoading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingPort, setEditingPort] = useState<SimPort | null>(null);
  const [form] = Form.useForm();
  const [search, setSearch] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/api/admin/dinstar/sim-inventory');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setPorts(Array.isArray(data) ? data : []);
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل جرد الشرائح');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => {
    if (!search) return ports;
    const s = search.toLowerCase();
    return ports.filter((p) =>
      p.gatewayName.toLowerCase().includes(s) ||
      p.gatewayHost.includes(s) ||
      (p.simLabel || '').toLowerCase().includes(s) ||
      (p.operatorLabel || '').toLowerCase().includes(s) ||
      `SIM ${p.portIndex + 1}`.toLowerCase().includes(s)
    );
  }, [ports, search]);

  const openEdit = (port: SimPort) => {
    setEditingPort(port);
    form.setFieldsValue({
      operatorLabel: port.operatorLabel || '',
      simLabel: port.simLabel || '',
      verificationState: port.verificationState,
      verificationMethod: port.verificationMethod || null,
      lastFourDigits: port.msisdnMasked ? port.msisdnMasked.replace('••••', '') : '',
    });
    setEditOpen(true);
  };

  const saveEdit = async () => {
    if (!editingPort) return;
    try {
      const values = form.getFieldsValue();
      const res = await apiFetch(
        `/api/admin/dinstar/sim-inventory/${editingPort.gatewayId}/${editingPort.portIndex}`,
        {
          method: 'PUT',
          body: JSON.stringify({
            operatorLabel: values.operatorLabel || null,
            simLabel: values.simLabel || null,
            verificationState: values.verificationState,
            verificationMethod: values.verificationMethod || null,
            lastFourDigits: values.lastFourDigits || null,
          }),
        },
      );
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      message.success('حُدّثت بيانات الشريحة');
      setEditOpen(false);
      load();
    } catch (e: any) {
      message.error(e.message || 'تعذر الحفظ');
    }
  };

  // إحصائيات
  const stats = useMemo(() => ({
    total: ports.length,
    verified: ports.filter((p) => p.verificationState === 'VERIFIED').length,
    pending: ports.filter((p) => p.verificationState === 'PENDING').length,
    mismatch: ports.filter((p) => p.verificationState === 'MISMATCH').length,
    notPresent: ports.filter((p) => p.verificationState === 'NOT_PRESENT').length,
    unknown: ports.filter((p) => p.verificationState === 'UNKNOWN').length,
    registered: ports.filter((p) => (p.registrationState || '').toUpperCase() === 'REGISTERED').length,
    withSignal: ports.filter((p) => (p.signalPercent ?? 0) > 0).length,
  }), [ports]);

  const columns = [
    {
      title: 'البوابة',
      width: 180,
      render: (_: any, r: SimPort) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.gatewayName}</div>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
            {r.gatewayHost} · {r.gatewayModel}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: 'SIM',
      width: 70,
      dataIndex: 'portIndex',
      render: (v: number) => (
        <Tag color="blue" style={{ fontWeight: 700 }}>SIM {v + 1}</Tag>
      ),
    },
    {
      title: 'الشبكة',
      width: 80,
      dataIndex: 'radioType',
      render: (v: string | null) => v ? <Tag>{v}</Tag> : '—',
    },
    {
      title: 'التسجيل',
      width: 110,
      dataIndex: 'registrationState',
      render: (v: string | null) => {
        const state = (v || '').toUpperCase();
        return state === 'REGISTERED'
          ? <Tag icon={<CheckCircleOutlined />} color="success">مسجّل</Tag>
          : <Tag icon={<CloseCircleOutlined />} color="default">غير مسجّل</Tag>;
      },
    },
    {
      title: 'الإشارة',
      width: 90,
      dataIndex: 'signalPercent',
      render: (v: number | null) => {
        if (v == null) return '—';
        const color = v >= 60 ? '#00C896' : v >= 30 ? '#E0A83C' : '#F5222D';
        return (
          <Space size={2}>
            <SignalFilled style={{ color, fontSize: 12 }} />
            <span style={{ color }}>{v}%</span>
          </Space>
        );
      },
    },
    {
      title: 'المشغل',
      width: 100,
      dataIndex: 'operatorLabel',
      render: (v: string | null) => v || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'تسمية الشريحة',
      width: 130,
      dataIndex: 'simLabel',
      render: (v: string | null) => v || <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'الرقم',
      width: 100,
      dataIndex: 'msisdnMasked',
      render: (v: string | null) => (
        v ? <Typography.Text code>{v}</Typography.Text>
          : <Typography.Text type="secondary">—</Typography.Text>
      ),
    },
    {
      title: 'التحقق',
      width: 110,
      dataIndex: 'verificationState',
      render: (v: string, r: SimPort) => (
        <Tooltip title={r.verificationMethod ? `عبر ${METHOD_LABELS[r.verificationMethod] || r.verificationMethod}` : undefined}>
          <Tag color={VERIFICATION_COLORS[v] || 'default'}>
            {VERIFICATION_LABELS[v] || v}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: 'إجراءات',
      width: 80,
      fixed: 'right' as const,
      render: (_: any, r: SimPort) => (
        <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(r)}>
          تعديل
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* إحصائيات */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#00C896' }}>{stats.total}</div>
            <Typography.Text type="secondary">إجمالي المنافذ</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#52C41A' }}>{stats.verified}</div>
            <Typography.Text type="secondary">محقّقة</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#E0A83C' }}>{stats.pending}</div>
            <Typography.Text type="secondary">قيد التحقق</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#1890FF' }}>{stats.registered}</div>
            <Typography.Text type="secondary">مسجّلة على الشبكة</Typography.Text>
          </Card>
        </Col>
      </Row>

      {/* الجدول */}
      <Card
        title="جرد شرائح SIM"
        extra={
          <Space>
            <Input
              placeholder="بحث..."
              prefix={<SearchOutlined />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>تحديث</Button>
          </Space>
        }
      >
        <Table
          dataSource={filtered}
          columns={columns}
          rowKey={(r) => `${r.gatewayId}-${r.portIndex}`}
          loading={loading}
          size="small"
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          locale={{ emptyText: <Empty description="لا توجد شرائح مسجّلة — ابدأ باكتشاف البوابات" /> }}
        />
      </Card>

      {/* نافذة التعديل */}
      <Modal
        title={editingPort ? `تعديل شريحة — ${editingPort.gatewayName} — SIM ${editingPort.portIndex + 1}` : 'تعديل'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={saveEdit}
        okText="حفظ"
        cancelText="إلغاء"
        width={560}
      >
        <Form form={form} layout="vertical" initialValues={{ verificationState: 'UNKNOWN' }}>
          <Form.Item name="operatorLabel" label="المشغل">
            <Select
              allowClear
              placeholder="اختر المشغل"
              options={YEMEN_OPERATORS}
            />
          </Form.Item>
          <Form.Item name="simLabel" label="تسمية الشريحة">
            <Input placeholder="مثال: شريحة رئيسية، شريحة احتياطية" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="verificationState" label="حالة التحقق">
                <Select
                  options={Object.entries(VERIFICATION_LABELS).map(([v, l]) => ({ value: v, label: l }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="verificationMethod" label="طريقة التحقق">
                <Select
                  allowClear
                  placeholder="اختر الطريقة"
                  options={Object.entries(METHOD_LABELS).map(([v, l]) => ({ value: v, label: l }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="lastFourDigits"
            label="آخر 4 أرقام من الرقم"
            rules={[{ pattern: /^[0-9]{0,4}$/, message: '4 أرقام فقط' }]}
            tooltip="يُخزَّن masked: ••••1234 — لا يُخزَّن الرقم كاملًا إطلاقًا"
          >
            <Input placeholder="1234" maxLength={4} />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="يُخزَّن فقط آخر 4 أرقام من رقم الهاتف مع قناع •••• لحماية الخصوصية. لا يمكن استعادة الرقم الكامل من البيانات المخزّنة."
          />
        </Form>
      </Modal>
    </div>
  );
}
