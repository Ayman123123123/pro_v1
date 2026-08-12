import { useCallback, useState } from 'react';
import { Alert, Card, Empty, Space, Statistic, Table, Tag } from 'antd';
import { VideoCameraOutlined } from '@ant-design/icons';
import { apiFetch } from '../../api';
import { usePolling } from '../../hooks/usePolling';

export default function MediaTab() {
  const [calls, setCalls] = useState<any[]>([]);
  const [online, setOnline] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const response = await apiFetch('/api/master/v1/media/active-calls');
      setOnline(response.ok);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const body = await response.json();
      setCalls(Array.isArray(body) ? body : []);
      setError('');
    } catch (e: any) {
      setCalls([]);
      setError(e?.message || 'تعذر قراءة المكالمات النشطة');
    }
  }, []);

  usePolling(load, 8000);

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="WebRTC / mediasoup SFU"
        extra={<Tag color={online ? 'green' : 'red'}>{online ? 'ONLINE' : 'UNAVAILABLE'}</Tag>}
      >
        <Statistic title="مكالمات نشطة الآن" value={calls.length} prefix={<VideoCameraOutlined />} />
        {error && <Alert type="warning" showIcon message={error} style={{ marginTop: 12 }} />}
      </Card>
      <Card title="الغرف الحية">
        {calls.length === 0 ? (
          <Empty description="لا توجد مكالمات نشطة عبر SFU" />
        ) : (
          <Table
            size="small"
            rowKey={(r) => r.id || `${r.room}-${r.startedAt}`}
            pagination={false}
            dataSource={calls}
            columns={[
              { title: 'النوع', dataIndex: 'type', render: (v: string) => <Tag color={v === 'VIDEO' ? 'purple' : 'blue'}>{v}</Tag> },
              { title: 'الغرفة', dataIndex: 'room' },
              { title: 'المشاركون', dataIndex: 'participants' },
              { title: 'Bitrate', dataIndex: 'bitrateKbps', render: (v: number) => (v ? `${v} kbps` : '—') },
              { title: 'بدأت', dataIndex: 'startedAt', render: (v: string) => (v ? new Date(v).toLocaleString('ar') : '—') },
            ]}
          />
        )}
      </Card>
    </Space>
  );
}
