import { useCallback, useMemo, useState } from 'react';
import { Alert, Card, Col, Progress, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import {
  ApiOutlined,
  CloudServerOutlined,
  DatabaseFilled,
  PhoneOutlined,
  SafetyCertificateFilled,
  ThunderboltFilled,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';
import { usePolling } from '../hooks/usePolling';

type Slot = {
  index?: number;
  status?: string;
  signal?: number | null;
  signalLabel?: string;
  signalUsable?: boolean;
  operator?: string;
  callState?: string;
};

const SIGNAL_COLOR: Record<string, string> = {
  EXCELLENT: 'green', GOOD: 'cyan', FAIR: 'gold', WEAK: 'orange', UNUSABLE: 'red', NO_SIGNAL: 'default',
};

export default function MasterOverview() {
  const [stats, setStats] = useState<any>({});
  const [slots, setSlots] = useState<Slot[]>([]);
  const [calls, setCalls] = useState<any[]>([]);
  const [health, setHealth] = useState<any>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [s, d, c, h] = await Promise.all([
        apiFetch('/api/master/v1/stats/realtime'),
        apiFetch('/api/master/v1/hardware/dinstar/slots'),
        apiFetch('/api/master/v1/media/active-calls'),
        apiFetch('/health'),
      ]);
      if (s.ok) setStats(await s.json());
      if (d.ok) {
        const body = await d.json();
        setSlots(Array.isArray(body) ? body : []);
      }
      if (c.ok) {
        const body = await c.json();
        setCalls(Array.isArray(body) ? body : []);
      }
      if (h.ok) setHealth(await h.json());
      setError('');
    } catch (e: any) {
      setError(e?.message || 'تعذر تحديث المراقبة الحية');
    }
  }, []);

  usePolling(load, 5000);

  const usable = slots.filter((x) => x.signalUsable).length;
  const registered = slots.filter((x) => x.status === 'REGISTERED').length;
  const signals = slots.map((x) => Number(x.signal || 0)).filter(Number.isFinite);
  const signal = signals.length ? Math.round(signals.reduce((a, b) => a + b, 0) / signals.length) : 0;

  const services = useMemo(() => {
    const map = health?.services && typeof health.services === 'object' ? health.services : {};
    return Object.entries(map) as Array<[string, any]>;
  }, [health]);

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          المراقبة الحية — مركز السيادة
        </Typography.Title>
        <Typography.Text type="secondary">
          نبض المنصة كل 5 ثوانٍ: المستخدمون، المكالمات، DINSTAR، وصحة الخدمات.
        </Typography.Text>
      </div>

      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="المستخدمون المعتمدون" value={stats.active_users || 0} prefix={<ThunderboltFilled />} />
            <Tag color="green">LIVE</Tag>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="طلبات الموافقة" value={stats.pending_approvals || 0} prefix={<SafetyCertificateFilled />} />
            <Tag color="orange">AUTHORITY</Tag>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="متوسط إشارة DINSTAR" value={signal} suffix="%" prefix={<ApiOutlined />} />
            <Progress percent={signal} showInfo={false} strokeColor={signal >= 60 ? '#00C896' : '#E8B84A'} />
            <Tag color={slots.length ? 'green' : 'red'}>{usable} جاهزة / {registered} مسجّلة</Tag>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="قاعدة البيانات" value={stats.db_health || health?.status || 'UNKNOWN'} prefix={<DatabaseFilled />} />
            <Tag color={(stats.db_health || health?.status) === 'UP' ? 'green' : 'red'}>REAL CHECK</Tag>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="رسائل 24 ساعة" value={stats.messages_24h ?? '—'} prefix={<CloudServerOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="معدل التوصيل" value={stats.delivery_rate_percent ?? '—'} suffix="%" prefix={<PhoneOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="مكالمات SFU نشطة" value={calls.length} prefix={<VideoCameraOutlined />} />
          </Card>
        </Col>
      </Row>

      <Card title="منافذ DINSTAR الحية">
        <Table
          size="small"
          rowKey={(r) => String(r.index)}
          pagination={false}
          dataSource={slots}
          locale={{ emptyText: 'لا توجد قراءات منافذ' }}
          columns={[
            { title: 'SIM', dataIndex: 'index', render: (v: number) => `SIM ${(v ?? 0) + 1}` },
            { title: 'الحالة', dataIndex: 'status', render: (v: string) => <Tag color={v === 'REGISTERED' ? 'green' : 'red'}>{v || '—'}</Tag> },
            { title: 'الإشارة', dataIndex: 'signalLabel', render: (v: string, r: Slot) => <Tag color={SIGNAL_COLOR[v] || 'default'}>{v || '—'}{r.signal != null ? ` · ${r.signal}%` : ''}</Tag> },
            { title: 'المشغل', dataIndex: 'operator', render: (v?: string) => v || '—' },
            { title: 'المكالمة', dataIndex: 'callState', render: (v?: string) => v || '—' },
          ]}
        />
      </Card>

      <Card title="المكالمات النشطة عبر SFU">
        <Table
          size="small"
          rowKey={(r) => r.id || `${r.room}-${r.startedAt}`}
          pagination={false}
          dataSource={calls}
          locale={{ emptyText: 'لا توجد مكالمات نشطة' }}
          columns={[
            { title: 'النوع', dataIndex: 'type' },
            { title: 'الغرفة', dataIndex: 'room' },
            { title: 'المشاركون', dataIndex: 'participants' },
            { title: 'Bitrate', dataIndex: 'bitrateKbps', render: (v: number) => (v ? `${v} kbps` : '—') },
            { title: 'بدأت', dataIndex: 'startedAt', render: (v: string) => (v ? new Date(v).toLocaleTimeString('ar') : '—') },
          ]}
        />
      </Card>

      {services.length > 0 && (
        <Card title="صحة الخدمات">
          <Space wrap>
            {services.map(([name, svc]) => (
              <Tag key={name} color={svc?.status === 'UP' ? 'green' : svc?.status === 'DEGRADED' ? 'orange' : 'red'}>
                {name}: {svc?.status || 'UNKNOWN'}
              </Tag>
            ))}
          </Space>
        </Card>
      )}
    </Space>
  );
}
