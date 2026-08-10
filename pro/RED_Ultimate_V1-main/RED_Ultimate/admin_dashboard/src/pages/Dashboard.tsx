import { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Spin, Alert, Progress, Tag, List, Typography, Space, Tooltip } from 'antd';
import {
  TeamOutlined,
  AlertOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  CloudServerOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  ThunderboltOutlined,
  DollarOutlined,
  MessageOutlined,
  PhoneOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import {
  getDashboardSummary,
  getSystemAnalytics,
  getSystemHealth,
  getRealtimeMetrics,
  type DashboardSummary,
  type SystemHealth,
  type RealtimeMetrics,
} from '../api';

const { Title, Text } = Typography;

interface AnalyticsRow {
  statDate: string;
  totalUsers: number;
  newUsers: number;
  activeUsersDau: number;
  messagesSent: number;
  voiceMessages: number;
  callsTotal: number;
  callsPstn: number;
  dinstarBalanceRemaining: number;
  storageUsedBytes: number;
}

export default function Dashboard() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [analytics, setAnalytics] = useState<AnalyticsRow[]>([]);
  const [health, setHealth] = useState<SystemHealth[]>([]);
  const [realtime, setRealtime] = useState<RealtimeMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    const fetchAll = async () => {
      try {
        const [sum, healthData, rt] = await Promise.all([
          getDashboardSummary(),
          getSystemHealth(),
          getRealtimeMetrics(),
        ]);
        if (!mounted) return;
        setSummary(sum);
        setHealth(healthData);
        setRealtime(rt);

        // Last 7 days
        const end = new Date();
        const start = new Date();
        start.setDate(start.getDate() - 6);
        const ana = await getSystemAnalytics(
          start.toISOString().slice(0, 10),
          end.toISOString().slice(0, 10)
        );
        if (mounted) setAnalytics(ana);
      } catch (e: any) {
        if (mounted) setError(e.message ?? 'تعذر تحميل لوحة الإدارة');
      } finally {
        if (mounted) setLoading(false);
      }
    };
    fetchAll();
    // Auto-refresh every 30s
    const interval = setInterval(fetchAll, 30000);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
        <Spin size="large" tip="جاري تحميل لوحة الإدارة..." />
      </div>
    );
  }

  if (error) {
    return <Alert type="error" message="خطأ في التحميل" description={error} showIcon />;
  }

  const formatBytes = (bytes: number): string => {
    if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(2)} GB`;
    if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(2)} MB`;
    if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(2)} KB`;
    return `${bytes} B`;
  };

  const userChart = {
    title: { text: 'المستخدمون النشطون', textStyle: { color: '#00E6A0' } },
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: analytics.map(a => a.statDate.slice(5)) },
    yAxis: { type: 'value' },
    series: [
      {
        name: 'إجمالي',
        type: 'line',
        data: analytics.map(a => a.totalUsers),
        smooth: true,
        itemStyle: { color: '#00E6A0' },
        areaStyle: { color: 'rgba(0,230,160,0.2)' },
      },
      {
        name: 'جدد',
        type: 'line',
        data: analytics.map(a => a.newUsers),
        smooth: true,
        itemStyle: { color: '#35CBE0' },
      },
    ],
  };

  const messageChart = {
    title: { text: 'الرسائل والمكالمات (آخر 7 أيام)', textStyle: { color: '#00E6A0' } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['رسائل', 'مكالمات', 'رسائل صوتية'], textStyle: { color: '#fff' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: analytics.map(a => a.statDate.slice(5)) },
    yAxis: { type: 'value' },
    series: [
      { name: 'رسائل', type: 'bar', data: analytics.map(a => a.messagesSent), itemStyle: { color: '#00E6A0' } },
      { name: 'مكالمات', type: 'bar', data: analytics.map(a => a.callsTotal), itemStyle: { color: '#E8B84A' } },
      { name: 'رسائل صوتية', type: 'line', data: analytics.map(a => a.voiceMessages), smooth: true, itemStyle: { color: '#35CBE0' } },
    ],
  };

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Header */}
      <div>
        <Title level={2} style={{ color: '#00E6A0', margin: 0 }}>
          <ThunderboltOutlined /> لوحة الإدارة السيادية
        </Title>
        <Text type="secondary">
          <ClockCircleOutlined /> آخر تحديث: {new Date().toLocaleString('ar-EG')}
          {realtime && <Tag color="green" style={{ marginRight: 12 }}>مباشر</Tag>}
        </Text>
      </div>

      {/* Critical Alerts */}
      {summary && (summary.recentCriticalAlerts > 0 || summary.degradedComponents > 0) && (
        <Alert
          type="warning"
          message="تنبيهات تحتاج انتباه"
          description={
            <Space>
              {summary.recentCriticalAlerts > 0 && (
                <Tag color="red">{summary.recentCriticalAlerts} تنبيهات حرجة</Tag>
              )}
              {summary.degradedComponents > 0 && (
                <Tag color="orange">{summary.degradedComponents} مكونات متدهورة</Tag>
              )}
              {summary.activeBackups > 0 && (
                <Tag color="blue">{summary.activeBackups} نسخ قيد التنفيذ</Tag>
              )}
            </Space>
          }
          showIcon
        />
      )}

      {/* User Stats */}
      {summary && (
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="إجمالي المستخدمين"
                value={summary.analytics.totalUsers ?? 0}
                prefix={<TeamOutlined style={{ color: '#00E6A0' }} />}
                valueStyle={{ color: '#00E6A0' }}
              />
              <Text type="secondary">+{summary.analytics.newUsers24h ?? 0} آخر 24 ساعة</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="في انتظار الموافقة"
                value={summary.analytics.pendingUsers ?? 0}
                prefix={<UserAddOutlined style={{ color: '#E8B84A' }} />}
                valueStyle={{ color: '#E8B84A' }}
              />
              {summary.analytics.approvalRate != null && (
                <Progress
                  percent={Number(summary.analytics.approvalRate.toFixed(1))}
                  size="small"
                  showInfo={false}
                  strokeColor="#E8B84A"
                />
              )}
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="البلاغات المعلقة"
                value={Number(summary.pendingReports)}
                prefix={<AlertOutlined style={{ color: '#FF6B6B' }} />}
                valueStyle={{ color: '#FF6B6B' }}
              />
              <Text type="secondary">تحتاج مراجعة</Text>
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="المستخدمون المحظورون"
                value={summary.analytics.bannedUsers ?? 0}
                prefix={<WarningOutlined style={{ color: '#FF6B6B' }} />}
                valueStyle={{ color: '#FF6B6B' }}
              />
            </Card>
          </Col>
        </Row>
      )}

      {/* Charts */}
      {analytics.length > 0 && (
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card title={<><TeamOutlined /> المستخدمون</>}>
              <ReactECharts option={userChart} style={{ height: 280 }} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title={<><MessageOutlined /> النشاط</>}>
              <ReactECharts option={messageChart} style={{ height: 280 }} />
            </Card>
          </Col>
        </Row>
      )}

      {/* System Health */}
      <Card title={<><CloudServerOutlined /> صحة النظام (آخر 5 دقائق)</>}>
        {health.length === 0 ? (
          <Alert type="info" message="لا توجد بيانات صحة حديثة" />
        ) : (
          <Row gutter={[16, 16]}>
            {health.map((h, idx) => {
              const status = h.status as string;
              const color = status === 'HEALTHY' ? 'green' : status === 'DEGRADED' ? 'orange' : 'red';
              const icon = status === 'HEALTHY' ? <CheckCircleOutlined /> :
                           status === 'DEGRADED' ? <WarningOutlined /> : <AlertOutlined />;
              return (
                <Col xs={24} sm={12} md={8} key={idx}>
                  <Card size="small">
                    <Space>
                      <Tag color={color} icon={icon}>{h.component}</Tag>
                      {h.cpuUsage != null && (
                        <Tooltip title="CPU">
                          <Tag><DatabaseOutlined /> {h.cpuUsage.toFixed(0)}%</Tag>
                        </Tooltip>
                      )}
                      {h.memoryUsage != null && (
                        <Tooltip title="Memory">
                          <Tag color="cyan">{h.memoryUsage.toFixed(0)}%</Tag>
                        </Tooltip>
                      )}
                    </Space>
                  </Card>
                </Col>
              );
            })}
          </Row>
        )}
      </Card>

      {/* Storage */}
      {analytics.length > 0 && (
        <Card title={<><DatabaseOutlined /> التخزين والـ DINSTAR</>}>
          <Row gutter={[16, 16]}>
            <Col xs={24} md={12}>
              <Statistic
                title="التخزين المستخدم"
                value={formatBytes(analytics[analytics.length - 1]?.storageUsedBytes ?? 0)}
                prefix={<DatabaseOutlined style={{ color: '#35CBE0' }} />}
                valueStyle={{ color: '#35CBE0' }}
              />
            </Col>
            <Col xs={24} md={12}>
              <Statistic
                title="رصيد DINSTAR المتبقي"
                value={analytics[analytics.length - 1]?.dinstarBalanceRemaining ?? 0}
                prefix={<DollarOutlined style={{ color: '#E8B84A' }} />}
                valueStyle={{ color: '#E8B84A' }}
                suffix="ريال"
              />
            </Col>
          </Row>
        </Card>
      )}

      {/* Recent Activity */}
      {analytics.length > 0 && (
        <Card title="آخر النشاط اليومي">
          <List
            dataSource={analytics.slice(-5).reverse()}
            renderItem={(item) => (
              <List.Item>
                <Space>
                  <Tag color="cyan">{item.statDate}</Tag>
                  <Text>{item.messagesSent} رسالة</Text>
                  <Text>{item.callsTotal} مكالمة</Text>
                  <Text type="secondary">+{item.newUsers} مستخدم جديد</Text>
                </Space>
              </List.Item>
            )}
          />
        </Card>
      )}
    </Space>
  );
}
