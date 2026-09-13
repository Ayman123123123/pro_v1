import { useCallback, useMemo, useState } from 'react';
import { Alert, Card, Col, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import {
  CloudServerOutlined,
  DatabaseFilled,
  SafetyCertificateFilled,
  ThunderboltFilled,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';
import { usePolling } from '../hooks/usePolling';

/** Live overview for the RED application services and media layer. */
export default function MasterOverview() {
  const [stats, setStats] = useState<any>({});
  const [calls, setCalls] = useState<any[]>([]);
  const [health, setHealth] = useState<any>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [s, c, h] = await Promise.all([
        apiFetch('/api/master/v1/stats/realtime'),
        apiFetch('/api/master/v1/media/active-calls'),
        apiFetch('/health'),
      ]);
      if (s.ok) setStats(await s.json());
      if (c.ok) {
        const body = await c.json();
        setCalls(Array.isArray(body) ? body : Array.isArray(body?.calls) ? body.calls : []);
      }
      if (h.ok) setHealth(await h.json());
      setError('');
    } catch (e: any) {
      setError(e?.message || 'تعذر تحديث المراقبة الحية');
    }
  }, []);

  usePolling(load, 5000);

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
          نبض المنصة كل 5 ثوانٍ: المستخدمون، الرسائل، المكالمات، وصحة الخدمات.
        </Typography.Text>
      </div>

      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="المستخدمون المعتمدون" value={stats.active_users || 0} prefix={<ThunderboltFilled />} />
            <Tag color="gold">LIVE</Tag>
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
            <Statistic title="قاعدة البيانات" value={stats.db_health || health?.status || 'UNKNOWN'} prefix={<DatabaseFilled />} />
            <Tag color={(stats.db_health || health?.status) === 'UP' ? 'gold' : 'red'}>REAL CHECK</Tag>
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="المكالمات النشطة" value={calls.length} prefix={<VideoCameraOutlined />} />
            <Tag color="cyan">MEDIA</Tag>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card><Statistic title="رسائل 24 ساعة" value={stats.messages_24h ?? '—'} prefix={<CloudServerOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card><Statistic title="معدل التوصيل" value={stats.delivery_rate_percent ?? '—'} suffix="%" prefix={<CloudServerOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card><Statistic title="مكالمات SFU" value={calls.length} prefix={<VideoCameraOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card><Statistic title="حالة الخادم" value={health?.status || 'UNKNOWN'} prefix={<DatabaseFilled />} /></Card>
        </Col>
      </Row>

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
              <Tag key={name} color={svc?.status === 'UP' ? 'gold' : svc?.status === 'DEGRADED' ? 'orange' : 'red'}>
                {name}: {svc?.status || 'UNKNOWN'}
              </Tag>
            ))}
          </Space>
        </Card>
      )}
    </Space>
  );
}
