import { useEffect, useState } from 'react';
import {
  Tabs, Card, Table, Tag, Space, Button, Modal, Form, Input, Select, DatePicker,
  InputNumber, message, Statistic, Row, Col, Typography, Empty, Popconfirm,
  Switch, Tooltip
} from 'antd';
import {
  BarChartOutlined, CalendarOutlined, TagsOutlined, PictureOutlined,
  PlusOutlined, ReloadOutlined, CheckOutlined, CloseOutlined, DeleteOutlined,
  RiseOutlined, FireOutlined, StopOutlined, CheckCircleOutlined
} from '@ant-design/icons';
import {
  getPolls, createPoll, closePoll, deletePoll,
  getEvents, createEvent, cancelEvent, deleteEvent,
  getTrendingHashtags, getPopularHashtags, blockHashtag, unblockHashtag,
  getStickerPacks, createStickerPack, publishStickerPack, deleteStickerPack,
  type Poll, type Event, type Hashtag, type StickerPack
} from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

const POLL_TYPE_LABELS: Record<string, { label: string; color: string }> = {
  SINGLE_CHOICE: { label: 'اختيار واحد', color: 'blue' },
  MULTIPLE_CHOICE: { label: 'اختيار متعدد', color: 'purple' },
  RANKED: { label: 'تصنيف', color: 'gold' },
};

const POLL_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: 'مسودة', color: 'default' },
  ACTIVE: { label: 'نشطة', color: 'gold' },
  CLOSED: { label: 'مغلقة', color: 'orange' },
  ARCHIVED: { label: 'مؤرشفة', color: 'default' },
};

const EVENT_TYPE_LABELS: Record<string, { label: string; color: string }> = {
  MEETING: { label: 'اجتماع', color: 'blue' },
  CONFERENCE: { label: 'مؤتمر', color: 'purple' },
  WEBINAR: { label: 'ندوة', color: 'cyan' },
  SOCIAL: { label: 'اجتماعي', color: 'pink' },
  CELEBRATION: { label: 'احتفال', color: 'gold' },
  OTHER: { label: 'أخرى', color: 'default' },
};

const EVENT_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: 'مسودة', color: 'default' },
  SCHEDULED: { label: 'مجدول', color: 'blue' },
  LIVE: { label: 'مباشر', color: 'gold' },
  ENDED: { label: 'منتهي', color: 'default' },
  CANCELLED: { label: 'ملغي', color: 'red' },
};

const EVENT_VISIBILITY_LABELS: Record<string, string> = {
  PUBLIC: 'عام',
  PRIVATE: 'خاص',
  INVITATION_ONLY: 'بدعوة فقط',
};

export default function ContentManagement() {
  const [activeTab, setActiveTab] = useState('polls');

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#D4B16A', margin: 0 }}>
          <BarChartOutlined /> إدارة المحتوى
        </Title>
        <Text type="secondary">استطلاعات، أحداث، هاشتاجات، وملصقات</Text>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        size="large"
        items={[
          {
            key: 'polls',
            label: <Space><BarChartOutlined /> الاستطلاعات</Space>,
            children: <PollsTab />,
          },
          {
            key: 'events',
            label: <Space><CalendarOutlined /> الأحداث</Space>,
            children: <EventsTab />,
          },
          {
            key: 'hashtags',
            label: <Space><TagsOutlined /> الهاشتاجات</Space>,
            children: <HashtagsTab />,
          },
          {
            key: 'stickers',
            label: <Space><PictureOutlined /> الملصقات</Space>,
            children: <StickersTab />,
          },
        ]}
      />
    </Space>
  );
}

// ━━━━━━━━━━━━━━━━ Polls Tab ━━━━━━━━━━━━━━━━
function PollsTab() {
  const [polls, setPolls] = useState<Poll[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const result = await getPolls({ page: 0, size: 50 });
      setPolls(Array.isArray(result?.content) ? result.content : []);
    } catch (e: any) {
      message.error('تعذر التحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ pollType: 'SINGLE_CHOICE', isAnonymous: false, allowAddOptions: false });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      const options = (Array.isArray(values.options) ? values.options : [])
        .map((item: unknown) => String(item ?? '').trim())
        .filter(Boolean);
      if (options.length < 2) {
        message.error('أضف خيارين على الأقل');
        return;
      }
      await createPoll({
        question: values.question,
        options,
        pollType: values.pollType,
        isAnonymous: values.isAnonymous,
        allowAddOptions: values.allowAddOptions,
        endsAt: values.endDate?.toISOString(),
      });
      message.success('تم إنشاء الاستطلاع');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleClose = async (id: string) => {
    try {
      await closePoll(id);
      message.success('تم إغلاق الاستطلاع');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deletePoll(id);
      message.success('تم حذف الاستطلاع');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'السؤال',
      dataIndex: 'question',
      key: 'question',
      render: (q: string, r: Poll) => (
        <Space direction="vertical" size={0}>
          <Text strong>{q}</Text>
          {r.description && <Text type="secondary" style={{ fontSize: 11 }}>{r.description}</Text>}
        </Space>
      ),
    },
    {
      title: 'النوع',
      dataIndex: 'pollType',
      key: 'pollType',
      render: (t: string) => {
        const type = POLL_TYPE_LABELS[t] ?? { label: t, color: 'default' };
        return <Tag color={type.color}>{type.label}</Tag>;
      },
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => {
        const status = POLL_STATUS_LABELS[s] ?? { label: s, color: 'default' };
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
    {
      title: 'الأصوات',
      key: 'votes',
      render: (r: Poll) => (
        <Space direction="vertical" size={0}>
          <Text strong>{r.totalVotes}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.uniqueVoters} مصوّت</Text>
        </Space>
      ),
    },
    {
      title: 'ينتهي',
      dataIndex: 'endsAt',
      key: 'endsAt',
      render: (e: string) => e ? <Text style={{ fontSize: 12 }}>{new Date(e).toLocaleDateString('ar-EG')}</Text> : <Tag>مفتوح</Tag>,
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: Poll) => (
        <Space size="small">
          {r.status === 'ACTIVE' && (
            <Tooltip title="إغلاق">
              <Button size="small" icon={<StopOutlined />} onClick={() => handleClose(r.id)} />
            </Tooltip>
          )}
          <Popconfirm title="حذف؟" onConfirm={() => handleDelete(r.id)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="إجمالي"
              value={polls.length}
              prefix={<BarChartOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="نشطة"
              value={polls.filter(p => p.status === 'ACTIVE').length}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#B78A2E' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="إجمالي الأصوات"
              value={polls.reduce((sum, p) => sum + p.totalVotes, 0)}
              prefix={<RiseOutlined />}
              valueStyle={{ color: '#E0A83C' }}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>إنشاء استطلاع</Button>
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      <Card>
        {polls.length === 0 ? <Empty description="لا توجد استطلاعات" /> : (
          <Table rowKey="id" columns={columns} dataSource={polls} loading={loading} pagination={{ pageSize: 10 }} />
        )}
      </Card>

      <Modal
        title="إنشاء استطلاع جديد"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={submitCreate}
        okText="إنشاء"
        cancelText="إلغاء"
        width={600}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="question" label="السؤال" rules={[{ required: true }]}>
            <Input placeholder="مثال: ما رأيك في..." />
          </Form.Item>
          <Form.Item name="description" label="الوصف (اختياري)">
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item label="الخيارات" required>
            <Form.List name="options" rules={[{ validator: async (_, value) => value && value.length >= 2 ? Promise.resolve() : Promise.reject(new Error('minimum 2 options')) }]}>
              {(fields, { add, remove }, { errors }) => (
                <>
                  {fields.map((field, idx) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                      <Form.Item name={field.name} rules={[{ required: true, message: 'مطلوب' }]} style={{ margin: 0, width: 350 }}>
                        <Input placeholder={`خيار ${idx + 1}`} />
                      </Form.Item>
                      {fields.length > 2 && (
                        <Button danger onClick={() => remove(field.name)} icon={<DeleteOutlined />} />
                      )}
                    </Space>
                  ))}
                  <Form.Item>
                    <Button onClick={() => add('')} icon={<PlusOutlined />}>إضافة خيار</Button>
                    <Form.ErrorList errors={errors} />
                  </Form.Item>
                </>
              )}
            </Form.List>
          </Form.Item>
          <Space>
            <Form.Item name="pollType" label="النوع">
              <Select style={{ width: 180 }} options={Object.entries(POLL_TYPE_LABELS).map(([v, t]) => ({ value: v, label: t.label }))} />
            </Form.Item>
            <Form.Item name="endDate" label="تاريخ الانتهاء">
              <DatePicker showTime style={{ width: 200 }} placeholder="اختياري" />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="isAnonymous" label="مجهول" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="allowAddOptions" label="السماح بإضافة خيارات" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  );
}

// ━━━━━━━━━━━━━━━━ Events Tab ━━━━━━━━━━━━━━━━
function EventsTab() {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const result = await getEvents({ page: 0, size: 50 });
      setEvents(Array.isArray(result?.content) ? result.content : []);
    } catch (e: any) {
      message.error('تعذر التحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ eventType: 'MEETING', visibility: 'PUBLIC', rsvpEnabled: true });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      await createEvent({
        title: values.title,
        description: values.description,
        locationName: values.locationName,
        startsAt: values.dateRange[0].toISOString(),
        endsAt: values.dateRange[1]?.toISOString(),
        eventType: values.eventType,
        visibility: values.visibility,
        maxAttendees: values.maxAttendees,
        rsvpEnabled: values.rsvpEnabled,
      });
      message.success('تم إنشاء الحدث');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleCancel = async (id: string) => {
    try {
      await cancelEvent(id, 'إلغاء إداري');
      message.success('تم إلغاء الحدث');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteEvent(id);
      message.success('تم حذف الحدث');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'الحدث',
      dataIndex: 'title',
      key: 'title',
      render: (t: string, r: Event) => (
        <Space direction="vertical" size={0}>
          <Text strong>{t}</Text>
          {r.locationName && <Text type="secondary" style={{ fontSize: 11 }}>📍 {r.locationName}</Text>}
        </Space>
      ),
    },
    {
      title: 'النوع',
      dataIndex: 'eventType',
      key: 'eventType',
      render: (t: string) => {
        const type = EVENT_TYPE_LABELS[t] ?? { label: t, color: 'default' };
        return <Tag color={type.color}>{type.label}</Tag>;
      },
    },
    {
      title: 'الظهور',
      dataIndex: 'visibility',
      key: 'visibility',
      render: (v: string) => <Tag>{EVENT_VISIBILITY_LABELS[v] ?? v}</Tag>,
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => {
        const status = EVENT_STATUS_LABELS[s] ?? { label: s, color: 'default' };
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
    {
      title: 'التاريخ',
      dataIndex: 'startsAt',
      key: 'startsAt',
      render: (d: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{new Date(d).toLocaleDateString('ar-EG')}</Text>
          <Text type="secondary" style={{ fontSize: 10 }}>{new Date(d).toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}</Text>
        </Space>
      ),
    },
    {
      title: 'الحضور',
      key: 'attendees',
      render: (r: Event) => (
        <Text>
          {r.currentAttendees}{r.maxAttendees ? `/${r.maxAttendees}` : ''}
        </Text>
      ),
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: Event) => (
        <Space size="small">
          {r.status !== 'CANCELLED' && r.status !== 'ENDED' && (
            <Button size="small" danger onClick={() => handleCancel(r.id)}>إلغاء</Button>
          )}
          <Popconfirm title="حذف؟" onConfirm={() => handleDelete(r.id)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic title="إجمالي" value={events.length} prefix={<CalendarOutlined />} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic title="مجدول" value={events.filter(e => e.status === 'SCHEDULED').length} prefix={<CalendarOutlined />} valueStyle={{ color: '#E0A83C' }} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic title="مباشر" value={events.filter(e => e.status === 'LIVE').length} prefix={<FireOutlined />} valueStyle={{ color: '#B78A2E' }} />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic title="إجمالي الحضور" value={events.reduce((s, e) => s + e.currentAttendees, 0)} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#4FC3F7' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>إنشاء حدث</Button>
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      <Card>
        {events.length === 0 ? <Empty description="لا توجد أحداث" /> : (
          <Table rowKey="id" columns={columns} dataSource={events} loading={loading} pagination={{ pageSize: 10 }} />
        )}
      </Card>

      <Modal
        title="إنشاء حدث جديد"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={submitCreate}
        okText="إنشاء"
        cancelText="إلغاء"
        width={650}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="title" label="العنوان" rules={[{ required: true }]}>
            <Input placeholder="عنوان الحدث" />
          </Form.Item>
          <Form.Item name="description" label="الوصف">
            <TextArea rows={3} />
          </Form.Item>
          <Space>
            <Form.Item name="dateRange" label="التاريخ والوقت" rules={[{ required: true }]}>
              <RangePicker showTime />
            </Form.Item>
            <Form.Item name="locationName" label="المكان">
              <Input placeholder="اختياري" style={{ width: 250 }} />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="eventType" label="النوع">
              <Select style={{ width: 180 }} options={Object.entries(EVENT_TYPE_LABELS).map(([v, t]) => ({ value: v, label: t.label }))} />
            </Form.Item>
            <Form.Item name="visibility" label="الظهور">
              <Select style={{ width: 150 }} options={Object.entries(EVENT_VISIBILITY_LABELS).map(([v, l]) => ({ value: v, label: l }))} />
            </Form.Item>
            <Form.Item name="maxAttendees" label="الحد الأقصى">
              <InputNumber min={1} placeholder="بلا حد" style={{ width: 130 }} />
            </Form.Item>
          </Space>
          <Form.Item name="rsvpEnabled" label="السماح بـ RSVP" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

// ━━━━━━━━━━━━━━━━ Hashtags Tab ━━━━━━━━━━━━━━━━
function HashtagsTab() {
  const [trending, setTrending] = useState<Hashtag[]>([]);
  const [popular, setPopular] = useState<Hashtag[]>([]);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<'trending' | 'popular'>('trending');

  const load = async () => {
    setLoading(true);
    try {
      const [t, p] = await Promise.all([getTrendingHashtags(100), getPopularHashtags(100)]);
      setTrending(Array.isArray(t) ? t : []);
      setPopular(Array.isArray(p) ? p : []);
    } catch (e: any) {
      message.error('تعذر التحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleBlock = async (id: string) => {
    try {
      await blockHashtag(id, 'إيقاف إداري');
      message.success('تم إيقاف الهاشتاج');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleUnblock = async (id: string) => {
    try {
      await unblockHashtag(id);
      message.success('تم تفعيل الهاشتاج');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const data = view === 'trending' ? trending : popular;

  const columns = [
    {
      title: 'الهاشتاج',
      dataIndex: 'tagName',
      key: 'tagName',
      render: (_n: string, r: Hashtag) => (
        <Space>
          <Text strong style={{ fontSize: 14 }}>#{(r as any).tagName || (r as any).tag || ''}</Text>
          {r.isTrending && <Tag color="orange" icon={<FireOutlined />}>ترند</Tag>}
          {r.isBlocked && <Tag color="red">محظور</Tag>}
        </Space>
      ),
    },
    {
      title: 'الاستخدام',
      dataIndex: 'usageCount',
      key: 'usageCount',
      render: (c: number) => <Tag color="blue">{c}</Tag>,
      sorter: (a: Hashtag, b: Hashtag) => a.usageCount - b.usageCount,
      defaultSortOrder: 'descend' as const,
    },
    {
      title: 'المنشورات',
      dataIndex: 'postsCount',
      key: 'postsCount',
    },
    {
      title: 'القصص',
      dataIndex: 'storiesCount',
      key: 'storiesCount',
    },
    {
      title: 'مستخدمون فريدون',
      dataIndex: 'uniqueUsers',
      key: 'uniqueUsers',
    },
    {
      title: 'Score',
      dataIndex: 'trendingScore',
      key: 'trendingScore',
      render: (s: number, r: Hashtag) => <Text code>{Number((r as any).trendingScore ?? (r as any).trendScore ?? s ?? 0).toFixed(2)}</Text>,
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: Hashtag) => (
        r.isBlocked ? (
          <Button size="small" type="primary" onClick={() => handleUnblock(r.id)}>تفعيل</Button>
        ) : (
          <Button size="small" danger onClick={() => handleBlock(r.id)}>حظر</Button>
        )
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Space>
          <Button type={view === 'trending' ? 'primary' : 'default'} icon={<FireOutlined />} onClick={() => setView('trending')}>
            ترند ({trending.length})
          </Button>
          <Button type={view === 'popular' ? 'primary' : 'default'} icon={<RiseOutlined />} onClick={() => setView('popular')}>
            الأكثر استخداماً ({popular.length})
          </Button>
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      <Card>
        {data.length === 0 ? <Empty description="لا توجد هاشتاجات" /> : (
          <Table rowKey="id" columns={columns} dataSource={data} loading={loading} pagination={{ pageSize: 20 }} />
        )}
      </Card>
    </Space>
  );
}

// ━━━━━━━━━━━━━━━━ Stickers Tab ━━━━━━━━━━━━━━━━
function StickersTab() {
  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const result = await getStickerPacks(false);
      setPacks(Array.isArray(result) ? result : []);
    } catch (e: any) {
      message.error('تعذر التحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ isOfficial: false, isFree: true, priceCents: 0 });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      await createStickerPack({
        name: values.name,
        description: values.description,
        coverMediaKey: values.coverMediaKey,
        isOfficial: values.isOfficial,
        isFree: values.isFree,
        priceCents: values.priceCents,
      });
      message.success('تم إنشاء حزمة الملصقات');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handlePublish = async (id: string) => {
    try {
      await publishStickerPack(id);
      message.success('تم النشر');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteStickerPack(id);
      message.success('تم الحذف');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'الاسم',
      dataIndex: 'name',
      key: 'name',
      render: (n: string, r: StickerPack) => (
        <Space>
          <Text strong>{n}</Text>
          {r.isOfficial && <Tag color="purple" icon={<CheckOutlined />}>رسمي</Tag>}
        </Space>
      ),
    },
    {
      title: 'الوصف',
      dataIndex: 'description',
      key: 'description',
      render: (d: string) => d ? <Tooltip title={d}><Typography.Text>{d}</Typography.Text></Tooltip> : '—',
    },
    {
      title: 'السعر',
      key: 'price',
      render: (r: StickerPack) => r.isFree ? (
        <Tag color="gold">مجاني</Tag>
      ) : (
        <Text strong>{(r.priceCents / 100).toFixed(2)} {r.currency}</Text>
      ),
    },
    {
      title: 'الملصقات',
      dataIndex: 'stickerCount',
      key: 'stickerCount',
    },
    {
      title: 'التنزيلات',
      dataIndex: 'totalDownloads',
      key: 'totalDownloads',
    },
    {
      title: 'الحالة',
      dataIndex: 'isPublished',
      key: 'isPublished',
      render: (p: boolean) => p ? <Tag color="gold">منشور</Tag> : <Tag>مسودة</Tag>,
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: StickerPack) => (
        <Space size="small">
          {!r.isPublished && (
            <Button size="small" type="primary" onClick={() => handlePublish(r.id)}>نشر</Button>
          )}
          <Popconfirm title="حذف؟" onConfirm={() => handleDelete(r.id)}>
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="إجمالي" value={packs.length} prefix={<PictureOutlined />} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="رسمية" value={packs.filter(p => p.isOfficial).length} prefix={<CheckOutlined />} valueStyle={{ color: '#722ED1' }} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic title="منشورة" value={packs.filter(p => p.isPublished).length} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#B78A2E' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>إنشاء حزمة ملصقات</Button>
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      <Card>
        {packs.length === 0 ? <Empty description="لا توجد حزم ملصقات" /> : (
          <Table rowKey="id" columns={columns} dataSource={packs} loading={loading} pagination={{ pageSize: 10 }} />
        )}
      </Card>

      <Modal
        title="إنشاء حزمة ملصقات"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={submitCreate}
        okText="إنشاء"
        cancelText="إلغاء"
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="name" label="الاسم" rules={[{ required: true }]}>
            <Input placeholder="اسم الحزمة" />
          </Form.Item>
          <Form.Item name="description" label="الوصف">
            <TextArea rows={2} />
          </Form.Item>
          <Form.Item name="coverMediaKey" label="مفتاح صورة الغلاف" rules={[{ required: true }]}>
            <Input placeholder="media-key" />
          </Form.Item>
          <Space>
            <Form.Item name="isOfficial" label="رسمية" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="isFree" label="مجانية" valuePropName="checked">
              <Switch defaultChecked />
            </Form.Item>
            <Form.Item name="priceCents" label="السعر (سنت)">
              <InputNumber min={0} style={{ width: 120 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  );
}
