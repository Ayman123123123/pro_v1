import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Empty, Form, Input, InputNumber, List, Modal,
  Popconfirm, Row, Select, Space, Statistic, Switch, Table, Tag, Tooltip, Typography, message,
} from 'antd';
import {
  ClockCircleOutlined, CopyOutlined, DeleteOutlined, EditOutlined,
  PlusOutlined, ReloadOutlined, SendOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * صفحة قوالب SMS — إدارة قوالب الرسائل الجاهزة للإرسال عبر بوابة DINSTAR.
 *
 * تتيح للمسؤول:
 * 1. إنشاء قوالب SMS مع متغيرات (اسم المستخدم، الكود، إلخ)
 * 2. جدولة رسائل للإرسال لاحقًا
 * 3. إرسال سريع من القالب
 * 4. نسخ القالب للحافظة
 *
 * المتغيرات المدعومة:
 * {{name}} — اسم المستخدم
 * {{code}} — رمز التحقق
 * {{link}} — رابط
 * {{date}} — التاريخ
 */

type SmsTemplate = {
  id: string;
  name: string;
  text: string;
  encoding: string;
  category: string;
  variables: string[];
  createdAt: string;
  usageCount: number;
};

type ScheduledSms = {
  id: string;
  templateId: string;
  templateName: string;
  recipients: string[];
  scheduledAt: string;
  status: string;
  gatewayHost: string | null;
  variables: Record<string, string>;
};

const ENCODING_LABELS: Record<string, string> = {
  'gsm-7bit': 'GSM 7-bit (160 حرف)',
  'unicode': 'Unicode (70 حرف)',
};

const CATEGORY_COLORS: Record<string, string> = {
  'verification': 'blue',
  'notification': 'gold',
  'marketing': 'orange',
  'support': 'purple',
  'custom': 'default',
};

const CATEGORY_LABELS: Record<string, string> = {
  'verification': 'تحقق',
  'notification': 'إشعار',
  'marketing': 'تسويق',
  'support': 'دعم',
  'custom': 'مخصص',
};

const STATUS_COLORS: Record<string, string> = {
  'PENDING': 'processing',
  'SENT': 'success',
  'DELIVERED': 'success',
  'FAILED': 'error',
  'CANCELLED': 'default',
};

export default function SmsTemplates() {
  const [templates, setTemplates] = useState<SmsTemplate[]>([]);
  const [scheduled, setScheduled] = useState<ScheduledSms[]>([]);
  const [loading, setLoading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [sendOpen, setSendOpen] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<SmsTemplate | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<SmsTemplate | null>(null);
  const [form] = Form.useForm();
  const [sendForm] = Form.useForm();
  const [scheduleForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [tRes, sRes] = await Promise.all([
        apiFetch('/api/admin/dinstar/sms/templates'),
        apiFetch('/api/admin/dinstar/sms/scheduled'),
      ]);
      if (tRes.ok) {
        const tData = await tRes.json();
        setTemplates(Array.isArray(tData) ? tData : (tData.templates || []));
      }
      if (sRes.ok) {
        const sData = await sRes.json();
        setScheduled(Array.isArray(sData) ? sData : (sData.scheduled || []));
      }
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل القوالب');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // إحصائيات
  const stats = useMemo(() => ({
    total: templates.length,
    totalUsage: templates.reduce((s, t) => s + t.usageCount, 0),
    pending: scheduled.filter((s) => s.status === 'PENDING').length,
    delivered: scheduled.filter((s) => ['SENT', 'DELIVERED'].includes(s.status)).length,
  }), [templates, scheduled]);

  // فتح نافذة التعديل
  const openEdit = (template?: SmsTemplate) => {
    if (template) {
      setEditingTemplate(template);
      form.setFieldsValue({
        name: template.name,
        text: template.text,
        encoding: template.encoding,
        category: template.category,
      });
    } else {
      setEditingTemplate(null);
      form.resetFields();
      form.setFieldsValue({ encoding: 'gsm-7bit', category: 'custom' });
    }
    setEditOpen(true);
  };

  // حفظ القالب
  const saveTemplate = async () => {
    try {
      const values = await form.validateFields();
      const method = editingTemplate ? 'PUT' : 'POST';
      const url = editingTemplate
        ? `/api/admin/dinstar/sms/templates/${editingTemplate.id}`
        : '/api/admin/dinstar/sms/templates';
      const res = await apiFetch(url, {
        method,
        body: JSON.stringify(values),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      message.success(editingTemplate ? 'حُدّث القالب' : 'أُنشئ القالب');
      setEditOpen(false);
      load();
    } catch (e: any) {
      message.error(e.message || 'تعذر الحفظ');
    }
  };

  // حذف القالب
  const deleteTemplate = async (id: string) => {
    try {
      const res = await apiFetch(`/api/admin/dinstar/sms/templates/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success('حُذف القالب');
      load();
    } catch (e: any) {
      message.error(e.message || 'تعذر الحذف');
    }
  };

  // فتح نافذة الإرسال
  const openSend = (template: SmsTemplate) => {
    setSelectedTemplate(template);
    sendForm.resetFields();
    setSendOpen(true);
  };

  // فتح نافذة الجدولة
  const openSchedule = (template: SmsTemplate) => {
    setSelectedTemplate(template);
    scheduleForm.resetFields();
    setScheduleOpen(true);
  };

  // إرسال SMS من القالب
  const sendFromTemplate = async () => {
    if (!selectedTemplate) return;
    try {
      const values = await sendForm.validateFields();
      const numbers = values.numbers
        .split(/[\s,،;]+/)
        .map((n: string) => n.trim())
        .filter(Boolean);

      if (numbers.length === 0) throw new Error('أدخل رقمًا واحدًا على الأقل');
      if (numbers.length > 128) throw new Error('الحد الأقصى 128 مستلمًا');

      // استبدال المتغيرات
      let text = selectedTemplate.text;
      if (values.variables) {
        Object.entries(values.variables).forEach(([key, val]) => {
          text = text.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), String(val));
        });
      }

      const res = await apiFetch('/api/admin/dinstar/sms/send', {
        method: 'POST',
        body: JSON.stringify({
          text,
          encoding: selectedTemplate.encoding === 'unicode' ? 'UCS2' : 'GSM7BIT',
          gatewayHost: values.gatewayHost || undefined,
          param: numbers.map((n: string, i: number) => ({ number: n, user_id: i + 1 })),
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success(`أُرسلت الرسالة إلى ${numbers.length} مستلمًا`);
      setSendOpen(false);
    } catch (e: any) {
      message.error(e.message || 'تعذر الإرسال');
    }
  };

  // جدولة SMS
  const scheduleSms = async () => {
    if (!selectedTemplate) return;
    try {
      const values = await scheduleForm.validateFields();
      const res = await apiFetch('/api/admin/dinstar/sms/schedule', {
        method: 'POST',
        body: JSON.stringify({
          templateId: selectedTemplate.id,
          scheduledAt: values.scheduledAt.toISOString(),
          recipients: values.recipients
            .split(/[\s,،;]+/)
            .map((n: string) => n.trim())
            .filter(Boolean),
          gatewayHost: values.gatewayHost || null,
          variables: values.variables || {},
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      message.success('جُدوِلت الرسالة');
      setScheduleOpen(false);
      load();
    } catch (e: any) {
      message.error(e.message || 'تعذر الجدولة');
    }
  };

  // نسخ القالب
  const copyTemplate = (template: SmsTemplate) => {
    navigator.clipboard.writeText(template.text).then(() => {
      message.success('نُسخ القالب إلى الحافظة');
    });
  };

  // استخراج المتغيرات من النص
  const extractVariables = (text: string): string[] => {
    const matches = text.match(/\{\{(\w+)\}\}/g) || [];
    return [...new Set(matches.map((m) => m.replace(/\{\{|\}\}/g, '')))];
  };

  // حساب البايت
  const textBytes = (text: string): number => new TextEncoder().encode(text).length;

  const columns = [
    {
      title: 'اسم القالب',
      dataIndex: 'name',
      width: 180,
      render: (v: string) => <strong>{v}</strong>,
    },
    {
      title: 'النص',
      dataIndex: 'text',
      render: (v: string) => (
        <Tooltip title={v}>
          <Typography.Text>{v}</Typography.Text>
        </Tooltip>
      ),
    },
    {
      title: 'الفئة',
      dataIndex: 'category',
      width: 90,
      render: (v: string) => (
        <Tag color={CATEGORY_COLORS[v] || 'default'}>
          {CATEGORY_LABELS[v] || v}
        </Tag>
      ),
    },
    {
      title: 'الترميز',
      dataIndex: 'encoding',
      width: 120,
      render: (v: string) => (
        <Typography.Text style={{ fontSize: 11 }}>
          {ENCODING_LABELS[v] || v}
        </Typography.Text>
      ),
    },
    {
      title: 'المتغيرات',
      dataIndex: 'variables',
      width: 150,
      render: (v: string[]) => v && v.length > 0 ? (
        <Space wrap>
          {v.map((varName) => (
            <Tag key={varName} color="cyan" style={{ fontSize: 10 }}>
              {`{{${varName}}}`}
            </Tag>
          ))}
        </Space>
      ) : <Typography.Text type="secondary">—</Typography.Text>,
    },
    {
      title: 'الاستخدام',
      dataIndex: 'usageCount',
      width: 80,
      render: (v: number) => <Badge count={v} style={{ backgroundColor: '#B78A2E' }} />,
    },
    {
      title: 'إجراءات',
      width: 180,
      fixed: 'right' as const,
      render: (_: any, r: SmsTemplate) => (
        <Space>
          <Tooltip title="إرسال سريع">
            <Button type="link" icon={<SendOutlined />} size="small" onClick={() => openSend(r)} />
          </Tooltip>
          <Tooltip title="جدولة">
            <Button type="link" icon={<ClockCircleOutlined />} size="small" onClick={() => openSchedule(r)} />
          </Tooltip>
          <Tooltip title="نسخ النص">
            <Button type="link" icon={<CopyOutlined />} size="small" onClick={() => copyTemplate(r)} />
          </Tooltip>
          <Tooltip title="تعديل">
            <Button type="link" icon={<EditOutlined />} size="small" onClick={() => openEdit(r)} />
          </Tooltip>
          <Popconfirm title="حذف القالب؟" onConfirm={() => deleteTemplate(r.id)} okText="حذف" cancelText="إلغاء">
            <Button type="link" danger icon={<DeleteOutlined />} size="small" />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const scheduledColumns = [
    {
      title: 'القالب',
      dataIndex: 'templateName',
      width: 150,
    },
    {
      title: 'المستلمون',
      dataIndex: 'recipients',
      width: 120,
      render: (v: string[]) => `${v.length} مستلم`,
    },
    {
      title: 'الوقت المجدوَل',
      dataIndex: 'scheduledAt',
      width: 160,
      render: (v: string) => new Date(v).toLocaleString('ar'),
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => (
        <Tag color={STATUS_COLORS[v] || 'default'}>{v}</Tag>
      ),
    },
  ];

  // استخراج المتغيرات عند تغيير النص
  const templateText = Form.useWatch('text', form) || '';
  const detectedVariables = useMemo(() => extractVariables(templateText), [templateText]);
  const templateByteCount = useMemo(() => textBytes(templateText), [templateText]);

  return (
    <div>
      {/* إحصائيات */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic title="إجمالي القوالب" value={stats.total} valueStyle={{ color: '#B78A2E' }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic title="مرات الاستخدام" value={stats.totalUsage} valueStyle={{ color: '#4FC3F7' }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic title="مجدوَلَة" value={stats.pending} valueStyle={{ color: '#E0A83C' }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic title="مُرسَلة" value={stats.delivered} valueStyle={{ color: '#1976D2' }} />
          </Card>
        </Col>
      </Row>

      {/* القوالب */}
      <Card
        title="قوالب الرسائل SMS"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>تحديث</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>قالب جديد</Button>
          </Space>
        }
      >
        <Table
          dataSource={templates}
          columns={columns}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: <Empty description="لا توجد قوالب — أنشئ قالبًا جديدًا" /> }}
        />
      </Card>

      {/* الرسائل المجدوَلَة */}
      {scheduled.length > 0 && (
        <Card title="الرسائل المجدوَلَة" style={{ marginTop: 16 }} size="small">
          <Table
            dataSource={scheduled}
            columns={scheduledColumns}
            rowKey="id"
            size="small"
            pagination={false}
          />
        </Card>
      )}

      {/* نافذة إنشاء/تعديل القالب */}
      <Modal
        title={editingTemplate ? `تعديل القالب: ${editingTemplate.name}` : 'قالب جديد'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={saveTemplate}
        okText="حفظ"
        cancelText="إلغاء"
        width={640}
      >
        <Form form={form} layout="vertical" initialValues={{ encoding: 'gsm-7bit', category: 'custom' }}>
          <Form.Item name="name" label="اسم القالب" rules={[{ required: true, message: 'مطلوب' }]}>
            <Input placeholder="مثال: رسالة ترحيب، رمز التحقق" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="category" label="الفئة">
                <Select
                  options={Object.entries(CATEGORY_LABELS).map(([v, l]) => ({ value: v, label: l }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="encoding" label="الترميز">
                <Select
                  options={[
                    { value: 'gsm-7bit', label: 'GSM 7-bit (160 حرف/مقطع)' },
                    { value: 'unicode', label: 'Unicode (70 حرف/مقطع)' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="text"
            label="نص القالب"
            rules={[{ required: true, message: 'نص الرسالة مطلوب' }]}
            extra={
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                  {templateByteCount} بايت
                  {templateByteCount > 1500 && <Typography.Text type="danger"> — يتجاوز الحد (1500 بايت)</Typography.Text>}
                </Typography.Text>
                {detectedVariables.length > 0 && (
                  <div style={{ marginTop: 4 }}>
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>المتغيرات المكتشفة: </Typography.Text>
                    {detectedVariables.map((v) => (
                      <Tag key={v} color="cyan" style={{ fontSize: 10 }}>{`{{${v}}}`}</Tag>
                    ))}
                  </div>
                )}
              </div>
            }
          >
            <Input.TextArea rows={4} placeholder="مرحبًا {{name}}، رمز التحقق: {{code}}" />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message={
              <>
                المتغيرات المدعومة:{' '}
                <code>{'{{name}}'}</code> اسم · <code>{'{{code}}'}</code> رمز ·{' '}
                <code>{'{{link}}'}</code> رابط · <code>{'{{date}}'}</code> تاريخ
              </>
            }
          />
        </Form>
      </Modal>

      {/* نافذة الإرسال السريع */}
      <Modal
        title={`إرسال: ${selectedTemplate?.name || ''}`}
        open={sendOpen}
        onCancel={() => setSendOpen(false)}
        onOk={sendFromTemplate}
        okText="إرسال"
        cancelText="إلغاء"
        width={560}
      >
        <Form form={sendForm} layout="vertical">
          <Form.Item
            name="numbers"
            label="أرقام المستلمين"
            rules={[{ required: true, message: 'أدخل رقمًا واحدًا على الأقل' }]}
            extra="مفصولة بفاصلة أو مسافة — حد أقصى 128 مستلمًا"
          >
            <Input.TextArea rows={2} placeholder="777123456, 733445566" />
          </Form.Item>
          {(selectedTemplate?.variables?.length ?? 0) > 0 && (
            <Card size="small" title="قيم المتغيرات" style={{ marginBottom: 12 }}>
              {(selectedTemplate?.variables ?? []).map((varName) => (
                <Form.Item key={varName} name={['variables', varName]} label={`{{${varName}}}`} style={{ marginBottom: 8 }}>
                  <Input placeholder={`قيمة ${varName}`} />
                </Form.Item>
              ))}
            </Card>
          )}
        </Form>
        <Alert
          type="info"
          message="معاينة النص"
          description={selectedTemplate?.text || ''}
          style={{ marginTop: 8 }}
        />
      </Modal>

      {/* نافذة الجدولة */}
      <Modal
        title={`جدولة: ${selectedTemplate?.name || ''}`}
        open={scheduleOpen}
        onCancel={() => setScheduleOpen(false)}
        onOk={scheduleSms}
        okText="جدولة"
        cancelText="إلغاء"
        width={560}
      >
        <Form form={scheduleForm} layout="vertical">
          <Form.Item
            name="recipients"
            label="أرقام المستلمين"
            rules={[{ required: true, message: 'أدخل رقمًا واحدًا على الأقل' }]}
          >
            <Input.TextArea rows={2} placeholder="777123456, 733445566" />
          </Form.Item>
          <Form.Item
            name="scheduledAt"
            label="وقت الإرسال"
            rules={[{ required: true, message: 'حدد وقت الإرسال' }]}
          >
            <Input type="datetime-local" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
