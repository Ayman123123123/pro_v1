import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ApiOutlined, EditOutlined, ReloadOutlined, SafetyCertificateOutlined, SignalFilled, ToolOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

type Port = { index: number; radioType?: string; status?: string; callState?: string; signal?: number; gprs?: string; numberMasked?: string; operator?: string };
type Discovery = { success: boolean; gatewayIp: string; model: string; status: string; portsDetected?: number; message?: string };
type InventoryPort = {
  gatewayId: string;
  gatewayName?: string;
  gatewayModel?: string;
  portIndex: number;
  operatorLabel?: string;
  simLabel?: string;
  verificationState?: string;
  verificationMethod?: string;
  msisdnMasked?: string;
  registrationState?: string;
  signalPercent?: number;
};

type InventoryForm = {
  operatorLabel?: string;
  simLabel?: string;
  verificationState: string;
  verificationMethod?: string;
  lastFourDigits?: string;
};

const readJson = async (response: Response) => {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(String(body.error || body.message || `HTTP ${response.status}`));
  return body;
};

const statusTag = (value?: string) => {
  const normalized = String(value || 'UNKNOWN').toUpperCase();
  const color = normalized === 'REGISTERED' || normalized === 'VERIFIED' ? 'success'
    : normalized === 'UNREGISTERED' || normalized === 'MISMATCH' ? 'error'
      : 'warning';
  return <Tag color={color}>{normalized}</Tag>;
};

/** DINSTAR operations console: current hardware state plus privacy-preserving SIM inventory. */
export default function DinstarControl() {
  const [ports, setPorts] = useState<Port[]>([]);
  const [inventory, setInventory] = useState<InventoryPort[]>([]);
  const [discovery, setDiscovery] = useState<Discovery | null>(null);
  const [capabilities, setCapabilities] = useState<Record<string, unknown>>({});
  const [loading, setLoading] = useState(false);
  const [ussdPort, setUssdPort] = useState<number | null>(null);
  const [ussd, setUssd] = useState('');
  const [editing, setEditing] = useState<InventoryPort | null>(null);
  const [form] = Form.useForm<InventoryForm>();

  const load = async () => {
    setLoading(true);
    try {
      const results = await Promise.allSettled([
        apiFetch('/api/admin/dinstar/discover').then(readJson),
        apiFetch('/api/admin/dinstar/capabilities').then(readJson),
        apiFetch('/api/admin/dinstar/status').then(readJson),
        apiFetch('/api/admin/dinstar/inventory').then(readJson),
      ]);
      const value = (index: number, fallback: unknown) => results[index].status === 'fulfilled' ? results[index].value : fallback;
      setDiscovery(value(0, null) as Discovery | null);
      setCapabilities(value(1, {}) as Record<string, unknown>);
      setPorts(Array.isArray(value(2, [])) ? value(2, []) as Port[] : []);
      setInventory(Array.isArray(value(3, [])) ? value(3, []) as InventoryPort[] : []);
      const failures = results.filter(result => result.status === 'rejected').length;
      if (failures) message.warning(`تعذر تحميل ${failures} مصدر بيانات؛ بقية الحالة معروضة.`);
    } catch (failure) {
      message.error(failure instanceof Error ? failure.message : 'تعذر الاتصال بالبوابة');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 15_000);
    return () => window.clearInterval(timer);
  }, []);

  const reset = (port: number) => Modal.confirm({
    title: `إعادة تشغيل وحدة المنفذ ${port + 1}`,
    content: 'سيتم قطع أي مكالمة نشطة على هذا المنفذ. الإجراء موثق في سجل التدقيق.',
    okType: 'danger',
    onOk: async () => {
      await readJson(await apiFetch(`/api/admin/dinstar/ports/${port}/reset`, { method: 'POST' }));
      message.success('تم إرسال Reset موثق للوحدة');
      window.setTimeout(() => void load(), 3_000);
    },
  });

  const sendUssd = async () => {
    if (ussdPort === null) return;
    try {
      await readJson(await apiFetch(`/api/admin/dinstar/ports/${ussdPort}/ussd`, { method: 'POST', body: JSON.stringify({ code: ussd }) }));
      message.success('تم إرسال USSD؛ استخدم واجهة DINSTAR الأصلية أو المسار المصرح لقراءة الرد عند الحاجة.');
      setUssdPort(null);
      setUssd('');
    } catch (failure) {
      message.error(failure instanceof Error ? failure.message : 'تعذر إرسال USSD');
    }
  };

  const editInventory = (entry: InventoryPort) => {
    setEditing(entry);
    form.setFieldsValue({
      operatorLabel: entry.operatorLabel,
      simLabel: entry.simLabel,
      verificationState: entry.verificationState || 'UNKNOWN',
      verificationMethod: entry.verificationMethod,
      lastFourDigits: entry.msisdnMasked?.replace(/^••••/, ''),
    });
  };

  const saveInventory = async () => {
    const entry = editing;
    if (!entry) return;
    const value = await form.validateFields();
    try {
      await readJson(await apiFetch(`/api/admin/dinstar/inventory/${entry.gatewayId}/ports/${entry.portIndex}`, {
        method: 'PUT',
        body: JSON.stringify({ ...value, lastFourDigits: value.lastFourDigits || null }),
      }));
      message.success('تم تحديث جرد الشريحة وتسجيل العملية في Audit Log');
      setEditing(null);
      form.resetFields();
      await load();
    } catch (failure) {
      message.error(failure instanceof Error ? failure.message : 'تعذر تحديث الجرد');
    }
  };

  const inventoryByPort = new Map(inventory.map(entry => [entry.portIndex, entry]));

  return <div style={{ padding: 20 }}>
    <Row justify="space-between" align="middle" gutter={[12, 12]}>
      <Col>
        <Typography.Title level={2} style={{ marginBottom: 0 }}>بوابة DINSTAR</Typography.Title>
        <Typography.Text type="secondary">جسر PSTN منفصل ومصرح به — لا يدخل في رسائل RED الخاصة أو مكالمات RED.</Typography.Text>
      </Col>
      <Col><Button loading={loading} icon={<ReloadOutlined />} onClick={() => void load()}>تحديث الحالة</Button></Col>
    </Row>

    <Alert
      style={{ margin: '14px 0' }}
      type={discovery?.success ? 'success' : 'warning'}
      showIcon
      message={discovery?.success ? `${discovery.model} متصل على ${discovery.gatewayIp}` : (discovery?.message || 'البوابة غير متصلة أو لم تكتمل المصادقة')}
      description="المكالمات لا تخرج إلا عبر Backend → Asterisk → PJSIP → DINSTAR. لا يوجد dial مباشر من لوحة الإدارة."
    />

    <Card title={<><SafetyCertificateOutlined /> حدود الأمان والقدرات</>} style={{ marginBottom: 16 }}>
      <Descriptions size="small" column={{ xs: 1, md: 3 }}>
        <Descriptions.Item label="Voice">Asterisk/PJSIP فقط</Descriptions.Item>
        <Descriptions.Item label="USSD">{capabilities.ussd ? 'مصرح ومدقق' : 'غير متاح'}</Descriptions.Item>
        <Descriptions.Item label="SIM IDs">{capabilities.simIdentifiersCollected ? 'جمع مصرح به' : 'غير مجمعة افتراضياً'}</Descriptions.Item>
        <Descriptions.Item label="Factory Reset"><Tag color="error">محظور من يونس</Tag></Descriptions.Item>
        <Descriptions.Item label="Firmware">واجهة الجهاز الأصلية فقط</Descriptions.Item>
        <Descriptions.Item label="إدارة الشبكة">VLAN / Access Rules في الجهاز</Descriptions.Item>
      </Descriptions>
    </Card>

    <Row gutter={[12, 12]}>
      {ports.map(port => {
        const entry = inventoryByPort.get(port.index);
        return <Col xs={24} sm={12} lg={6} key={port.index}>
          <Card
            title={`SIM ${port.index + 1}`}
            extra={statusTag(port.status)}
            actions={[
              <Button key="inventory" type="link" icon={<EditOutlined />} disabled={!entry} onClick={() => entry && editInventory(entry)}>جرد</Button>,
              <Button key="ussd" type="link" icon={<ApiOutlined />} onClick={() => { setUssdPort(port.index); setUssd(''); }}>USSD</Button>,
              <Button key="reset" type="link" danger icon={<ToolOutlined />} onClick={() => reset(port.index)}>Reset</Button>,
            ]}
          >
            <div style={{ textAlign: 'center' }}>
              <SignalFilled style={{ fontSize: 34, color: (port.signal || 0) > 55 ? '#00C896' : '#E8B84A' }} />
              <Progress percent={port.signal || 0} strokeColor="#00C896" />
              <Space wrap><Tag>{port.radioType || 'UNKNOWN'}</Tag><Tag color="blue">{port.callState || 'UNKNOWN'}</Tag><Tag>{port.gprs || 'UNKNOWN'}</Tag></Space>
            </div>
            <Descriptions column={1} size="small" style={{ marginTop: 10 }}>
              <Descriptions.Item label="المشغل">{entry?.operatorLabel || 'غير مصنف'}</Descriptions.Item>
              <Descriptions.Item label="وسم الشريحة">{entry?.simLabel || 'غير محدد'}</Descriptions.Item>
              <Descriptions.Item label="التحقق">{statusTag(entry?.verificationState)}</Descriptions.Item>
              <Descriptions.Item label="الرقم">{entry?.msisdnMasked || 'غير محفوظ'}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>;
      })}
    </Row>

    {!ports.length && <Card style={{ marginTop: 16 }}><Empty description="لا توجد بيانات منافذ؛ تحقق من عنوان البوابة وبيانات API ووصول الخادم إلى شبكة الإدارة." /></Card>}

    <Card title="جرد الشرائح" style={{ marginTop: 16 }}>
      <Table
        size="small"
        rowKey={entry => `${entry.gatewayId}-${entry.portIndex}`}
        dataSource={inventory}
        pagination={false}
        columns={[
          { title: 'البوابة', dataIndex: 'gatewayModel', render: (value: string) => value || 'DINSTAR' },
          { title: 'المنفذ', dataIndex: 'portIndex', render: (value: number) => `SIM ${value + 1}` },
          { title: 'المشغل', dataIndex: 'operatorLabel', render: (value: string) => value || 'غير مصنف' },
          { title: 'الحالة', dataIndex: 'verificationState', render: statusTag },
          { title: 'الإجراء', render: (_: unknown, entry: InventoryPort) => <Button size="small" icon={<EditOutlined />} onClick={() => editInventory(entry)}>وسم والتحقق</Button> },
        ]}
      />
    </Card>

    <Modal open={ussdPort !== null} title={`USSD — SIM ${(ussdPort ?? 0) + 1}`} onCancel={() => setUssdPort(null)} onOk={() => void sendUssd()} okButtonProps={{ disabled: !/^[*#0-9]{2,30}$/.test(ussd) }}>
      <Input value={ussd} onChange={event => setUssd(event.target.value)} placeholder="أدخل كوداً مؤكداً من المشغل فقط" />
      <Alert style={{ marginTop: 12 }} type="warning" message="USSD إجراء خارجي؛ لا تستخدم كوداً تخمينياً. محتوى الرد لا يسجل في Audit Log." />
    </Modal>

    <Modal open={editing !== null} title={`جرد SIM ${(editing?.portIndex ?? 0) + 1}`} onCancel={() => setEditing(null)} onOk={() => void saveInventory()} okText="حفظ موثق">
      <Form form={form} layout="vertical">
        <Form.Item name="operatorLabel" label="اسم المشغل" rules={[{ max: 50 }]}><Input placeholder="مثال: Sabafon أو Yemen Mobile" /></Form.Item>
        <Form.Item name="simLabel" label="وسم تشغيلي للشريحة" rules={[{ max: 80 }]}><Input placeholder="مثال: SABAFON-PRIMARY" /></Form.Item>
        <Form.Item name="verificationState" label="حالة التحقق" rules={[{ required: true }]}>
          <Select options={['UNKNOWN', 'PENDING', 'VERIFIED', 'MISMATCH', 'NOT_PRESENT'].map(value => ({ value, label: value }))} />
        </Form.Item>
        <Form.Item name="verificationMethod" label="طريقة التحقق"><Select allowClear options={['MANUAL', 'USSD', 'SMS', 'CALL'].map(value => ({ value, label: value }))} /></Form.Item>
        <Form.Item name="lastFourDigits" label="آخر 4 أرقام فقط" rules={[{ pattern: /^$|^[0-9]{4}$/, message: 'يسمح بآخر 4 أرقام فقط' }]}><Input maxLength={4} inputMode="numeric" placeholder="لا تدخل الرقم كاملاً" /></Form.Item>
        <Alert type="info" showIcon message="لا تحفظ الواجهة رقم الهاتف الكامل أو IMSI أو ICCID أو IMEI." />
      </Form>
    </Modal>
  </div>;
}
