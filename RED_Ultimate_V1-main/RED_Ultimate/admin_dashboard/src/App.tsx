import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Badge, Button, ConfigProvider, Layout, Menu, Space, Spin, theme } from 'antd';
import {
  DashboardOutlined,
  MobileOutlined,
  MonitorOutlined,
  SafetyOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  AlertOutlined,
  AuditOutlined,
  CloudUploadOutlined,
  ExperimentOutlined,
  FlagOutlined,
  NotificationOutlined,
  BarChartOutlined,
  BellOutlined,
  FileSearchOutlined,
  KeyOutlined,
  VideoCameraOutlined,
  MessageOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { adminLogin, adminLogout, authStore, apiFetch, getPendingApprovals, probeBackend } from './api';
import Login from './pages/Login';
import ErrorBoundary from './components/ErrorBoundary';
import { usePolling } from './hooks/usePolling';
import './styles.css';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const MasterOverview = lazy(() => import('./pages/MasterOverview'));
const UserManagement = lazy(() => import('./pages/UserManagement'));
const DinstarControl = lazy(() => import('./pages/DinstarControl'));
const Diagnostics = lazy(() => import('./pages/Diagnostics'));
const Reports = lazy(() => import('./pages/Reports'));
const AuditLog = lazy(() => import('./pages/AuditLog'));
const Backups = lazy(() => import('./pages/Backups'));
const Announcements = lazy(() => import('./pages/Announcements'));
const FeatureFlags = lazy(() => import('./pages/FeatureFlags'));
const ContentManagement = lazy(() => import('./pages/ContentManagement'));
const Approvals = lazy(() => import('./pages/Approvals'));
const SecurityCenter = lazy(() => import('./pages/SecurityCenter'));
const NotificationsCenter = lazy(() => import('./pages/NotificationsCenter'));
const SystemLogs = lazy(() => import('./pages/SystemLogs'));
const MediaCenter = lazy(() => import('./pages/MediaCenter'));
const MessagingCenter = lazy(() => import('./pages/MessagingCenter'));
const InfrastructureCenter = lazy(() => import('./pages/InfrastructureCenter'));
const ModerationCenter = lazy(() => import('./pages/ModerationCenter'));
const DataOverview = lazy(() => import('./pages/DataOverview'));
const SimInventory = lazy(() => import('./pages/SimInventory'));
const CdrAnalysis = lazy(() => import('./pages/CdrAnalysis'));
const SmsTemplates = lazy(() => import('./pages/SmsTemplates'));
const PortControl = lazy(() => import('./pages/PortControl'));

const { Header, Sider, Content } = Layout;

type PageKey =
  | 'dashboard'
  | 'users'
  | 'approvals'
  | 'content'
  | 'reports'
  | 'audit'
  | 'moderation'
  | 'messaging'
  | 'announcements'
  | 'featureflags'
  | 'backups'
  | 'security'
  | 'notifications'
  | 'logs'
  | 'media'
  | 'infrastructure'
  | 'dinstar'
  | 'dinstar-sim'
  | 'dinstar-cdr'
  | 'dinstar-sms-templates'
  | 'dinstar-port-control'
  | 'monitor'
  | 'diagnostics'
  | 'data-overview';

const menuItems: { key: PageKey; icon: React.JSX.Element; label: string; group: string }[] = [
  // Operations — مدموجة من القديمة + الجديدة — بيانات حقيقية — كل التبويبات القديمة بالشكل الجديد
  { key: 'dashboard', icon: <DashboardOutlined />, label: 'الرئيسية', group: 'main' },
  { key: 'data-overview', icon: <DatabaseOutlined />, label: 'جرد بيانات المنصة', group: 'main' },
  { key: 'users', icon: <TeamOutlined />, label: 'المستخدمون', group: 'main' },
  { key: 'approvals', icon: <SafetyCertificateOutlined />, label: 'الموافقات المعلقة', group: 'main' },
  { key: 'content', icon: <BarChartOutlined />, label: 'المحتوى', group: 'main' },
  { key: 'reports', icon: <AlertOutlined />, label: 'مراقبة المحتوى', group: 'main' },
  { key: 'audit', icon: <AuditOutlined />, label: 'سجل التدقيق', group: 'main' },
  { key: 'moderation', icon: <SafetyCertificateOutlined />, label: 'الإشراف السريع', group: 'main' },
  { key: 'messaging', icon: <MessageOutlined />, label: 'مركز الرسائل', group: 'main' },
  // System — مدموجة: الإعلانات + أعلام + نسخ + أمان + إشعارات + سجلات + وسائط + بنية
  { key: 'announcements', icon: <NotificationOutlined />, label: 'الإعلانات', group: 'system' },
  { key: 'featureflags', icon: <ExperimentOutlined />, label: 'أعلام الميزات', group: 'system' },
  { key: 'backups', icon: <CloudUploadOutlined />, label: 'النسخ الاحتياطية', group: 'system' },
  { key: 'security', icon: <SafetyOutlined />, label: 'مركز الأمان', group: 'system' },
  { key: 'notifications', icon: <BellOutlined />, label: 'الإشعارات', group: 'system' },
  { key: 'logs', icon: <FileSearchOutlined />, label: 'سجل النظام الحي', group: 'system' },
  { key: 'media', icon: <VideoCameraOutlined />, label: 'مركز الوسائط', group: 'system' },
  { key: 'infrastructure', icon: <CloudServerOutlined />, label: 'البنية التحتية', group: 'system' },
  // Sovereign — DINSTAR + مراقبة + تشخيص
  { key: 'dinstar', icon: <MobileOutlined />, label: 'بوابات DINSTAR', group: 'sovereign' },
  { key: 'dinstar-sim', icon: <SafetyCertificateOutlined />, label: 'جرد شرائح SIM', group: 'sovereign' },
  { key: 'dinstar-cdr', icon: <BarChartOutlined />, label: 'تحليل المكالمات CDR', group: 'sovereign' },
  { key: 'dinstar-sms-templates', icon: <MessageOutlined />, label: 'قوالب SMS', group: 'sovereign' },
  { key: 'dinstar-port-control', icon: <SettingOutlined />, label: 'التحكم بالمنافذ', group: 'sovereign' },
  { key: 'monitor', icon: <MonitorOutlined />, label: 'المراقبة الحية', group: 'sovereign' },
  { key: 'diagnostics', icon: <SettingOutlined />, label: 'التشخيص', group: 'sovereign' },
];

const groupLabels: Record<string, string> = {
  main: 'العمليات',
  system: 'النظام',
  sovereign: 'السيادي',
};

/**
 * Real administrative shell. Authentication is delegated to /api/auth/login via adminLogin(),
 * and every feature page remains reachable after the TypeScript UI migration.
 */
export default function App() {
  const [authenticated, setAuthenticated] = useState(() => authStore.isAuthenticated());
  const [currentPage, setCurrentPage] = useState<PageKey>('dashboard');
  const [loginLoading, setLoginLoading] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [apiUp, setApiUp] = useState(true);
  const adminUser = authStore.user();
  // عداد فشل متتالٍ لفحص صحة الخادم: لا نقلب «متصل» إلى «غير متصل» من طلة
  // عابرة واحدة — فوسيط المعاينة أو انقطاع شبكة مؤقت قد يفشل دورة واحدة فقط.
  const healthFailsRef = useRef(0);

  useEffect(() => {
    const onExpired = () => setAuthenticated(false);
    window.addEventListener('younes:auth-expired', onExpired);
    return () => window.removeEventListener('younes:auth-expired', onExpired);
  }, []);

  const refreshShell = useCallback(async () => {
    if (!authenticated) return;
    try {
      const [pending, health] = await Promise.all([
        getPendingApprovals().catch(() => []),
        probeBackend(3000),
      ]);
      setPendingCount(Array.isArray(pending) ? pending.length : 0);
      if (health.state === 'READY' || health.state === 'LIVE') {
        healthFailsRef.current = 0;
        setApiUp(true);
      } else {
        healthFailsRef.current += 1;
        if (healthFailsRef.current >= 2) setApiUp(false);
      }
    } catch {
      healthFailsRef.current += 1;
      if (healthFailsRef.current >= 2) setApiUp(false);
    }
  }, [authenticated]);

  usePolling(refreshShell, authenticated ? 20000 : null);

  /**
   * 🛡️ تحقق فوري من صحة الجلسة عند فتح اللوحة.
   *
   * جلسة قديمة عالقة (مثلًا بعد إعادة تشغيل الخادم أو إعادة بناء قاعدة
   * البيانات) تُبقي `authStore` ممتلئًا برمز وصول لم يعد صالحًا، فتعرض
   * الصفحات أخطاء «انتهت الجلسة» بدل توجيه المسؤول للدخول من جديد.
   * نداء خفيف هنا يتحقق من الصلاحية فورًا؛ إن فشل التجديد يمسح `apiFetch`
   * الجلسة ويُطلق حدث `younes:auth-expired` فيعود المسؤول لشاشة الدخول.
   */
  useEffect(() => {
    if (!authenticated) return;
    let cancelled = false;
    void apiFetch('/api/admin/operations/overview').then((res) => {
      // 500/503 تعني عطل خادم لا جلسة منتهية — لا تُخرج المسؤول.
      if (!cancelled && res.status === 401) authStore.clear();
    });
    return () => { cancelled = true; };
  }, [authenticated]);

  const login = async (username: string, password: string) => {
    setLoginLoading(true);
    try {
      await adminLogin(username, password);
      setAuthenticated(true);
    } finally {
      setLoginLoading(false);
    }
  };

  const logout = async () => {
    await adminLogout();
    setAuthenticated(false);
    setCurrentPage('dashboard');
  };

  if (!authenticated) {
    return <Login onLogin={login} isLoading={loginLoading} />;
  }

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard': return <Dashboard />;
      case 'data-overview': return <DataOverview />;
      case 'users': return <UserManagement />;
      case 'approvals': return <Approvals />;
      case 'content': return <ContentManagement />;
      case 'reports': return <Reports />;
      case 'audit': return <AuditLog />;
      case 'moderation': return <ModerationCenter />;
      case 'messaging': return <MessagingCenter />;
      case 'announcements': return <Announcements />;
      case 'featureflags': return <FeatureFlags />;
      case 'backups': return <Backups />;
      case 'security': return <SecurityCenter />;
      case 'notifications': return <NotificationsCenter />;
      case 'logs': return <SystemLogs />;
      case 'media': return <MediaCenter />;
      case 'infrastructure': return <InfrastructureCenter />;
      case 'dinstar': return <DinstarControl />;
      case 'dinstar-sim': return <SimInventory />;
      case 'dinstar-cdr': return <CdrAnalysis />;
      case 'dinstar-sms-templates': return <SmsTemplates />;
      case 'dinstar-port-control': return <PortControl />;
      case 'monitor': return <MasterOverview />;
      case 'diagnostics': return <Diagnostics />;
    }
  };

  // Group menu items
  const groupedMenu = ['main', 'system', 'sovereign'].map(group => ({
    key: group,
    type: 'group' as const,
    label: groupLabels[group],
    children: menuItems.filter(m => m.group === group).map(m => ({
      key: m.key,
      icon: m.icon,
      label: m.key === 'approvals' && pendingCount > 0
        ? <span>{m.label} <Badge count={pendingCount} size="small" color="#E8B84A" /></span>
        : m.label,
    })),
  }));

  return (
    <ConfigProvider
      direction="rtl"
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#00C896',
          colorInfo: '#35CBE0',
          colorWarning: '#E8B84A',
          colorBgBase: '#050A16',
          borderRadius: 14,
          fontFamily: "'Cairo', 'Tajawal', 'Segoe UI', Tahoma, Arial, sans-serif",
        },
        components: {
          Menu: {
            darkItemBg: 'transparent',
            darkSubMenuItemBg: 'transparent',
            darkItemColor: '#8892B0',
            darkItemHoverBg: 'rgba(0, 201, 140, 0.08)',
            darkItemHoverColor: '#EDF7FB',
            darkItemSelectedBg: 'linear-gradient(135deg, rgba(0,201,140,0.22) 0%, rgba(53,203,224,0.12) 100%)' as unknown as string,
            darkItemSelectedColor: '#00E6A0',
            itemBorderRadius: 10,
            itemMarginInline: 10,
          },
          Layout: {
            siderBg: '#0A1628',
            headerBg: 'rgba(8, 21, 37, 0.85)',
            headerHeight: 64,
          },
        },
      }}
    >
      <Layout style={{ minHeight: '100vh', background: '#050A16' }}>
        <Sider theme="dark" collapsible width={248} className="yns-sider">
          <div className="yns-brand">
            <span className="admin-brand-icon admin-brand-icon--image">
              <img src="/admin-master-icon.svg" alt="شعار يونس" />
            </span>
            <span className="yns-brand-text">
              <strong>يونس ماستر</strong>
              <small>الإدارة السيادية</small>
            </span>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[currentPage]}
            items={groupedMenu}
            onClick={({ key }) => setCurrentPage(key as PageKey)}
            style={{ borderRight: 0, background: 'transparent' }}
            className="yns-menu"
          />
          <div className="yns-sider-footer">
            <span className={apiUp ? 'yns-dot up' : 'yns-dot down'} />
            {apiUp ? 'النظام يعمل' : 'النظام متوقف'}
          </div>
        </Sider>
        <Layout>
          <Header className="yns-header">
            <div className="yns-header-titles">
              <span className="yns-header-group">
                {groupLabels[menuItems.find(m => m.key === currentPage)?.group || 'main']}
              </span>
              <span className="yns-header-page">
                {menuItems.find(m => m.key === currentPage)?.label || 'يونس'}
              </span>
            </div>
            <Space size={12}>
              <span className={apiUp ? 'yns-status up' : 'yns-status down'}>
                <span className={apiUp ? 'yns-dot up' : 'yns-dot down'} />
                {apiUp ? 'الخادم متصل' : 'الخادم غير متصل'}
              </span>
              {pendingCount > 0 && (
                <Badge count={pendingCount} color="#E8B84A">
                  <Button size="small" onClick={() => setCurrentPage('approvals')}>موافقات</Button>
                </Badge>
              )}
              {adminUser?.username && (
                <span className="yns-user-chip">
                  <span className="yns-user-avatar">
                    {(adminUser.displayName || adminUser.username || '؟').trim().charAt(0)}
                  </span>
                  {adminUser.username}
                </span>
              )}
              <Button danger icon={<LogoutOutlined />} onClick={logout}>تسجيل الخروج</Button>
            </Space>
          </Header>
          <Content className="yns-content">
            {/* حد أخطاء لكل صفحة: عطل في قسم واحد لا يُسقط اللوحة كلها */}
            <ErrorBoundary resetKey={currentPage}>
              <Suspense fallback={
                <div style={{ display: 'grid', placeItems: 'center', minHeight: 320 }}>
                  <Spin size="large" tip="جاري التحميل..." />
                </div>
              }>
                <div className="yns-page" key={currentPage}>
                  {renderPage()}
                </div>
              </Suspense>
            </ErrorBoundary>
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}
