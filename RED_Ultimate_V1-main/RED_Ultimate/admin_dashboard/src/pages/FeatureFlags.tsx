import { useEffect, useState } from 'react';
import {
  Card, Table, Tag, Space, Button, Switch, Modal, Form, Input, InputNumber, Select, message,
  Statistic, Row, Col, Typography, Empty, Tooltip, Alert
} from 'antd';
import {
  FlagOutlined, EditOutlined, ReloadOutlined, CheckCircleOutlined,
  CloseCircleOutlined, ExperimentOutlined, RiseOutlined
} from '@ant-design/icons';
import { getFeatureFlags, updateFeatureFlag } from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export default function FeatureFlags() {
  const [flags, setFlags] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editForm] = Form.useForm();
  const [selectedFlag, setSelectedFlag] = useState<any | null>(null);
  const [stats, setStats] = useState({ total: 0, enabled: 0, rolloutAvg: 0 });

  const load = async () => {
    setLoading(true);
    try {
      const items = await getFeatureFlags();
      setFlags(items);
      const enabled = items.filter((f: any) => f.enabled);
      setStats({
        total: items.length,
        enabled: enabled.length,
        rolloutAvg: enabled.length > 0
          ? enabled.reduce((s: number, f: any) => s + (f.rolloutPercentage ?? 0), 0) / enabled.length
          : 0,
      });
    } catch (e: any) {
      message.error('تعذر تحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleEdit = (flag: any) => {
    setSelectedFlag(flag);
    editForm.resetFields();
    editForm.setFieldsValue({
      enabled: flag.enabled,
      rolloutPercentage: flag.rolloutPercentage,
      description: flag.description,
    });
    setEditModalOpen(true);
  };

  const handleToggle = async (flag: any) => {
    try {
      await updateFeatureFlag(flag.flagName, { enabled: !flag.enabled });
      message.success(`تم ${!flag.enabled ? 'تفعيل' : 'إلغاء'} ${flag.flagName}`);
      load();
    } catch (e: any) {
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const submitEdit = async () => {
    try {
      const values = await editForm.validateFields();
      if (!selectedFlag) return;
      await updateFeatureFlag(selectedFlag.flagName, {
        enabled: values.enabled,
        rolloutPercentage: values.rolloutPercentage,
        description: values.description,
      });
      message.success('تم تحديث العلم');
      setEditModalOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error('فشل: ' + (e.message ?? ''));
    }
  };

  const columns = [
    {
      title: 'الاسم',
      dataIndex: 'flagName',
      key: 'flagName',
      render: (n: string) => (
        <Space>
          <FlagOutlined style={{ color: '#1890ff' }} />
          <Text code>{n}</Text>
        </Space>
      ),
    },
    {
      title: 'الوصف',
      dataIndex: 'description',
      key: 'description',
      render: (d: string) => d ? <Text ellipsis={{ tooltip: d }} style={{ maxWidth: 200 }}>{d}</Text> : '—',
    },
    {
      title: 'الحالة',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean, flag: any) => (
        <Switch
          checked={enabled}
          onChange={() => handleToggle(flag)}
          checkedChildren="مفعل"
          unCheckedChildren="معطل"
        />
      ),
    },
    {
      title: 'Rollout',
      dataIndex: 'rolloutPercentage',
      key: 'rolloutPercentage',
      render: (pct: number) => (
        <Space direction="vertical" size={0} style={{ width: 120 }}>
          <Text strong>{pct ?? 0}%</Text>
          <div style={{
            height: 4, background: '#1A242C', borderRadius: 2, overflow: 'hidden'
          }}>
            <div style={{
              height: '100%', width: `${pct ?? 0}%`,
              background: pct === 100 ? '#B78A2E' : pct > 0 ? '#4FC3F7' : '#666',
              transition: 'width 0.3s'
            }} />
          </div>
        </Space>
      ),
    },
    {
      title: 'تاريخ الإنشاء',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: string, flag: any) => {
        const value = d || flag.updatedAt;
        const date = value ? new Date(value) : null;
        return <Text style={{ fontSize: 12 }}>{date && Number.isFinite(date.getTime()) ? date.toLocaleDateString('ar') : '—'}</Text>;
      },
    },
    {
      title: 'إجراءات',
      key: 'actions',
      render: (r: any) => (
        <Space size="small">
          <Tooltip title="تفعيل/إلغاء">
            <Button
              size="small"
              type={r.enabled ? 'default' : 'primary'}
              icon={r.enabled ? <CloseCircleOutlined /> : <CheckCircleOutlined />}
              onClick={() => handleToggle(r)}
            >
              {r.enabled ? 'إلغاء' : 'تفعيل'}
            </Button>
          </Tooltip>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(r)}
          >
            تعديل
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#D4B16A', margin: 0 }}>
          <ExperimentOutlined /> أعلام الميزات
        </Title>
        <Text type="secondary">إدارة تفعيل الميزات بشكل تدريجي (Feature Flags)</Text>
      </div>

      <Alert
        type="info"
        message="ما هي أعلام الميزات؟"
        description="تسمح بتفعيل أو إلغاء ميزات محددة لمجموعة من المستخدمين دون الحاجة لتحديث التطبيق."
        showIcon
      />

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="إجمالي الأعلام"
              value={stats.total}
              prefix={<FlagOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="مفعل"
              value={stats.enabled}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#B78A2E' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="متوسط Rollout"
              value={stats.rolloutAvg}
              precision={0}
              suffix="%"
              prefix={<RiseOutlined />}
              valueStyle={{ color: '#4FC3F7' }}
            />
          </Card>
        </Col>
      </Row>

      <Card>
        <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
      </Card>

      <Card>
        {flags.length === 0 ? (
          <Empty description="لا توجد أعلام ميزات" />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={flags}
            loading={loading}
            pagination={{ pageSize: 20 }}
          />
        )}
      </Card>

      {/* Edit Modal */}
      <Modal
        title={`تعديل: ${selectedFlag?.flagName}`}
        open={editModalOpen}
        onCancel={() => setEditModalOpen(false)}
        onOk={submitEdit}
        okText="حفظ"
        cancelText="إلغاء"
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="enabled" label="مفعل" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item
            name="rolloutPercentage"
            label="نسبة الـ Rollout (%)"
            rules={[{ required: true }]}
          >
            <InputNumber min={0} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="الوصف">
            <TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
