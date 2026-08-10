import { useEffect, useState } from 'react';
import {
  Table, Tag, Space, Card, Select, Button, Input, Statistic, Row, Col,
  Typography, Empty, Tabs, Tooltip
} from 'antd';
import {
  AuditOutlined, ReloadOutlined, SearchOutlined, UserOutlined,
  ClockCircleOutlined, ExclamationCircleOutlined, CheckCircleOutlined,
  WarningOutlined, CodeOutlined
} from '@ant-design/icons';
import { getAuditLog, getSecurityAlerts } from '../api';

const { Title, Text, Paragraph } = Typography;
const { Search } = Input;

const ACTION_LABELS: Record<string, { label: string; color: string; category: string }> = {
  USER_APPROVED: { label: 'موافقة على مستخدم', color: 'green', category: 'USER' },
  USER_BANNED: { label: 'حظر مستخدم', color: 'red', category: 'USER' },
  USER_REJECTED: { label: 'رفض مستخدم', color: 'orange', category: 'USER' },
  USER_UNBANNED: { label: 'رفع حظر', color: 'blue', category: 'USER' },
  USER_PROMOTED: { label: 'ترقية مستخدم', color: 'purple', category: 'USER' },
  USER_DELETED: { label: 'حذف مستخدم', color: 'red', category: 'USER' },
  USERS_LISTED: { label: 'عرض قائمة المستخدمين', color: 'default', category: 'USER' },
  USER_DETAIL_VIEWED: { label: 'عرض تفاصيل مستخدم', color: 'default', category: 'USER' },
  MEDIA_DELETED: { label: 'حذف وسائط', color: 'red', category: 'MEDIA' },
  GROUP_DELETED: { label: 'حذف مجموعة', color: 'red', category: 'CONTENT' },
  CONFIG_CHANGED: { label: 'تغيير إعدادات', color: 'orange', category: 'SYSTEM' },
  REPORT_RESOLVED: { label: 'حل بلاغ', color: 'green', category: 'CONTENT' },
  REPORT_DISMISSED: { label: 'رفض بلاغ', color: 'default', category: 'CONTENT' },
  SESSION_TERMINATED: { label: 'إنهاء جلسة', color: 'orange', category: 'SECURITY' },
  SESSIONS_CLEANED: { label: 'تنظيف الجلسات', color: 'default', category: 'SECURITY' },
  FEATURE_FLAG_UPDATED: { label: 'تحديث علم ميزة', color: 'purple', category: 'SYSTEM' },
  ANNOUNCEMENT_CREATED: { label: 'إنشاء إعلان', color: 'blue', category: 'SYSTEM' },
  ANNOUNCEMENT_PUBLISHED: { label: 'نشر إعلان', color: 'green', category: 'SYSTEM' },
  ANNOUNCEMENT_DELETED: { label: 'حذف إعلان', color: 'red', category: 'SYSTEM' },
  BACKUP_STARTED: { label: 'بدء نسخة احتياطية', color: 'blue', category: 'SYSTEM' },
  BACKUP_RESTORED: { label: 'استعادة نسخة', color: 'red', category: 'SYSTEM' },
  BACKUP_DELETED: { label: 'حذف نسخة', color: 'red', category: 'SYSTEM' },
};

const CATEGORY_COLORS: Record<string, string> = {
  USER: 'blue',
  MEDIA: 'orange',
  CONTENT: 'cyan',
  SYSTEM: 'purple',
  SECURITY: 'red',
  MODERATION: 'magenta',
};

const SEVERITY_COLORS: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'orange',
  CRITICAL: 'red',
};

function getActionMeta(action: string) {
  return ACTION_LABELS[action] ?? { label: action, color: 'default', category: 'SYSTEM' };
}

export default function AuditLog() {
  const [logs, setLogs] = useState<any[]>([]);
  const [alerts, setAlerts] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [total, setTotal] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [adminFilter, setAdminFilter] = useState('');
  const [activeTab, setActiveTab] = useState('all');
  const [stats, setStats] = useState({ total: 0, critical: 0, todayCount: 0, uniqueAdmins: 0 });

  const load = async () => {
    setLoading(true);
    try {
      const params: any = { page, size };
      if (categoryFilter) params.category = categoryFilter;
      if (adminFilter) params.adminId = adminFilter;
      const result = await getAuditLog(params);
      const items = Array.isArray(result) ? result : result.content ?? [];
      setLogs(items);
      setTotal(Array.isArray(result) ? items.length : (result.totalElements ?? items.length));
      // Update stats
      const today = new Date().toDateString();
      setStats({
        total: items.length,
        critical: items.filter((l: any) => l.severity === 'CRITICAL').length,
        todayCount: items.filter((l: any) => new Date(l.createdAt).toDateString() === today).length,
        uniqueAdmins: new Set(items.map((l: any) => l.adminId).filter(Boolean)).size,
      });
    } catch (e: any) {
      message.error('تعذر التحميل: ' + (e.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  const loadAlerts = async () => {
    try {
      const result = await getSecurityAlerts({ page: 0, size: 50, severity: 'CRITICAL' });
      const items = Array.isArray(result) ? result : result.content ?? [];
      setAlerts(items);
    } catch (e: any) {
      // ignore
    }
  };

  useEffect(() => { load(); loadAlerts(); }, [categoryFilter, page]);

  const columns = [
    {
      title: 'الإجراء',
      dataIndex: 'action',
      key: 'action',
      render: (a: string) => {
        const meta = getActionMeta(a);
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: 'التصنيف',
      dataIndex: 'category',
      key: 'category',
      render: (c: string) => <Tag color={CATEGORY_COLORS[c] ?? 'default'}>{c}</Tag>,
    },
    {
      title: 'المسؤول',
      dataIndex: 'adminUsername',
      key: 'adminUsername',
      render: (u: string, r: any) => (
        <Space direction="vertical" size={0}>
          <Space>
            <UserOutlined />
            <Text strong>{u ?? 'system'}</Text>
          </Space>
          {r.adminId && <Text type="secondary" style={{ fontSize: 10 }}>{r.adminId.slice(0, 8)}...</Text>}
        </Space>
      ),
    },
    {
      title: 'الهدف',
      key: 'target',
      render: (r: any) => r.targetId ? (
        <Space direction="vertical" size={0}>
          <Tag>{r.targetType}</Tag>
          <Text code style={{ fontSize: 10 }}>{r.targetId.slice(0, 12)}...</Text>
        </Space>
      ) : '—',
    },
    {
      title: 'الخطورة',
      dataIndex: 'severity',
      key: 'severity',
      render: (s: string) => {
        const icon = s === 'CRITICAL' ? <ExclamationCircleOutlined /> :
                     s === 'WARNING' ? <WarningOutlined /> : <CheckCircleOutlined />;
        return <Tag color={SEVERITY_COLORS[s] ?? 'default'} icon={icon}>{s ?? 'INFO'}</Tag>;
      },
    },
    {
      title: 'التفاصيل',
      dataIndex: 'description',
      key: 'description',
      render: (d: string) => d ? (
        <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: d }}>{d}</Text>
      ) : '—',
    },
    {
      title: 'IP',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      render: (ip: string) => ip ? (
        <Text code style={{ fontSize: 10 }}>{ip}</Text>
      ) : '—',
    },
    {
      title: 'الوقت',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: string) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 12 }}>{new Date(d).toLocaleDateString('ar-EG')}</Text>
          <Text type="secondary" style={{ fontSize: 10 }}>
            <ClockCircleOutlined /> {new Date(d).toLocaleTimeString('ar-EG')}
          </Text>
        </Space>
      ),
      sorter: (a: any, b: any) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      defaultSortOrder: 'descend' as const,
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <AuditOutlined /> سجل التدقيق والأمان
        </Title>
        <Text type="secondary">تتبع جميع عمليات الإدارة والأحداث الأمنية</Text>
      </div>

      {/* Stats */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="إجمالي العمليات"
              value={stats.total}
              prefix={<AuditOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="تنبيهات حرجة"
              value={stats.critical}
              prefix={<ExclamationCircleOutlined />}
              valueStyle={{ color: '#FF6B6B' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="اليوم"
              value={stats.todayCount}
              prefix={<ClockCircleOutlined />}
              valueStyle={{ color: '#E8B84A' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card>
            <Statistic
              title="مسؤولون نشطون"
              value={stats.uniqueAdmins}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Critical Alerts */}
      {alerts.length > 0 && (
        <Card
          title={
            <Space>
              <ExclamationCircleOutlined style={{ color: '#FF6B6B' }} />
              <Text strong>تنبيهات حرجة ({alerts.length})</Text>
            </Space>
          }
          style={{ borderColor: '#FF6B6B' }}
        >
          {alerts.slice(0, 5).map((a, idx) => {
            const meta = getActionMeta(a.action);
            return (
              <div key={idx} style={{ padding: 8, borderBottom: '1px solid #1A2F4A' }}>
                <Space>
                  <Tag color="red" icon={<ExclamationCircleOutlined />}>{a.severity}</Tag>
                  <Tag color={meta.color}>{meta.label}</Tag>
                  <Text>{a.adminUsername ?? 'system'}</Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {new Date(a.createdAt).toLocaleString('ar-EG')}
                  </Text>
                </Space>
                {a.description && <Paragraph style={{ marginTop: 4, marginBottom: 0, fontSize: 12 }}>{a.description}</Paragraph>}
              </div>
            );
          })}
        </Card>
      )}

      {/* Filters */}
      <Card>
        <Space wrap>
          <Select
            placeholder="التصنيف"
            allowClear
            style={{ width: 180 }}
            onChange={setCategoryFilter}
            value={categoryFilter}
            options={Object.entries(CATEGORY_COLORS).map(([v, c]) => ({ value: v, label: v }))}
          />
          <Search
            placeholder="بحث بـ Admin ID"
            allowClear
            onSearch={(v) => { setAdminFilter(v); load(); }}
            style={{ width: 300 }}
            prefix={<SearchOutlined />}
          />
          <Button icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Card>

      {/* Table */}
      <Card>
        {logs.length === 0 ? (
          <Empty description="لا توجد سجلات" />
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={logs}
            loading={loading}
            pagination={{
              current: page + 1,
              pageSize: size,
              total,
              onChange: (p) => setPage(p - 1),
              showSizeChanger: false,
            }}
            scroll={{ x: 1400 }}
            expandable={{
              expandedRowRender: (r) => (
                <div style={{ padding: 16, background: '#0A1628' }}>
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    {r.description && <div><Text type="secondary">الوصف:</Text> {r.description}</div>}
                    {r.metadata && (
                      <div>
                        <Text type="secondary"><CodeOutlined /> Metadata:</Text>
                        <pre style={{
                          background: '#050A16', padding: 8, borderRadius: 4,
                          fontSize: 11, color: '#00E6A0', marginTop: 4
                        }}>
                          {typeof r.metadata === 'string' ? r.metadata : JSON.stringify(r.metadata, null, 2)}
                        </pre>
                      </div>
                    )}
                    {r.userAgent && <div><Text type="secondary">User Agent:</Text> <Text code style={{ fontSize: 10 }}>{r.userAgent}</Text></div>}
                  </Space>
                </div>
              ),
            }}
          />
        )}
      </Card>
    </Space>
  );
}
