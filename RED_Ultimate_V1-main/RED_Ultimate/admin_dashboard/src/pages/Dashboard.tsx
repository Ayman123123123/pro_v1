import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  PhoneOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';

const { Title, Text } = Typography;

type Json = Record<string, unknown>;
type GatewayPort = {
  index?: number;
  radioType?: string;
  status?: string;
  callState?: string;
  signal?: number;
  gprs?: string;
  operator?: string;
};
type AuditEvent = {
  id?: string;
  action?: string;
  targetId?: string;
  createdAt?: string;
};
type GatewayInventoryPort = {
  gatewayId?: string;
  gatewayName?: string;
  gatewayModel?: string;
  portIndex?: number;
  operatorLabel?: string;
  simLabel?: string;
  verificationState?: string;
  verificationMethod?: string;
  msisdnMasked?: string;
  registrationState?: string;
  signalPercent?: number;
};

type DashboardSnapshot = {
  monitor: Json;
  realtime: Json;
  calls: Json;
  ports: GatewayPort[];
  audit: AuditEvent[];
  inventory: GatewayInventoryPort[];
};

const emptySnapshot: DashboardSnapshot = { monitor: {}, realtime: {}, calls: {}, ports: [], audit: [], inventory: [] };

const number = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
};

const statusTag = (value: unknown) => {
  const normalized = String(value ?? 'UNKNOWN').toUpperCase();
  const color = normalized === 'UP' || normalized === 'REGISTERED' || normalized === 'ONLINE'
    ? 'success'
    : normalized === 'DOWN' || normalized === 'OFFLINE' || normalized === 'UNREGISTERED'
      ? 'error'
      : 'warning';
  return <Tag color={color}>{normalized}</Tag>;
};

async function readJson(response: Response): Promise<Json> {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(String(body.message || body.error || `HTTP ${response.status}`));
  return body as Json;
}

/** Real-time local operations dashboard. It intentionally never fills operational cards with mock data. */
export default function Dashboard() {
  const [snapshot, setSnapshot] = useState<DashboardSnapshot>(emptySnapshot);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const results = await Promise.allSettled([
        apiFetch('/api/admin/monitor/stats').then(readJson),
        apiFetch('/api/master/v1/stats/realtime').then(readJson),
        apiFetch('/api/master/v1/media/active-calls').then(readJson),
        apiFetch('/api/admin/dinstar/status').then(readJson),
        apiFetch('/api/admin/audit').then(readJson),
        apiFetch('/api/admin/dinstar/inventory').then(readJson),
      ]);
      const value = (index: number, fallback: Json | unknown[] = {}) => {
        const result = results[index];
        return result.status === 'fulfilled' ? result.value : fallback;
      };
      const failures = results.filter((result) => result.status === 'rejected');
      setSnapshot({
        monitor: value(0) as Json,
        realtime: value(1) as Json,
        calls: value(2) as Json,
        ports: Array.isArray(value(3, [])) ? value(3, []) as GatewayPort[] : [],
        audit: Array.isArray(value(4, [])) ? value(4, []) as AuditEvent[] : [],
        inventory: Array.isArray(value(5, [])) ? value(5, []) as GatewayInventoryPort[] : [],
      });
      if (failures.length) setError(`تعذر تحميل ${failures.length} مصدر بيانات؛ بقية بيانات العمليات ما زالت معروضة.`);
      setUpdatedAt(new Date());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'تعذر تحميل بيانات المنظومة');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 30_000);
    return () => window.clearInterval(timer);
  }, [load]);

  const activeUsers = number(snapshot.realtime.active_users ?? snapshot.monitor.active_users);
  const totalMessages = number(snapshot.monitor.total_messages);
  const messages24h = number(snapshot.realtime.messages_24h);
  const pendingApprovals = number(snapshot.realtime.pending_approvals);
  const activeCalls = number(snapshot.calls.active_calls);
  const deliveryRate = number(snapshot.realtime.delivery_rate_percent);
  const memoryPercent = number(snapshot.realtime.jvm_memory_percent ?? snapshot.monitor.jvm_memory_percent);
  const dbHealth = snapshot.realtime.db_health ?? 'UNKNOWN';

  const portColumns = [
    { title: 'المنفذ', dataIndex: 'index', key: 'index', render: (value: number | undefined) => `SIM ${(value ?? 0) + 1}` },
    { title: 'الراديو', dataIndex: 'radioType', key: 'radioType', render: (value: string | undefined) => value || '—' },
    { title: 'التسجيل', dataIndex: 'status', key: 'status', render: statusTag },
    { title: 'الإشارة', dataIndex: 'signal', key: 'signal', render: (value: number | undefined) => <Progress percent={number(value)} size="small" status={number(value) < 20 ? 'exception' : 'normal'} /> },
    { title: 'الحالة', dataIndex: 'callState', key: 'callState', render: (value: string | undefined) => value || '—' },
  ];

  const auditColumns = [
    { title: 'الإجراء', dataIndex: 'action', key: 'action', render: (value: string | undefined) => <Text strong>{value || 'UNKNOWN'}</Text> },
    { title: 'الهدف', dataIndex: 'targetId', key: 'targetId', render: (value: string | undefined) => value || '—' },
    { title: 'الوقت', dataIndex: 'createdAt', key: 'createdAt', render: (value: string | undefined) => value ? new Date(value).toLocaleString('ar-SA') : '—' },
  ];

  const inventoryColumns = [
    { title: 'البوابة', dataIndex: 'gatewayName', key: 'gatewayName', render: (value: string | undefined, row: GatewayInventoryPort) => <Space direction="vertical" size={0}><Text strong>{value || 'DINSTAR'}</Text><Text type="secondary">{row.gatewayModel || '—'}</Text></Space> },
    { title: 'المنفذ', dataIndex: 'portIndex', key: 'portIndex', render: (value: number | undefined) => `SIM ${(value ?? 0) + 1}` },
    { title: 'المشغل / الوسم', key: 'label', render: (_: unknown, row: GatewayInventoryPort) => <Space direction="vertical" size={0}><Text>{row.operatorLabel || 'غير مصنف'}</Text><Text type="secondary">{row.simLabel || 'لا يوجد وسم'}</Text></Space> },
    { title: 'التحقق', dataIndex: 'verificationState', key: 'verificationState', render: statusTag },
    { title: 'الرقم', dataIndex: 'msisdnMasked', key: 'msisdnMasked', render: (value: string | undefined) => value || 'غير محفوظ' },
    { title: 'الشبكة', key: 'network', render: (_: unknown, row: GatewayInventoryPort) => <Space direction="vertical" size={0}>{statusTag(row.registrationState)}<Progress percent={number(row.signalPercent)} size="small" /></Space> },
  ];

  return (
    <div style={{ direction: 'rtl' }}>
      <Space direction="vertical" size={18} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
          <div>
            <Title level={2} style={{ color: '#E8B84A', margin: 0 }}>مركز عمليات يونس</Title>
            <Text type="secondary">بيانات تشغيلية حقيقية من الخادم المحلي — لا توجد أرقام تجريبية في هذه الشاشة.</Text>
          </div>
          <Space>
            {updatedAt && <Text type="secondary">آخر تحديث: {updatedAt.toLocaleTimeString('ar-SA')}</Text>}
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>تحديث آمن</Button>
          </Space>
        </div>

        {error && <Alert type="error" showIcon message="تعذر تحميل البيانات الحية" description={error} action={<Button size="small" onClick={() => void load()}>إعادة المحاولة</Button>} />}

        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}><Card><Statistic title="المستخدمون المتصلون" value={activeUsers} prefix={<TeamOutlined />} /><Tag color="cyan">REALTIME</Tag></Card></Col>
          <Col xs={24} sm={12} lg={6}><Card><Statistic title="الرسائل خلال 24 ساعة" value={messages24h} prefix={<SendOutlined />} /><Text type="secondary">الإجمالي المخزن: {totalMessages}</Text></Card></Col>
          <Col xs={24} sm={12} lg={6}><Card><Statistic title="طلبات الموافقة" value={pendingApprovals} prefix={<SafetyCertificateOutlined />} /><Tag color={pendingApprovals ? 'warning' : 'success'}>{pendingApprovals ? 'تحتاج مراجعة' : 'لا توجد طلبات'}</Tag></Card></Col>
          <Col xs={24} sm={12} lg={6}><Card><Statistic title="مكالمات RED النشطة" value={activeCalls} prefix={<PhoneOutlined />} /><Tag color="blue">REALTIME</Tag></Card></Col>
        </Row>

        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title={<Space><CloudServerOutlined /> صحة الخادم</Space>}>
              {loading && !updatedAt ? <Spin /> : (
                <Descriptions column={{ xs: 1, sm: 2 }} size="small">
                  <Descriptions.Item label="PostgreSQL">{statusTag(dbHealth)}</Descriptions.Item>
                  <Descriptions.Item label="تسليم الرسائل"><Progress percent={deliveryRate} size="small" /></Descriptions.Item>
                  <Descriptions.Item label="ذاكرة JVM"><Progress percent={memoryPercent} size="small" status={memoryPercent > 85 ? 'exception' : 'normal'} /></Descriptions.Item>
                  <Descriptions.Item label="محادثات نشطة">{number(snapshot.realtime.active_conversations)}</Descriptions.Item>
                  <Descriptions.Item label="رسائل غير مسلمة">{number(snapshot.realtime.pending_messages_24h)}</Descriptions.Item>
                  <Descriptions.Item label="نواتج CPU">{number(snapshot.monitor.cpu_cores) || '—'}</Descriptions.Item>
                </Descriptions>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title={<Space><CheckCircleOutlined /> مبادئ التشغيل</Space>}>
              <Space direction="vertical">
                <Text>✓ هوية RED مستقلة بلا تسجيل Phone OTP.</Text>
                <Text>✓ المسارات الإدارية تتطلب حساب ADMIN.</Text>
                <Text>✓ PSTN منفصل ومراقب عن رسائل RED الخاصة.</Text>
                <Text>✓ DINSTAR لا يظهر معلومات SIM الحساسة افتراضياً.</Text>
              </Space>
            </Card>
          </Col>
        </Row>

        <Card title={<Space><ApiOutlined /> حالة بوابة DINSTAR</Space>} extra={<Text type="secondary">بيانات الحالة فقط؛ لا يعرض IMSI أو ICCID أو أرقاماً كاملة</Text>}>
          {snapshot.ports.length ? <Table rowKey={(port) => String(port.index)} columns={portColumns} dataSource={snapshot.ports} pagination={false} size="small" /> : <Empty description="لا توجد بوابة متصلة أو لا توجد بيانات منافذ" />}
        </Card>

        <Card title="جرد الشرائح المصرح" extra={<SafetyCertificateOutlined />}>
          {snapshot.inventory.length ? <Table rowKey={(port) => `${port.gatewayId}-${port.portIndex}`} columns={inventoryColumns} dataSource={snapshot.inventory} pagination={false} size="small" /> : <Empty description="لا يوجد جرد بعد؛ تظهر المنافذ هنا بعد أول قراءة حالة من DINSTAR ثم وسمها من المسؤول." />}
        </Card>

        <Card title="أحداث التدقيق الأخيرة" extra={<DatabaseOutlined />}>
          {snapshot.audit.length ? <Table rowKey={(event) => event.id || `${event.action}-${event.createdAt}`} columns={auditColumns} dataSource={snapshot.audit.slice(0, 10)} pagination={false} size="small" /> : <Empty description="لا توجد أحداث تدقيق بعد" />}
        </Card>
      </Space>
    </div>
  );
}
