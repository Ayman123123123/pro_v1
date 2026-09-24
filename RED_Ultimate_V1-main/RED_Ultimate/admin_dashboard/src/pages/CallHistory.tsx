import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, Col, Radio, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd';
import { PhoneOutlined, VideoCameraOutlined, HistoryOutlined, StopOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';
import { RequireAuth, formatSafeDate, formatSafeTime } from './_shared';

type HistoryRow = {
  id: string;
  caller_id?: string | null;
  callee_id?: string | null;
  call_type?: string;
  direction?: string;
  status?: string;
  started_at?: string;
  answered_at?: string | null;
  ended_at?: string | null;
  duration_ms?: number | null;
};

const STATUS_COLOR: Record<string, string> = {
  RINGING: 'gold',
  ACTIVE: 'cyan',
  ENDED: 'gold',
  MISSED: 'red',
  REJECTED: 'orange',
  BUSY: 'volcano',
  FAILED: 'default',
};

const STATUS_LABEL: Record<string, string> = {
  RINGING: 'يرن',
  ACTIVE: 'نشطة',
  ENDED: 'انتهت',
  MISSED: 'فائتة',
  REJECTED: 'مرفوضة',
  BUSY: 'مشغول',
  FAILED: 'فشلت',
};

const TYPE_LABEL: Record<string, string> = {
  AUDIO_1V1: 'صوت 1:1',
  VIDEO_1V1: 'فيديو 1:1',
  GROUP_AUDIO: 'جماعية صوت',
  GROUP_VIDEO: 'جماعية فيديو',
  LIVE_STREAM: 'بث مباشر',
  SPACE: 'مساحة',
};

export default function CallHistory() {
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (filter?: string) => {
    setLoading(true);
    try {
      const query = filter ? `?status=${encodeURIComponent(filter)}` : '';
      const res = await apiFetch('/api/master/v1/calls/history' + query);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const body = await res.json();
      setRows(Array.isArray(body?.calls) ? body.calls : []);
      setTotal(body?.total ?? 0);
      setError('');
    } catch (e: any) {
      setRows([]);
      setTotal(0);
      setError(e?.message || 'تعذر تحميل سجل المكالمات');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(status); }, [status, load]);

  const answered = rows.filter((r) => r.status === 'ENDED' || r.status === 'ACTIVE').length;
  const missed = rows.filter((r) => r.status === 'MISSED').length;

  const columns = [
    {
      title: 'الطرف المتصل',
      dataIndex: 'caller_id',
      render: (_: unknown, r: HistoryRow) => (
        <Typography.Text strong style={{ color: '#35CBE0' }}>{r.caller_id || '—'}</Typography.Text>
      ),
    },
    {
      title: 'المُستدعى',
      render: (_: unknown, r: HistoryRow) => r.callee_id || '—',
    },
    {
      title: 'النوع',
      dataIndex: 'call_type',
      render: (v: string) => (
        <Tag color={(v || '').startsWith('VIDEO') ? 'purple' : 'blue'}>
          {TYPE_LABEL[v] || v || '—'}
        </Tag>
      ),
    },
    {
      title: 'الاتجاه',
      dataIndex: 'direction',
      // ✅ 2026-09-24: قيمة ناقصة/غريبة كانت تُعرض "وارد" زوراً
      render: (v: string) => (v === 'OUTGOING' ? 'صادر' : v === 'INCOMING' ? 'وارد' : (v || '—')),
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      render: (v: string) => (
        <Tag color={STATUS_COLOR[v] || 'default'}>{STATUS_LABEL[v] || v || '—'}</Tag>
      ),
    },
    {
      title: 'المدة',
      dataIndex: 'duration_ms',
      render: (v: number | null) => {
        if (!v) return '—';
        const s = Math.round(v / 1000);
        return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
      },
    },
    {
      title: 'البداية',
      dataIndex: 'started_at',
      render: (v: string) => (v ? `${formatSafeDate(v, 'ar')} ${formatSafeTime(v, 'ar')}` : '—'),
    },
  ];

  return (
    <RequireAuth>
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          سجل المكالمات الموحّد
        </Typography.Title>
        <Typography.Text type="secondary">
          كل مكالمات RED من قاعدة Postgres الحية — تُحدَّث لحظياً من أحداث الخادم.
        </Typography.Text>
      </div>

      {error && <Alert type="warning" showIcon message={error} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="إجمالي السجل" value={total} prefix={<HistoryOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="نجحت (تم الرد)" value={answered} prefix={<PhoneOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="فائتة / مرفوضة" value={missed} prefix={<StopOutlined />} />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <VideoCameraOutlined />
            <span>أحدث 100 مكالمة</span>
          </Space>
        }
        extra={
          <Radio.Group
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            optionType="button"
            buttonStyle="solid"
            size="small"
            style={{ display: 'flex', flexWrap: 'wrap', gap: 4, maxWidth: '100%' }}
          >
            <Radio.Button value={undefined}>الكل</Radio.Button>
            <Radio.Button value="MISSED">فائتة</Radio.Button>
            <Radio.Button value="REJECTED">مرفوضة</Radio.Button>
            <Radio.Button value="BUSY">مشغول</Radio.Button>
            <Radio.Button value="ENDED">انتهت</Radio.Button>
          </Radio.Group>
        }
      >
        <Table
          size="small"
          rowKey={(r: HistoryRow) => r?.id ?? `${r?.caller_id ?? 'x'}-${r?.started_at ?? ''}`}
          loading={loading}
          dataSource={rows}
          columns={columns}
          pagination={false}
          scroll={{ x: 900 }}
          locale={{ emptyText: 'لا توجد مكالمات بهذا الفلتر' }}
        />
      </Card>
    </Space>
    </RequireAuth>
  );
}