import { useEffect, useState } from 'react';
import {
  Table, Tag, Space, Button, Modal, Form, Input, Select, message, Card,
  Statistic, Row, Col, Typography, Empty, Progress, Alert, Popconfirm
} from 'antd';
import {
  CloudUploadOutlined, DownloadOutlined, DeleteOutlined, ReloadOutlined,
  DatabaseOutlined, ClockCircleOutlined, CheckCircleOutlined, CloseCircleOutlined,
  SyncOutlined, FileZipOutlined, HddOutlined, SafetyOutlined
} from '@ant-design/icons';
import {
  getBackups, createBackup, restoreBackup, deleteBackup
} from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const TYPE_LABELS: Record<string, { label: string; color: string; icon: string }> = {
  FULL: { label: 'كامل', color: 'green', icon: '🌐' },
  INCREMENTAL: { label: 'تدريجي', color: 'blue', icon: '📈' },
  CONFIG_ONLY: { label: 'إعدادات فقط', color: 'purple', icon: '⚙️' },
  USER_DATA: { label: 'بيانات المستخدمين', color: 'cyan', icon: '👥' },
  MEDIA: { label: 'الوسائط', color: 'orange', icon: '📁' },
};

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  IN_PROGRESS: { label: 'قيد التنفيذ', color: 'blue' },
  COMPLETED: { label: 'مكتمل', color: 'green' },
  FAILED: { label: 'فشل', color: 'red' },
  VERIFIED: { label: 'متحقق', color: 'purple' },
};

const TRIGGERED_BY_LABELS: Record<string, string> = {
  SCHEDULED: 'مجدول',
  MANUAL: 'يدوي',
  API: 'API',
};

function formatBytes(bytes: number): string {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(2)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(2)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(2)} KB`;
  return `${bytes} B`;
}

export default function Backups() {
  const [backups, setBackups] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [restoreModalOpen, setRestoreModalOpen] = useState(false);
  const [selectedBackup, setSelectedBackup] = useState<any | null>(null);
  const [createForm] = Form.useForm();
  const [restoreForm] = Form.useForm();
  const [stats, setStats] = useState({ total: 0, completed: 0, inProgress: 0, totalSize: 0 });

  const load = async () => {
    setLoading(true);
    try {
      const result = await getBackups();
      const items = Array.isArray(result) ? result : result.content ?? [];
      setBackups(items);
      setStats({
        total: items.length,
        completed: items.filter((b: any) => b.status === 'COMPLETED' || b.status === 'VERIFIED').length,
        inProgress: items.filter((b: any) => b.status === 'IN_PROGRESS').length,
        totalSize: items.reduce((sum: number, b: any) => sum + (b.sizeBytes ?? 0), 0),
      });
    } catch (e: any) {
      message.error('تعذر تحميل النسخ الاحتياطية: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = () => {
    createForm.resetFields();
    createForm.setFieldsValue({ type: 'FULL' });
    setCreateModalOpen(true);
  };

  const submitCreate = async () => {
    try {
      const values = await createForm.validateFields();
      await createBackup(values.type, values.notes);
      message.success('بدأ إنشاء النسخة الاحتياطية');
      setCreateModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleRestore = (b: any) => {
    setSelectedBackup(b);
    restoreForm.resetFields();
    setRestoreModalOpen(true);
  };

  const submitRestore = async () => {
    try {
      const values = await restoreForm.validateFields();
      if (!selectedBackup) return;
      const result = await restoreBackup(selectedBackup.id, values.confirmCode);
      if (result.success) {
        message.success(`بدأ استعادة النسخة من ${formatBytes(selectedBackup.sizeBytes)}`);
        setRestoreModalOpen(false);
        load();
      } else {
        message.error('رمز التأكيد غير صحيح');
      }
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const handleDelete = async (b: any) => {
    try {
      await deleteBackup(b.id);
      message.success('تم حذف النسخة الاحتياطية');
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'النوع',
      dataIndex: 'backupType',
      key: 'backupType',
      render: (t: string) => {
        const type = TYPE_LABELS[t] ?? { label: t, color: 'default', icon: '❓' };
        return <Tag color={type.color}>{type.icon} {type.label}</Tag>;
      },
    },
    {
      title: 'الحجم',
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      render: (s: number) => (
        <Space>
          <HddOutlined />
          <Text>{s > 0 ? formatBytes(s) : '—'}</Text>
        </Space>
      ),
      sorter: (a: any, b: any) => (a.sizeBytes ?? 0) - (b.sizeBytes ?? 0),
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => {
        const status = STATUS_LABELS[s] ?? { label: s, color: 'default' };
        const icon = s === 'IN_PROGRESS' ? <SyncOutlined spin /> :
                     s === 'COMPLETED' || s === 'VERIFIED' ? <CheckCircleOutlined /> :
                     <CloseCircleOutlined />;
        return <Tag color={status.color} icon={icon}>{status.label}</Tag>;
      },
    },
    {
      title: 'SHA-256',
      dataIndex: 'checksum',
      key: 'checksum',
      render: (c: string) => c ? (
        <Text code style={{ fontSize: 10 }}>{c.slice(0, 16)}...</Text>
      ) : <Text type="secondary">—</Text>,
    },
    {
      title: 'محفز',
      dataIndex: 'triggeredBy',
      key: 'triggeredBy',
      render: (t: string) => <Tag>{TRIGGERED_BY_LABELS[t] ?? t}</Tag>,
    },
    {
      title: 'استعادة',
      dataIndex: 'restoreCount',
      key: 'restoreCount',
      render: (c: number) => c > 0 ? <Tag color="orange">{c}x</Tag> : <Text type="secondary">0</Text>,
    },
    {
      title: 'التاريخ',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (d: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{new Date(d).toLocaleDateString('ar-EG')}</Text>
          <Text type="secondary" style={{ fontSize: 10 }}>{new Date(d).toLocaleTimeString('ar-EG')}</Text>
        </Space>
      ),
      sorter: (a: any, b: any) => new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime(),
      defaultSortOrder: 'descend' as const,
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: any) => (
        <Space size="small">
          {(r.status === 'COMPLETED' || r.status === 'VERIFIED') && (
            <>
              <Button
                type="primary"
                size="small"
                icon={<DownloadOutlined />}
                onClick={() => handleRestore(r)}
              >
                استعادة
              </Button>
            </>
          )}
          <Popconfirm
            title="حذف النسخة؟"
            description="لا يمكن التراجع عن هذا الإجراء"
            onConfirm={() => handleDelete(r)}
            okText="نعم"
            cancelText="إلغاء"
          >
            <Button danger size="small" icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <DatabaseOutlined /> النسخ الاحتياطية
        </Title>
        <Text type="secondary">إنشاء واستعادة وحذف النسخ الاحتياطية</Text>
      </div>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="إجمالي"
              value={stats.total}
              prefix={<FileZipOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="مكتمل"
              value={stats.completed}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="قيد التنفيذ"
              value={stats.inProgress}
              prefix={<SyncOutlined spin />}
              valueStyle={{ color: '#E8B84A' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="الحجم الكلي"
              value={formatBytes(stats.totalSize)}
              prefix={<HddOutlined />}
              valueStyle={{ color: '#35CBE0' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Actions */}
      <Card>
        <Space>
          <Button
            type="primary"
            icon={<CloudUploadOutlined />}
            onClick={handleCreate}
            size="large"
          >
            إنشاء نسخة احتياطية جديدة
          </Button>
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      {/* Table */}
      <Card>
        {backups.length === 0 ? (
          <Empty description="لا توجد نسخ احتياطية" />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={backups}
            loading={loading}
            pagination={{ pageSize: 15, showSizeChanger: false }}
            scroll={{ x: 1200 }}
          />
        )}
      </Card>

      {/* Create Modal */}
      <Modal
        title="إنشاء نسخة احتياطية جديدة"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={submitCreate}
        okText="إنشاء"
        cancelText="إلغاء"
        okButtonProps={{ icon: <CloudUploadOutlined /> }}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="type"
            label="نوع النسخة"
            rules={[{ required: true }]}
          >
            <Select
              options={Object.entries(TYPE_LABELS).map(([v, t]) => ({
                value: v, label: `${t.icon} ${t.label}`
              }))}
            />
          </Form.Item>
          <Form.Item name="notes" label="ملاحظات (اختياري)">
            <TextArea rows={3} placeholder="سبب النسخة أو ملاحظات" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Restore Modal */}
      <Modal
        title={`استعادة النسخة الاحتياطية`}
        open={restoreModalOpen}
        onCancel={() => setRestoreModalOpen(false)}
        onOk={submitRestore}
        okText="استعادة"
        cancelText="إلغاء"
        okButtonProps={{ danger: true, icon: <DownloadOutlined /> }}
      >
        <Alert
          type="warning"
          message="تحذير: استعادة البيانات"
          description="سيتم استبدال البيانات الحالية بمحتوى النسخة الاحتياطية. هذا الإجراء لا يمكن التراجع عنه."
          showIcon
        />
        <div style={{ marginTop: 16 }}>
          <Paragraph>
            <strong>النوع:</strong> {TYPE_LABELS[selectedBackup?.backupType]?.label}
            <br />
            <strong>الحجم:</strong> {selectedBackup ? formatBytes(selectedBackup.sizeBytes) : '—'}
            <br />
            <strong>التاريخ:</strong> {selectedBackup ? new Date(selectedBackup.startedAt).toLocaleString('ar-EG') : '—'}
          </Paragraph>
        </div>
        <Form form={restoreForm} layout="vertical">
          <Form.Item
            name="confirmCode"
            label="رمز التأكيد"
            rules={[{ required: true, message: 'مطلوب' }]}
            extra="اكتب: RESTORE_CONFIRM"
          >
            <Input placeholder="RESTORE_CONFIRM" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
