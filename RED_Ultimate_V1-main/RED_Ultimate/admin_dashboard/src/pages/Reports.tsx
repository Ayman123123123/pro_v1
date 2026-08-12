import { useEffect, useState } from 'react';
import {
  Table, Tag, Space, Button, Modal, Form, Input, message, Card,
  Select, Statistic, Row, Col, Typography, Empty, Timeline, Badge
} from 'antd';
import {
  AlertOutlined, CheckOutlined, CloseOutlined, ReloadOutlined,
  UserOutlined, MessageOutlined, FileImageOutlined, ClockCircleOutlined,
  ExclamationCircleOutlined, SafetyOutlined
} from '@ant-design/icons';
import {
  getReports, resolveReport, dismissReport, assignReport
} from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const CATEGORY_LABELS: Record<string, { label: string; color: string; icon: string }> = {
  SPAM: { label: 'إزعاج', color: 'orange', icon: '🚫' },
  HARASSMENT: { label: 'تحرش', color: 'red', icon: '⚠️' },
  HATE_SPEECH: { label: 'خطاب كراهية', color: 'red', icon: '🚷' },
  VIOLENCE: { label: 'عنف', color: 'red', icon: '⚡' },
  SEXUAL: { label: 'محتوى جنسي', color: 'magenta', icon: '🔞' },
  IMPERSONATION: { label: 'انتحال شخصية', color: 'purple', icon: '🎭' },
  SCAM: { label: 'احتيال', color: 'volcano', icon: '💸' },
  MISINFORMATION: { label: 'معلومات مضللة', color: 'gold', icon: '⚠️' },
  PRIVACY_VIOLATION: { label: 'انتهاك خصوصية', color: 'geekblue', icon: '🔒' },
  OTHER: { label: 'أخرى', color: 'default', icon: '❓' },
};

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'gold',
  REVIEWING: 'blue',
  RESOLVED: 'green',
  DISMISSED: 'default',
};

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'معلقة',
  REVIEWING: 'قيد المراجعة',
  RESOLVED: 'تم الحل',
  DISMISSED: 'مرفوضة',
};

const RESOLUTION_LABELS: Record<string, string> = {
  WARNING_ISSUED: 'تحذير صادر',
  USER_BANNED: 'مستخدم محظور',
  CONTENT_REMOVED: 'محتوى محذوف',
  NO_ACTION: 'لا إجراء',
};

export default function Reports() {
  const [reports, setReports] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [total, setTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>('PENDING');
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [dismissModalOpen, setDismissModalOpen] = useState(false);
  const [selectedReport, setSelectedReport] = useState<any | null>(null);
  const [resolveForm] = Form.useForm();
  const [dismissForm] = Form.useForm();
  const [stats, setStats] = useState({ pending: 0, resolved: 0, dismissed: 0, today: 0 });

  const load = async () => {
    setLoading(true);
    try {
      const result = await getReports({
        page, size,
        status: statusFilter,
        category: categoryFilter,
      });
      const items = Array.isArray(result) ? result : result.content ?? [];
      setReports(items);
      setTotal(Array.isArray(result) ? items.length : (result.totalElements ?? items.length));
      // Update stats
      setStats({
        pending: items.filter((r: any) => r.status === 'PENDING').length,
        resolved: items.filter((r: any) => r.status === 'RESOLVED').length,
        dismissed: items.filter((r: any) => r.status === 'DISMISSED').length,
        today: items.filter((r: any) => {
          const d = new Date(r.createdAt);
          const today = new Date();
          return d.toDateString() === today.toDateString();
        }).length,
      });
    } catch (e: any) {
      message.error('تعذر تحميل البلاغات: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [statusFilter, categoryFilter, page]);

  const handleResolve = (r: any) => {
    setSelectedReport(r);
    resolveForm.resetFields();
    setResolveModalOpen(true);
  };

  const handleDismiss = (r: any) => {
    setSelectedReport(r);
    dismissForm.resetFields();
    setDismissModalOpen(true);
  };

  const submitResolve = async () => {
    try {
      const values = await resolveForm.validateFields();
      if (!selectedReport) return;
      await resolveReport(selectedReport.id, values.resolution, values.notes);
      message.success('تم حل البلاغ');
      setResolveModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const submitDismiss = async () => {
    try {
      const values = await dismissForm.validateFields();
      if (!selectedReport) return;
      await dismissReport(selectedReport.id, values.notes);
      message.success('تم رفض البلاغ');
      setDismissModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'التصنيف',
      dataIndex: 'category',
      key: 'category',
      render: (c: string) => {
        const cat = CATEGORY_LABELS[c] ?? { label: c, color: 'default', icon: '❓' };
        return <Tag color={cat.color}>{cat.icon} {cat.label}</Tag>;
      },
    },
    {
      title: 'المُبلِّغ',
      dataIndex: 'reporterId',
      key: 'reporterId',
      render: (id: string) => (
        <Space>
          <UserOutlined />
          <Text code style={{ fontSize: 11 }}>{id.slice(0, 8)}...</Text>
        </Space>
      ),
    },
    {
      title: 'المُبلَّغ عنه',
      dataIndex: 'targetUserId',
      key: 'targetUserId',
      render: (_id: string, r: any) => {
        const id = r.targetUserId || r.reportedUserId;
        return id ? (
          <Text code style={{ fontSize: 11 }}>{String(id).slice(0, 8)}...</Text>
        ) : <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'المحتوى',
      dataIndex: 'targetContentType',
      key: 'targetContentType',
      render: (_type: string, r: any) => {
        const type = r.targetContentType || r.contentType;
        return type ? <Tag>{type}</Tag> : <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'السبب',
      dataIndex: 'reason',
      key: 'reason',
      render: (_reason: string, r: any) => {
        const reason = r.reason || r.description;
        return (
          <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: reason }}>
            {reason || '—'}
          </Text>
        );
      },
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={STATUS_COLORS[s]}>{STATUS_LABELS[s] ?? s}</Tag>,
    },
    {
      title: 'التاريخ',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{new Date(d).toLocaleDateString('ar-EG')}</Text>
          <Text type="secondary" style={{ fontSize: 10 }}>
            <ClockCircleOutlined /> {new Date(d).toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit' })}
          </Text>
        </Space>
      ),
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: any) => (
        <Space size="small">
          {r.status === 'PENDING' && (
            <>
              <Button
                type="primary"
                size="small"
                icon={<CheckOutlined />}
                onClick={() => handleResolve(r)}
              >
                حل
              </Button>
              <Button
                size="small"
                icon={<CloseOutlined />}
                onClick={() => handleDismiss(r)}
              >
                رفض
              </Button>
            </>
          )}
          {r.status === 'RESOLVED' && r.resolution && (
            <Tag color="green">{RESOLUTION_LABELS[r.resolution] ?? r.resolution}</Tag>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <AlertOutlined /> مراقبة المحتوى
        </Title>
        <Text type="secondary">مراجعة البلاغات واتخاذ الإجراءات المناسبة</Text>
      </div>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="معلقة"
              value={stats.pending}
              prefix={<ExclamationCircleOutlined />}
              valueStyle={{ color: '#E8B84A' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="تم حلها"
              value={stats.resolved}
              prefix={<CheckOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="مرفوضة"
              value={stats.dismissed}
              prefix={<CloseOutlined />}
              valueStyle={{ color: '#8c8c8c' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="اليوم"
              value={stats.today}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#35CBE0' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Filters */}
      <Card>
        <Space wrap>
          <Select
            value={statusFilter}
            style={{ width: 150 }}
            onChange={setStatusFilter}
            options={Object.entries(STATUS_LABELS).map(([v, l]) => ({ value: v, label: l }))}
          />
          <Select
            placeholder="التصنيف"
            allowClear
            style={{ width: 180 }}
            onChange={setCategoryFilter}
            value={categoryFilter}
            options={Object.entries(CATEGORY_LABELS).map(([v, c]) => ({ value: v, label: c.label }))}
          />
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      {/* Table */}
      <Card>
        {reports.length === 0 ? (
          <Empty description="لا توجد بلاغات" />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={reports}
            loading={loading}
            pagination={{
              current: page + 1,
              pageSize: size,
              total,
              onChange: (p) => setPage(p - 1),
              showSizeChanger: false,
            }}
            scroll={{ x: 1200 }}
          />
        )}
      </Card>

      {/* Resolve Modal */}
      <Modal
        title="حل البلاغ"
        open={resolveModalOpen}
        onCancel={() => setResolveModalOpen(false)}
        onOk={submitResolve}
        okText="حل"
        cancelText="إلغاء"
        okButtonProps={{ type: 'primary' }}
      >
        <Paragraph>سيتم اتخاذ إجراء ضد المستخدم المُبلَّغ عنه.</Paragraph>
        <Form form={resolveForm} layout="vertical">
          <Form.Item name="resolution" label="الإجراء" rules={[{ required: true, message: 'اختر إجراء' }]}>
            <Select
              options={Object.entries(RESOLUTION_LABELS).map(([v, l]) => ({ value: v, label: l }))}
            />
          </Form.Item>
          <Form.Item name="notes" label="ملاحظات (اختياري)">
            <TextArea rows={3} placeholder="ملاحظات إضافية" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Dismiss Modal */}
      <Modal
        title="رفض البلاغ"
        open={dismissModalOpen}
        onCancel={() => setDismissModalOpen(false)}
        onOk={submitDismiss}
        okText="رفض"
        cancelText="إلغاء"
      >
        <Paragraph type="secondary">
          البلاغ سيتم رفضه. لا يتم اتخاذ أي إجراء ضد المُبلَّغ عنه.
        </Paragraph>
        <Form form={dismissForm} layout="vertical">
          <Form.Item name="notes" label="سبب الرفض (اختياري)">
            <TextArea rows={3} placeholder="لماذا يتم رفض هذا البلاغ؟" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
