import React, { useEffect, useState } from 'react';
import * as echarts from 'echarts';
import './styles.css';

// ===== SERVER METRICS PANEL =====
interface ServerMetric {
  id: string;
  name: string;
  status: 'online' | 'offline' | 'warning';
  cpu: number;
  memory: number;
  uptime: number;
  message: string;
  connections?: number;
}

const ServerMetricsPanel: React.FC = () => {
  const [metrics, setMetrics] = useState<ServerMetric[]>([
    { id: 'web', name: 'Web Server', status: 'online', cpu: 23, memory: 45, uptime: 99.98, message: 'يعمل بشكل طبيعي', connections: 156 },
    { id: 'postgres', name: 'PostgreSQL', status: 'online', cpu: 18, memory: 38, uptime: 99.99, message: 'اتصالات: 124 نشطة' },
    { id: 'mongodb', name: 'MongoDB', status: 'online', cpu: 27, memory: 52, uptime: 99.97, message: 'عمليات: 45 ع/sec' },
    { id: 'redis', name: 'Redis Cache', status: 'online', cpu: 8, memory: 32, uptime: 99.999, message: 'ذاكرة: 68% مستخدمة' },
    { id: 'sfu', name: 'SFU Media', status: 'online', cpu: 41, memory: 67, uptime: 99.95, message: 'مؤتمرات نشطة: 12' },
    { id: 'minio', name: 'MinIO Storage', status: 'online', cpu: 12, memory: 28, uptime: 99.98, message: 'التخزين: 234 GB مستخدمة' },
  ]);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return '#00C98C';
      case 'offline': return '#FF6B6B';
      case 'warning': return '#FFB347';
      default: return '#8892B0';
    }
  };

  return (
    <div className="server-metrics-panel">
      <div className="panel-header">
        <h3>حالة الخوادم</h3>
        <div className="status-summary">
          <span className="status-badge online">6/6 نشطة</span>
        </div>
      </div>

      {metrics.map((metric) => (
        <div key={metric.id} className="metric-row">
          <div className="metric-icon" style={{ backgroundColor: `${getStatusColor(metric.status)}20`, color: getStatusColor(metric.status) }}>
            {metric.status === 'online' ? '🟢' : metric.status === 'offline' ? '🔴' : '🟡'}
          </div>
          <div className="metric-info">
            <span className="metric-name">{metric.name}</span>
            <span className="metric-message">{metric.message}</span>
          </div>
          <div className="metric-bars">
            <div className="metric-bar">
              <div className="metric-bar-fill cpu" style={{ width: `${metric.cpu}%` }} />
              <span className="metric-bar-label">CPU {metric.cpu}%</span>
            </div>
            <div className="metric-bar">
              <div className="metric-bar-fill memory" style={{ width: `${metric.memory}%` }} />
              <span className="metric-bar-label">ذاكرة {metric.memory}%</span>
            </div>
          </div>
          <span className={`status-badge ${metric.status}`}>
            {'online' === metric.status ? 'نشط' : 'غير نشط'}
          </span>
        </div>
      ))}
    </div>
  );
};

// ===== ACTIVITY FEED =====
interface ActivityItem {
  id: number;
  type: 'user' | 'device' | 'message' | 'call' | 'group';
  action: string;
  user: string;
  time: string;
  status: 'pending' | 'approved' | 'rejected' | 'active';
}

const ActivityFeed: React.FC = () => {
  const [activities, setActivities] = useState<ActivityItem[]>([
    { id: 1, type: 'user', action: 'إنشاء حساب جديد', user: 'أحمد محمد', time: 'منذ دقيقتين', status: 'pending' },
    { id: 2, type: 'device', action: 'جهاز جديد مسجل', user: 'فاطمة علي', time: 'قبل 5 دقائق', status: 'approved' },
    { id: 3, type: 'message', action: 'رسالة مرفوضة', user: 'خالد عبدالرحمن', time: 'قبل 12 دقيقة', status: 'rejected' },
    { id: 4, type: 'group', action: 'مجموعة جديدة', user: 'سارة أحمد', time: 'قبل 25 دقيقة', status: 'pending' },
    { id: 5, type: 'call', action: 'مكالمة PSTN', user: 'ياسر خالد', time: 'قبل ساعة', status: 'active' },
    { id: 6, type: 'user', action: 'تفعيل الحساب', user: 'محمد حسن', time: 'قبل ساعتين', status: 'approved' },
    { id: 7, type: 'device', action: 'إلغاء جهاز', user: 'عمر عبدالرحمن', time: 'قبل 3 ساعات', status: 'rejected' },
    { id: 8, type: 'message', action: 'رسالة تلقت', user: 'أحمد محمد', time: 'قبل 4 ساعات', status: 'active' },
  ]);

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'user': return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      );
      case 'device': return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="5" y="2" width="14" height="20" rx="2" />
          <line x1="12" y1="18" x2="12" y2="18.01" />
        </svg>
      );
      case 'message': return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      );
      case 'call': return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
        </svg>
      );
      default: return (
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      );
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'pending': return 'ينتظر';
      case 'approved': return 'تم';
      case 'rejected': return 'رفض';
      case 'active': return 'نشط';
      default: return status;
    }
  };

  return (
    <div className="activity-feed">
      <div className="panel-header">
        <h3>الأنشطة الأخيرة</h3>
        <button className="btn-ghost">عرض الكل</button>
      </div>
      <div className="activity-list">
        {activities.map((activity) => (
          <div key={activity.id} className={`activity-item ${activity.status}`}>
            <div className={`activity-icon ${activity.type}`}>
              {getTypeIcon(activity.type)}
            </div>
            <div className="activity-content">
              <p className="activity-text">
                <strong>{activity.user}</strong> {activity.action}
              </p>
              <span className="activity-time">{activity.time}</span>
            </div>
            <span className={`activity-status badge ${activity.status}`}>
              {getStatusLabel(activity.status)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

// ===== STATISTICS CARDS =====
interface StatCardProps {
  title: string;
  value: string | number;
  change?: string;
  changeType?: 'positive' | 'negative';
  icon: React.ReactNode;
  iconBg: string;
}

const StatCard: React.FC<StatCardProps> = ({ title, value, change, changeType = 'positive', icon, iconBg }) => (
  <div className="stat-card">
    <div className="stat-card-header">
      <div className={`stat-card-icon ${iconBg}`}>{icon}</div>
      {change && (
        <span className={`stat-card-change ${changeType}`}>
          {changeType === 'positive' ? '↑' : '↓'} {change}
        </span>
      )}
    </div>
    <div className="stat-card-value">{value}</div>
    <div className="stat-card-label">{title}</div>
  </div>
);

// ===== CHARTS =====
const UserGrowthChart: React.FC = () => {
  const chartRef = React.useRef<HTMLDivElement>(null);
  const [chart, setChart] = useState<echarts.ECharts | null>(null);

  useEffect(() => {
    if (chartRef.current) {
      const c = echarts.init(chartRef.current);
      c.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(17, 34, 64, 0.95)',
          borderColor: 'rgba(0, 201, 140, 0.3)',
          textStyle: { color: '#EDF7FB', fontFamily: 'Cairo, sans-serif' }
        },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['00:00', '03:00', '06:00', '09:00', '12:00', '15:00', '18:00', '21:00', '24:00'],
          axisLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.8)' } },
          axisLabel: { color: '#8892B0', fontSize: 11 }
        },
        yAxis: {
          type: 'value',
          name: 'المستخدمين',
          nameTextStyle: { color: '#8892B0', fontSize: 12 },
          axisLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.8)' } },
          axisLabel: { color: '#8892B0', fontSize: 11 },
          splitLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.3)', type: 'dashed' } }
        },
        series: [{
          name: 'المستخدمين',
          type: 'line',
          data: [2847, 2654, 2532, 3102, 3891, 4238, 4567, 3921, 3104],
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { color: '#00C98C', width: 3 },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(0, 201, 140, 0.4)' },
                { offset: 1, color: 'rgba(0, 201, 140, 0.05)' }
              ]
            }
          },
          itemStyle: { color: '#00C98C', borderColor: '#050A16', borderWidth: 2 }
        }]
      });
      setChart(c);

      const handleResize = () => c.resize();
      window.addEventListener('resize', handleResize);
      return () => {
        window.removeEventListener('resize', handleResize);
        c.dispose();
      };
    }
  }, []);

  return <div ref={chartRef} className="chart-container" />;
};

const UserActivityChart: React.FC = () => {
  const chartRef = React.useRef<HTMLDivElement>(null);
  const [chart, setChart] = useState<echarts.ECharts | null>(null);

  useEffect(() => {
    if (chartRef.current) {
      const c = echarts.init(chartRef.current);
      c.setOption({
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(17, 34, 64, 0.95)',
          borderColor: 'rgba(53, 203, 224, 0.3)',
          textStyle: { color: '#EDF7FB', fontFamily: 'Cairo, sans-serif' }
        },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['الساعة 1', 'الساعة 2', 'الساعة 3', 'الساعة 4', 'الساعة 5', 'الساعة 6', 'الساعة 7', 'الساعة 8'],
          axisLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.8)' } },
          axisLabel: { color: '#8892B0', fontSize: 11 }
        },
        yAxis: {
          type: 'value',
          name: 'نشطون',
          nameTextStyle: { color: '#8892B0', fontSize: 12 },
          axisLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.8)' } },
          axisLabel: { color: '#8892B0', fontSize: 11 },
          splitLine: { lineStyle: { color: 'rgba(30, 58, 95, 0.3)', type: 'dashed' } }
        },
        series: [{
          name: 'نشطون',
          type: 'bar',
          data: [142, 287, 395, 521, 438, 312, 189, 112],
          barWidth: '60%',
          itemStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: '#00C98C' },
                { offset: 1, color: '#35CBE0' }
              ]
            },
            borderRadius: [4, 4, 0, 0]
          }
        }]
      });
      setChart(c);

      const handleResize = () => c.resize();
      window.addEventListener('resize', handleResize);
      return () => {
        window.removeEventListener('resize', handleResize);
        c.dispose();
      };
    }
  }, []);

  return <div ref={chartRef} className="chart-container" />;
};

// ===== MAIN DASHBOARD =====
const Dashboard: React.FC = () => {
  const [timeRange, setTimeRange] = useState('24h');

  return (
    <div className="dashboard-page">
      {/* Welcome Section */}
      <div className="dashboard-welcome">
        <div className="welcome-content">
          <h2 className="welcome-title">مرحباً بك، مسؤول النظام</h2>
          <p className="welcome-subtitle">
            نظرة شاملة على البنية التحتية المحلية
            <span className="welcome-time">
              {new Date().toLocaleTimeString('ar-SA', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            </span>
          </p>
        </div>
        <div className="welcome-actions">
          <button className="btn-secondary">تصدير التقرير</button>
          <button className="btn-primary">تحديث فوري</button>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="stats-grid">
        <StatCard
          title="إجمالي المستخدمين"
          value={3847}
          change="+12.5%"
          changeType="positive"
          icon={<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /></svg>}
          iconBg="green"
        />
        <StatCard
          title="نشطين الآن"
          value={521}
          change="+8.2%"
          changeType="positive"
          icon={<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>}
          iconBg="blue"
        />
        <StatCard
          title="إجمالي الأجهزة"
          value={1243}
          change="+3.1%"
          changeType="positive"
          icon={<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="5" y="2" width="14" height="20" rx="2" /><line x1="12" y1="18" x2="12" y2="18.01" /></svg>}
          iconBg="gold"
        />
        <StatCard
          title="طلبات معلقة"
          value={5}
          change="-2.4%"
          changeType="negative"
          icon={<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>}
          iconBg="danger"
        />
      </div>

      {/* Charts Row */}
      <div className="dashboard-charts-row">
        <div className="admin-card chart-card">
          <div className="admin-card-header">
            <div>
              <h3 className="admin-card-title">نمو المستخدمين (24 ساعة)</h3>
              <p className="admin-card-subtitle">عدد المستخدمين النشطين عبر اليوم</p>
            </div>
            <div className="chart-legend">
              <span className="legend-item">
                <span className="legend-dot" style={{ background: '#00C98C' }} />
                المستخدمين
              </span>
            </div>
          </div>
          <UserGrowthChart />
        </div>

        <div className="admin-card chart-card">
          <div className="admin-card-header">
            <div>
              <h3 className="admin-card-title">النشاط حسب الوقت</h3>
              <p className="admin-card-subtitle">عدد المستخدمين المتصلين</p>
            </div>
          </div>
          <UserActivityChart />
        </div>
      </div>

      {/* Bottom Section */}
      <div className="dashboard-bottom-row">
        <ServerMetricsPanel />
        <ActivityFeed />
        <QuickActions />
      </div>
    </div>
  );
};

// ===== QUICK ACTIONS =====
const QuickActions: React.FC = () => {
  const actions = [
    { icon: 'users', label: 'إدارة المستخدمين' },
    { icon: 'check', label: 'التصديقات المعلقة', badge: 5 },
    { icon: 'devices', label: 'إدارة الأجهزة' },
    { icon: 'message', label: 'مركز الرسائل' },
    { icon: 'phone', label: 'مركز المكالمات' },
    { icon: 'shield', label: 'السلامة والأذونات' },
  ];

  const getIcon = (icon: string) => {
    switch (icon) {
      case 'users':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="8.5" cy="7" r="4" /><line x1="20" y1="8" x2="20" y2="14" /><line x1="23" y1="11" x2="17" y2="11" /></svg>;
      case 'check':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" /></svg>;
      case 'devices':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2" /><line x1="3" y1="9" x2="21" y2="9" /><line x1="9" y1="21" x2="9" y2="9" /></svg>;
      case 'message':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>;
      case 'phone':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" /></svg>;
      case 'shield':
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>;
      default:
        return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" /></svg>;
    }
  };

  return (
    <div className="admin-card quick-actions-card">
      <div className="admin-card-header">
        <h3 className="admin-card-title">إجراءات سريعة</h3>
        <p className="admin-card-subtitle">الأكثر استخداماً</p>
      </div>
      <div className="quick-actions-grid">
        {actions.map((action, index) => (
          <button key={index} className="quick-action-btn">
            <div className="quick-action-icon">
              {getIcon(action.icon)}
            </div>
            <span>{action.label}</span>
            {action.badge && (
              <span className="quick-action-badge">{action.badge}</span>
            )}
          </button>
        ))}
      </div>
    </div>
  );
};

export default Dashboard;
