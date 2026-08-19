import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Descriptions, Divider, Empty, Form, Input,
  Modal, Popconfirm, Row, Select, Space, Switch, Table, Tag, Tooltip,
  Typography, message,
} from 'antd';
import {
  ForwardOutlined, PoweroffOutlined, ReloadOutlined, SettingOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * صفحة التحكم في المنافذ — Call Forward + Port Power.
 *
 * تتيح للمسؤول:
 * 1. إعادة توجيه المكالمات لكل منفذ (شروط مختلفة)
 * 2. تشغيل/إيقاف المنفذ (power on/off)
 * 3. عرض حالة المنفذ التفصيلية
 * 4. إعادة تعيين المنفذ
 */

type PortControl = {
  gatewayId: string;
  gatewayHost: string;
  gatewayName: string;
  portIndex: number;
  radioType: string;
  registrationState: string;
  callState: string;
  signalPercent: number | null;
  signalDbm: number | null;
  signalUsable: boolean;
  operator: string;
  powerState: boolean;
  callForwardState: string;
  callForwardNumber: string;
};

const CALL_FORWARD_PARAMS = [
  { value: 'Unconditional', label: 'كل المكالمات' },
  { value: 'Busy', label: 'عند الانشغال' },
  { value: 'NoReply', label: 'عند عدم الرد' },
  { value: 'Not_Reachable', label: 'عند عدم الوصول' },
  { value: 'CancelAll', label: 'إلغاء كل التحويلات' },
];

export default function PortControl() {
  const [ports, setPorts] = useState<PortControl[]>([]);
  const [loading, setLoading] = useState(false);
  const [forwardOpen, setForwardOpen] = useState(false);
  const [powerOpen, setPowerOpen] = useState(false);
  const [selectedPort, setSelectedPort] = useState<PortControl | null>(null);
  const [forwardForm] = Form.useForm();
  const [powerForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/api/admin/dinstar/port-control');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setPorts(Array.isArray(data) ? data : (data.ports || []));
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل حالة المنافذ');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // إعادة تعيين المنفذ
  const resetPort = async (port: PortControl) => {
    setSubmitting(true);
    try {
      const res = await apiFetch(`/api/admin/dinstar/ports/${port.portIndex}/reset`, { method: 'POST' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success(`أُرسل أمر إعادة تشغيل SIM ${port.portIndex + 1}`);
      setTimeout(load, 3000);
    } catch (e: any) {
      message.error(e.message || 'تعذر إعادة التشغيل');
    } finally {
      setSubmitting(false);
    }
  };

  // تشغيل/إيقاف المنفذ
  const togglePower = async () => {
    if (!selectedPort) return;
    setSubmitting(true);
    try {
      const powerOn = powerForm.getFieldValue('powerOn');
      const res = await apiFetch(`/api/admin/dinstar/ports/${selectedPort.portIndex}/power`, {
        method: 'POST',
        body: JSON.stringify({ on: powerOn }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success(powerOn ? 'شُغِّل المنفذ' : 'أُوقف المنفذ');
      setPowerOpen(false);
      setTimeout(load, 2000);
    } catch (e: any) {
      message.error(e.message || 'تعذر تنفيذ الأمر');
    } finally {
      setSubmitting(false);
    }
  };

  // تعيين Call Forward
  const setCallForward = async () => {
    if (!selectedPort) return;
    setSubmitting(true);
    try {
      const values = await forwardForm.validateFields();
      const res = await apiFetch(`/api/admin/dinstar/ports/${selectedPort.portIndex}/callforward`, {
        method: 'POST',
        body: JSON.stringify({
          param: values.param,
          number: values.number || '',
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success(values.param === 'CancelAll' ? 'أُلغيت كل التحويلات' : 'حُدِّث التحويل');
      setForwardOpen(false);
    } catch (e: any) {
      message.error(e.message || 'تعذر تعيين التحويل');
    } finally {
      setSubmitting(false);
    }
  };

  const openForward = (port: PortControl) => {
    setSelectedPort(port);
    forwardForm.resetFields();
    setForwardOpen(true);
  };

  const openPower = (port: PortControl) => {
    setSelectedPort(port);
    powerForm.setFieldsValue({ powerOn: port.powerState });
    setPowerOpen(true);
  };

  const columns = [
    {
      title: 'البوابة',
      width: 160,
      render: (_: any, r: PortControl) => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.gatewayName}</div>
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>{r.gatewayHost}</Typography.Text>
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
      title: 'الراديو',
      width: 70,
      dataIndex: 'radioType',
      render: (v: string) => <Tag>{v || '—'}</Tag>,
    },
    {
      title: 'التسجيل',
      width: 100,
      dataIndex: 'registrationState',
      render: (v: string) => {
        const state = (v || '').toUpperCase();
        return state === 'REGISTERED'
          ? <Tag color="success">مسجّل</Tag>
          : <Tag color="default">غير مسجّل</Tag>;
      },
    },
    {
      title: 'الإشارة',
      width: 80,
      dataIndex: 'signalPercent',
      render: (v: number | null, r: PortControl) => {
        if (v == null) return '—';
        const color = r.signalUsable
          ? (v >= 60 ? '#00C896' : v >= 30 ? '#E8B84A' : '#FA8C16')
          : '#F5222D';
        return (
          <span style={{ color, fontWeight: 600 }}>
            {v}% ({r.signalDbm ?? '?'} dBm)
          </span>
        );
      },
    },
    {
      title: 'الحالة',
      width: 100,
      dataIndex: 'callState',
      render: (v: string) => {
        const state = (v || '').toUpperCase();
        if (state === 'ACTIVE') return <Tag color="processing">نشط</Tag>;
        if (state === 'DIALING') return <Tag color="processing">يتصل</Tag>;
        if (state === 'RINGING') return <Tag color="warning">يرن</Tag>;
        return <Tag color="default">خامل</Tag>;
      },
    },
    {
      title: 'الطاقة',
      width: 70,
      dataIndex: 'powerState',
      render: (v: boolean) => v
        ? <Badge status="success" text="ON" />
        : <Badge status="error" text="OFF" />,
    },
    {
      title: 'التحويل',
      width: 120,
      render: (_: any, r: PortControl) => {
        if (!r.callForwardState || r.callForwardState === 'NONE') {
          return <Typography.Text type="secondary">لا يوجد</Typography.Text>;
        }
        return (
          <Tooltip title={r.callForwardNumber || ''}>
            <Tag color="orange">
              {r.callForwardState}
              {r.callForwardNumber ? ` → ${r.callForwardNumber}` : ''}
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: 'إجراءات',
      width: 200,
      fixed: 'right' as const,
      render: (_: any, r: PortControl) => (
        <Space>
          <Tooltip title="إعادة توجيه">
            <Button
              type="link"
              icon={<ForwardOutlined />}
              size="small"
              onClick={() => openForward(r)}
            />
          </Tooltip>
          <Tooltip title="الطاقة">
            <Button
              type="link"
              icon={<PoweroffOutlined />}
              size="small"
              style={{ color: r.powerState ? '#52C41A' : '#F5222D' }}
              onClick={() => openPower(r)}
            />
          </Tooltip>
          <Popconfirm
            title={`إعادة تشغيل SIM ${r.portIndex + 1}؟`}
            description="سيقطع أي مكالمة نشطة على هذا المنفذ."
            onConfirm={() => resetPort(r)}
            okText="إعادة تشغيل"
            cancelText="إلغاء"
            okButtonProps={{ danger: true }}
          >
            <Tooltip title="إعادة تشغيل">
              <Button type="link" icon={<ReloadOutlined />} size="small" loading={submitting} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      {/* إحصائيات سريعة */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#00C896' }}>
              {ports.filter((p) => (p.registrationState || '').toUpperCase() === 'REGISTERED').length}
            </div>
            <Typography.Text type="secondary">مسجّل</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#35CBE0' }}>
              {ports.filter((p) => (p.callState || '').toUpperCase() === 'ACTIVE').length}
            </div>
            <Typography.Text type="secondary">نشط الآن</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#52C41A' }}>
              {ports.filter((p) => p.powerState).length}
            </div>
            <Typography.Text type="secondary">طاقة ON</Typography.Text>
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small" style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 24, fontWeight: 700, color: '#E8B84A' }}>
              {ports.filter((p) => p.callForwardState && p.callForwardState !== 'NONE').length}
            </div>
            <Typography.Text type="secondary">محوّل</Typography.Text>
          </Card>
        </Col>
      </Row>

      {/* جدول المنافذ */}
      <Card
        title="التحكم في المنافذ"
        extra={<Button icon={<ReloadOutlined />} onClick={load} loading={loading}>تحديث</Button>}
      >
        <Table
          dataSource={ports}
          columns={columns}
          rowKey={(r) => `${r.gatewayId}-${r.portIndex}`}
          loading={loading}
          size="small"
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 16 }}
          locale={{ emptyText: <Empty description="لا توجد بوابات مسجّلة" /> }}
        />
      </Card>

      {/* نافذة إعادة التوجيه */}
      <Modal
        title={selectedPort ? `تحويل مكالمات SIM ${selectedPort.portIndex + 1}` : 'تحويل'}
        open={forwardOpen}
        onCancel={() => setForwardOpen(false)}
        onOk={setCallForward}
        okText="تعيين"
        cancelText="إلغاء"
        width={480}
      >
        <Form form={forwardForm} layout="vertical">
          <Form.Item
            name="param"
            label="نوع التحويل"
            rules={[{ required: true, message: 'اختر نوع التحويل' }]}
          >
            <Select options={CALL_FORWARD_PARAMS} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.param !== cur.param}>
            {({ getFieldValue }) => {
              const param = getFieldValue('param');
              if (param === 'CancelAll') return null;
              return (
                <Form.Item
                  name="number"
                  label="الرقم الوجهة"
                  rules={[{ required: true, message: 'أدخل الرقم' }]}
                >
                  <Input placeholder="777123456" />
                </Form.Item>
              );
            }}
          </Form.Item>
        </Form>
        <Alert
          type="warning"
          showIcon
          message="تحويل المكالمات يحدث على مستوى البوابة — سيُحوَّل كل الاتصال على هذا المنفذ."
          style={{ marginTop: 8 }}
        />
      </Modal>

      {/* نافذة الطاقة */}
      <Modal
        title={selectedPort ? `الطاقة — SIM ${selectedPort.portIndex + 1}` : 'الطاقة'}
        open={powerOpen}
        onCancel={() => setPowerOpen(false)}
        onOk={togglePower}
        okText="تطبيق"
        cancelText="إلغاء"
        width={420}
      >
        <Form form={powerForm} layout="vertical">
          <Form.Item name="powerOn" label="الطاقة" valuePropName="checked">
            <Switch checkedChildren="تشغيل" unCheckedChildren="إيقاف" />
          </Form.Item>
        </Form>
        <Alert
          type="warning"
          showIcon
          message="إيقاف المنفذ يقطع أي مكالمة نشطة ويعطل الاتصال حتى إعادة التشغيل."
          style={{ marginTop: 8 }}
        />
      </Modal>
    </div>
  );
}
