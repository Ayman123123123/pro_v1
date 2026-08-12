import { useCallback, useState } from 'react';
import { Card, Row, Col, Statistic, Button, Modal, Input, Alert, Tag, Space, Table, message } from 'antd';
import { SafetyOutlined, WarningOutlined, DeleteOutlined, LockOutlined } from '@ant-design/icons';
import { activateKillSwitch, getAuditLog, getOperationsOverview, requestSecurityWipe } from '../../api';
import { usePolling } from '../../hooks/usePolling';

const SecurityTab: React.FC = () => {
  const [killSwitchModal, setKillSwitchModal] = useState(false);
  const [wipeModal, setWipeModal] = useState(false);
  const [targetUserId, setTargetUserId] = useState('');
  const [reason, setReason] = useState('');
  const [securityEvents, setSecurityEvents] = useState<any[]>([]);
  const [operational, setOperational] = useState<any>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [audit, overview] = await Promise.all([
        getAuditLog({ page: 0, size: 20 }),
        getOperationsOverview().catch(() => null),
      ]);
      setSecurityEvents(Array.isArray(audit?.content) ? audit.content : []);
      setOperational(overview);
      setError('');
    } catch (e: any) {
      setError(e?.message || 'تعذر تحميل أحداث الأمان');
      setSecurityEvents([]);
    }
  }, []);

  usePolling(load, 15000);

  const handleKillSwitch = async () => {
    if (!reason.trim()) { message.error('أدخل سبب تفعيل Kill Switch'); return; }
    try {
      await activateKillSwitch(reason);
      message.success('تم تفعيل Kill Switch');
      setKillSwitchModal(false);
      setReason('');
      await load();
    } catch (e: any) {
      message.error(e?.message || 'فشل Kill Switch');
    }
  };

  const handleWipe = async () => {
    if (!targetUserId.trim()) { message.error('أدخل معرّف المستخدم'); return; }
    try {
      await requestSecurityWipe(targetUserId.trim());
      message.success('أُرسل أمر المسح');
      setWipeModal(false);
      setTargetUserId('');
      await load();
    } catch (e: any) {
      message.error(e?.message || 'فشل المسح');
    }
  };

  return (
    <div>
      <Alert
        message="مركز عمليات الأمان"
        description="إدارة المسح عن بُعد وKill Switch. كل إجراء يُسجَّل في التدقيق."
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="تنبيهات 24 ساعة" value={operational?.moderation?.securityAlerts24h ?? 0}
              prefix={<SafetyOutlined />} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="أجهزة ملغاة" value={operational?.devices?.revoked ?? 0}
              prefix={<LockOutlined />} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="جلسات تجديد نشطة" value={operational?.devices?.activeRefreshSessions ?? 0}
              prefix={<SafetyOutlined />} valueStyle={{ color: '#1890ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic title="بلاغات مفتوحة" value={operational?.moderation?.openReports ?? 0}
              prefix={<WarningOutlined />} valueStyle={{ color: '#faad14' }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={12}>
          <Card title="إجراءات الطوارئ">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button danger block icon={<WarningOutlined />} size="large"
                onClick={() => setKillSwitchModal(true)}>
                Kill Switch — مسح كل الأجهزة
              </Button>
              <Button type="primary" danger block icon={<DeleteOutlined />}
                onClick={() => setWipeModal(true)}>
                مسح عن بُعد — حساب واحد
              </Button>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="أحداث الأمان الأخيرة" extra={<Button size="small" onClick={load}>تحديث</Button>}>
            <Table
              dataSource={securityEvents}
              rowKey="id"
              columns={[
                { title: 'الإجراء', dataIndex: 'action', render: (v: string) => <Tag color={String(v || '').includes('KILL') ? 'red' : 'blue'}>{v}</Tag> },
                { title: 'الهدف', dataIndex: 'targetId', render: (v: string) => v || '—' },
                { title: 'المدير', dataIndex: 'adminUsername', render: (v: string) => v || 'SYSTEM' },
                { title: 'الوقت', dataIndex: 'createdAt', render: (v: string) => (v ? new Date(v).toLocaleString('ar') : '—') },
              ]}
              locale={{ emptyText: 'لا توجد أحداث تدقيق مسجلة' }}
              pagination={{ pageSize: 8 }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <Modal title="تأكيد Kill Switch" open={killSwitchModal}
        onOk={handleKillSwitch} onCancel={() => setKillSwitchModal(false)}
        okButtonProps={{ danger: true }} okText="تأكيد المسح الشامل">
        <Alert message="سيتم مسح كل الأجهزة فوراً!" type="error" showIcon />
        <Input.TextArea style={{ marginTop: 16 }} placeholder="سبب تفعيل Kill Switch..."
          value={reason} onChange={e => setReason(e.target.value)} rows={3} />
      </Modal>

      <Modal title="مسح عن بُعد — حساب واحد" open={wipeModal}
        onOk={handleWipe} onCancel={() => setWipeModal(false)}
        okButtonProps={{ danger: true }}>
        <Input placeholder="معرّف المستخدم (UUID أو RED ID)" value={targetUserId}
          onChange={e => setTargetUserId(e.target.value)} />
      </Modal>
    </div>
  );
};

export default SecurityTab;
