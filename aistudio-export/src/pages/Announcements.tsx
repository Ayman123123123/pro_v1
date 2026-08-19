import { useEffect, useState } from 'react';
import {
  Table, Tag, Space, Button, Modal, Form, Input, Select, DatePicker, message, Card,
  Statistic, Row, Col, Typography, Empty, Switch, Alert
} from 'antd';
import {
  NotificationOutlined, PlusOutlined, ReloadOutlined, SendOutlined,
  DeleteOutlined, CheckCircleOutlined, ClockCircleOutlined, EditOutlined
} from '@ant-design/icons';
import {
  getAnnouncements, createAnnouncement, publishAnnouncement, deleteAnnouncement
} from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

const TYPE_LABELS: Record<string, { label: string; color: string; icon: string }> = {
  INFO: { label: 'معلومة', color: 'blue', icon: 'ℹ️' },
  WARNING: { label: 'تحذير', color: 'orange', icon: '⚠️' },
  MAINTENANCE: { label: 'صيانة', color: 'purple', icon: '🔧' },
  FEATURE: { label: 'ميزة جديدة', color: 'green', icon: '✨' },
  PROMO: { label: 'عرض ترويجي', color: 'gold', icon: '🎁' },
};

const AUDIENCE_LABELS: Record<string, string> = {
  ALL: 'الجميع',
  ADMINS: 'المسؤولون فقط',
  USERS: 'المستخدمون',
  SPECIFIC: 'مستخدمين محددين',
};

export default function Announcements() {
  const [announcements, setAnnouncements] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [stats, setStats] = useState({ total: 0, published: 0, active: 0 });

  const load = async () => {
    setLoading(true);
    try {
      const items = await getAnnouncements();
      setAnnouncements(items);
      const now = new Date();
      setStats({
        total: items.length,
        published: items.filter((a: any) => a.isPublished).length,
        active: items.filter((a: any) => a.isPublished && new Date(a.showFrom) <= now && (!a.showUntil || new Date(a.showUntil) > now)).length,
      });
    } catch (e: any) {
      message.error('تعذر تحميل الإعلانات: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({
      type: 'INFO',
      targetAudience: 'ALL',
      priority: 0,
      isDismissible: true,
    });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      const data: any = {
        title: values.title,
        body: values.body,
        type: values.type,
        targetAudience: values.targetAudience,
        priority: values.priority,
        isDismissible: values.isDismissible,
      };
      if (values.dateRange && values.dateRange[0]) {
        data.showFrom = values.dateRange[0].toISOString();
      }
      if (values.dateRange && values.dateRange[1]) {
        data.showUntil = values.dateRange[1].toISOString();
      }
      await createAnnouncement(data);
      message.success('تم إنشاء الإعلان');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handlePublish = async (a: any) => {
    try {
      await publishAnnouncement(a.id);
      message.success('تم نشر الإعلان');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (a: any) => {
    try {
      await deleteAnnouncement(a.id);
      message.success('تم حذف الإعلان');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'العنوان',
      dataIndex: 'title',
      key: 'title',
      render: (t: string, r: any) => (
        <Space direction="vertical" size={0}>
          <Text strong>{t}</Text>
          {r.priority >= 2 && <Tag color="red">حرج</Tag>}
          {r.priority === 1 && <Tag color="orange">مهم</Tag>}
        </Space>
      ),
    },
    {
      title: 'النوع',
      dataIndex: 'type',
      key: 'type',
      render: (t: string) => {
        const type = TYPE_LABELS[t] ?? { label: t, color: 'default', icon: '❓' };
        return <Tag color={type.color}>{type.icon} {type.label}</Tag>;
      },
    },
    {
      title: 'الجمهور',
      dataIndex: 'targetAudience',
      key: 'targetAudience',
      render: (a: string) => <Tag>{AUDIENCE_LABELS[a] ?? a}</Tag>,
    },
    {
      title: 'من-إلى',
      key: 'period',
      render: (r: any) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{new Date(r.showFrom).toLocaleDateString('ar-EG')}</Text>
          {r.showUntil && (
            <Text type="secondary" style={{ fontSize: 10 }}>
              إلى {new Date(r.showUntil).toLocaleDateString('ar-EG')}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'الحالة',
      dataIndex: 'isPublished',
      key: 'isPublished',
      render: (published: boolean) => published ? (
        <Tag color="green" icon={<CheckCircleOutlined />}>منشور</Tag>
      ) : (
        <Tag color="default" icon={<ClockCircleOutlined />}>مسودة</Tag>
      ),
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: any) => (
        <Space size="small">
          {!r.isPublished && (
            <Button
              type="primary"
              size="small"
              icon={<SendOutlined />}
              onClick={() => handlePublish(r)}
            >
              نشر
            </Button>
          )}
          <Button
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(r)}
          />
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <NotificationOutlined /> إعلانات النظام
        </Title>
        <Text type="secondary">إدارة الإعلانات والرسائل الجماعية للمستخدمين</Text>
      </div>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="إجمالي"
              value={stats.total}
              prefix={<NotificationOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="منشور"
              value={stats.published}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="نشط الآن"
              value={stats.active}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#35CBE0' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Actions */}
      <Card>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleCreate}
          size="large"
        >
          إنشاء إعلان جديد
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load} style={{ marginInlineStart: 8 }}>
          تحديث
        </Button>
      </Card>

      {/* Table */}
      <Card>
        {announcements.length === 0 ? (
          <Empty description="لا توجد إعلانات" />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={announcements}
            loading={loading}
            pagination={{ pageSize: 15 }}
            scroll={{ x: 1000 }}
          />
        )}
      </Card>

      {/* Create Modal */}
      <Modal
        title="إنشاء إعلان جديد"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={submitCreate}
        okText="إنشاء"
        cancelText="إلغاء"
        width={700}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="title"
            label="العنوان"
            rules={[{ required: true, message: 'مطلوب' }, { max: 200 }]}
          >
            <Input placeholder="عنوان الإعلان" />
          </Form.Item>
          <Form.Item
            name="body"
            label="المحتوى"
            rules={[{ required: true, message: 'مطلوب' }]}
          >
            <TextArea rows={4} placeholder="نص الإعلان" />
          </Form.Item>
          <Space>
            <Form.Item
              name="type"
              label="النوع"
              rules={[{ required: true }]}
            >
              <Select
                style={{ width: 180 }}
                options={Object.entries(TYPE_LABELS).map(([v, t]) => ({
                  value: v, label: `${t.icon} ${t.label}`
                }))}
              />
            </Form.Item>
            <Form.Item
              name="targetAudience"
              label="الجمهور"
              rules={[{ required: true }]}
            >
              <Select
                style={{ width: 180 }}
                options={Object.entries(AUDIENCE_LABELS).map(([v, l]) => ({ value: v, label: l }))}
              />
            </Form.Item>
            <Form.Item name="priority" label="الأولوية">
              <Select
                style={{ width: 120 }}
                options={[
                  { value: 0, label: 'عادي' },
                  { value: 1, label: 'مهم' },
                  { value: 2, label: 'حرج' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="dateRange" label="فترة العرض (اختياري)">
            <RangePicker
              showTime
              style={{ width: '100%' }}
              placeholder={['من', 'إلى']}
            />
          </Form.Item>
          <Form.Item name="isDismissible" label="يمكن للمستخدم إغلاقه" valuePropName="checked">
            <Switch defaultChecked />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
