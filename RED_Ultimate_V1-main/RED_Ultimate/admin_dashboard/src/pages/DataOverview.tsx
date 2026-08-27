import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Empty, Row, Spin, Statistic, Table, Tag, Typography } from 'antd';
import { DatabaseOutlined, ReloadOutlined } from '@ant-design/icons';
import { getOperationsOverview } from '../api';
import { usePolling } from '../hooks/usePolling';

/**
 * قيمة المقياس قد تكون `null` عندما يكون مصدر البيانات غير متاح.
 * `null` تعني «غير معروف»، لا صفر — التمييز مقصود ولا يجوز طيّه.
 */
type Section = Record<string, number | null>;
type SourceState = { available: boolean; error: string | null; observedAt: string };
type Overview = {
  generatedAt: string;
  users: Section;
  devices: Section;
  moderation: Section;
  content: Section;
  communications: Section;
  storage: Section;
  dataSources?: Record<string, SourceState>;
};

const labels: Record<string, string> = {
  total: 'الإجمالي', approved: 'معتمد', pending: 'معلّق', banned: 'محظور', administrators: 'مديرون', online: 'متصلون',
  revoked: 'ملغى', activeRefreshSessions: 'جلسات تجديد نشطة', openReports: 'بلاغات مفتوحة', securityAlerts24h: 'تنبيهات أمنية 24س',
  auditEvents24h: 'أحداث تدقيق 24س', groups: 'مجموعات', messages: 'رسائل مشفرة (عدد فقط)', stories: 'قصص', posts: 'منشورات',
  channels: 'قنوات', polls: 'استطلاعات', events: 'فعاليات', stickerPacks: 'حزم ملصقات', callHistory: 'سجل مكالمات',
  activeCalls: 'مكالمات نشطة', dinstarCdr: 'سجلات PSTN', gateways: 'بوابات', gatewayPorts: 'لقطات منافذ',
  mediaGrants: 'تصاريح وسائط', backups: 'سجلات نسخ', notifications: 'إشعارات'
};

const sourceLabels: Record<string, string> = {
  postgresql: 'PostgreSQL', mongodb: 'MongoDB', redis: 'Redis'
};

const sectionTitles: Array<[Exclude<keyof Overview, 'generatedAt' | 'dataSources'>, string]> = [
  ['users', 'المستخدمون والحضور'], ['devices', 'الأجهزة والجلسات'], ['moderation', 'الأمان والإشراف'],
  ['content', 'المحتوى'], ['communications', 'الاتصالات والبوابات'], ['storage', 'التخزين والعمليات']
];

/** يعرض «—» عند غياب القيمة حتى لا يُقرأ انقطاع المصدر كصفر حقيقي. */
function MetricValue({ value }: { value: number | null }) {
  if (value === null || value === undefined) {
    return <Tag color="warning">غير متاح</Tag>;
  }
  return <Tag color={value > 0 ? 'cyan' : 'default'}>{value.toLocaleString('ar')}</Tag>;
}

export default function DataOverview() {
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      setData(await getOperationsOverview());
    } catch (e: any) {
      setError(e?.message || 'تعذر تحميل جرد بيانات المنصة');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);
  usePolling(load, 30_000);

  const rows = data ? sectionTitles.flatMap(([key, title]) =>
    Object.entries(data[key] || {}).map(([metric, value]) => ({ key: `${key}.${metric}`, section: title, metric, value }))
  ) : [];

  const downSources = Object.entries(data?.dataSources || {}).filter(([, s]) => !s.available);

  return <div>
    <Typography.Title level={2} style={{ color: '#14D89B', marginTop: 0 }}><DatabaseOutlined /> جرد بيانات المنصة</Typography.Title>
    <Typography.Paragraph type="secondary">
      عرض تشغيلي شامل للبيانات المتاحة للإدارة. الأرقام مجمعة فقط ولا تعرض نصوص الرسائل المشفرة أو الأسرار أو مفاتيح الهوية.
    </Typography.Paragraph>
    <Alert type="info" showIcon style={{ marginBottom: 16 }} message="الخصوصية محفوظة" description="هذا القسم يعرض أعداداً وحالة تشغيلية فقط؛ لا يُستخدم لتصفح محتوى المستخدمين الخاص." />
    {downSources.length > 0 && <Alert type="warning" showIcon style={{ marginBottom: 16 }}
      message="مصدر بيانات غير متاح"
      description={`المقاييس التابعة لـ ${downSources.map(([k]) => sourceLabels[k] || k).join('، ')} تظهر «غير متاح» ولا تُقرأ كصفر. تحقق من حالة الخدمة قبل أي قرار تشغيلي.`} />}
    {error && <Alert type="error" showIcon closable message="فشل التحميل" description={error} style={{ marginBottom: 16 }}
      action={<Button size="small" onClick={() => void load()}>إعادة المحاولة</Button>} />}
    <Card extra={<Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>تحديث</Button>}
      title={data?.generatedAt ? `آخر تحديث: ${new Date(data.generatedAt).toLocaleString('ar')}` : (error ? 'تعذر تحميل الجرد' : 'جاري تحميل الجرد')}>
      <Spin spinning={loading && !data}>
        {!data ? <Empty description="لا توجد بيانات بعد" /> : <>
          {data.dataSources && <Card size="small" title="حالة مصادر البيانات" style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={{ xs: 1, md: 3 }}>
              {Object.entries(data.dataSources).map(([name, state]) => <Descriptions.Item key={name} label={sourceLabels[name] || name}>
                <Tag color={state.available ? 'success' : 'error'}>{state.available ? 'متاح' : 'غير متاح'}</Tag>
              </Descriptions.Item>)}
            </Descriptions>
          </Card>}
          <Row gutter={[16, 16]}>
            {sectionTitles.map(([key, title]) => <Col xs={24} md={12} xl={8} key={key}>
              <Card size="small" title={title} bordered>
                <Descriptions size="small" column={1}>
                  {Object.entries(data[key] || {}).map(([metric, value]) => <Descriptions.Item key={metric} label={labels[metric] || metric}>
                    <MetricValue value={value} />
                  </Descriptions.Item>)}
                </Descriptions>
              </Card>
            </Col>)}
          </Row>
          <Card size="small" title="كل المقاييس" style={{ marginTop: 16 }}>
            <Table size="small" rowKey="key" dataSource={rows} pagination={{ pageSize: 20 }} columns={[
              { title: 'القسم', dataIndex: 'section', filters: sectionTitles.map(([, title]) => ({ text: title, value: title })), onFilter: (v, r: any) => r.section === v },
              { title: 'المقياس', dataIndex: 'metric', render: (v: string) => labels[v] || v },
              {
                title: 'القيمة', dataIndex: 'value', render: (v: number | null) => v === null || v === undefined
                  ? <Tag color="warning">غير متاح</Tag>
                  : <Statistic value={v} valueStyle={{ fontSize: 16 }} />
              }
            ]} />
          </Card>
        </>}
      </Spin>
    </Card>
  </div>;
}
