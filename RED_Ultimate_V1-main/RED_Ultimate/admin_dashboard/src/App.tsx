import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Avatar, Badge, Button, ConfigProvider, Divider, Dropdown, Layout, Menu, Segmented, Space, Spin, Tag, Tooltip } from 'antd';
import { buildYounesTheme } from './theme';
import {
  DashboardOutlined,
  GlobalOutlined,
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
  FileTextOutlined,
  KeyOutlined,
  VideoCameraOutlined,
  MessageOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  BulbOutlined,
  MoonOutlined,
  LaptopOutlined,
  DownOutlined,
  UserOutlined,
  PhoneOutlined,
  HeartOutlined,
} from '@ant-design/icons';
import { adminLogin, adminLogout, authStore, apiFetch, getPendingApprovals, probeBackend } from './api';
import Login from './pages/Login';
import ErrorBoundary from './components/ErrorBoundary';
import { usePolling } from './hooks/usePolling';
import './styles.css';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const MasterOverview = lazy(() => import('./pages/MasterOverview'));
const CallsMonitor = lazy(() => import('./pages/CallsMonitor'));
const SystemHealth = lazy(() => import('./pages/SystemHealth'));
const UserManagement = lazy(() => import('./pages/UserManagement'));
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
const GroupsManagement = lazy(() => import('./pages/GroupsManagement'));
const PostsManagement = lazy(() => import('./pages/PostsManagement'));
const CallHistory = lazy(() => import('./pages/CallHistory'));
const BrowserSettings = lazy(() => import('./pages/BrowserSettings'));
const SysConfig = lazy(() => import('./pages/system/ConfigPage').then(m => ({ default: m.ConfigPage })));
const SysCache = lazy(() => import('./pages/system/CachePage').then(m => ({ default: m.CachePage })));
const SysBackups = lazy(() => import('./pages/system/BackupsPage').then(m => ({ default: m.BackupsPage })));
const SysAudit = lazy(() => import('./pages/system/AuditLogPage').then(m => ({ default: m.AuditLogPage })));
const SysMigrations = lazy(() => import('./pages/system/MigrationsPage').then(m => ({ default: m.MigrationsPage })));
const SecSSO = lazy(() => import('./pages/security/SSOPage').then(m => ({ default: m.SSOPage })));
const SecMFA = lazy(() => import('./pages/security/MFAPage').then(m => ({ default: m.MFAPage })));
const SecRoles = lazy(() => import('./pages/security/RolesPage').then(m => ({ default: m.RolesPage })));
const SecSessions = lazy(() => import('./pages/security/SessionsPage').then(m => ({ default: m.SessionsPage })));
const SecApiKeys = lazy(() => import('./pages/security/ApiKeysPage').then(m => ({ default: m.ApiKeysPage })));
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage').then(m => ({ default: m.SettingsPage })));
const AuthorityTab = lazy(() => import('./pages/tabs/AuthorityTab'));
const SecurityTab = lazy(() => import('./pages/tabs/SecurityTab'));

const { Header, Sider, Content } = Layout;

type PageKey =
  | 'dashboard'
  | 'users'
  | 'approvals'
  | 'authority'
  | 'content'
  | 'reports'
  | 'audit'
  | 'sys-audit'
  | 'moderation'
  | 'messaging'
  | 'announcements'
  | 'featureflags'
  | 'backups'
  | 'sys-backups'
  | 'sys-config'
  | 'sys-cache'
  | 'sys-migrations'
  | 'security'
  | 'security-ops'
  | 'sec-sso'
  | 'sec-mfa'
  | 'sec-roles'
  | 'sec-sessions'
  | 'sec-apikeys'
  | 'settings'
  | 'notifications'
  | 'logs'
  | 'media'
  | 'infrastructure'
  | 'calls'
  | 'calls-monitor'
  | 'system-health'
  | 'monitor'
  | 'diagnostics'
  | 'data-overview'
  | 'groups'
  | 'posts'
  | 'browser-settings';

const menuItems: { key: PageKey; icon: React.JSX.Element; label: string; group: string }[] = [
  // Operations — مدموجة من القديمة + الجديدة — بيانات حقيقية — كل التبويبات القديمة بالشكل الجديد
  { key: 'dashboard', icon: <DashboardOutlined />, label: 'الرئيسية', group: 'main' },
  { key: 'data-overview', icon: <DatabaseOutlined />, label: 'جرد بيانات المنصة', group: 'main' },
  { key: 'users', icon: <TeamOutlined />, label: 'المستخدمون', group: 'main' },
  { key: 'approvals', icon: <SafetyCertificateOutlined />, label: 'الموافقات المعلقة', group: 'main' },
  { key: 'authority', icon: <SafetyCertificateOutlined />, label: 'سلطة الاعتماد', group: 'main' },
  { key: 'content', icon: <BarChartOutlined />, label: 'المحتوى', group: 'main' },
  { key: 'reports', icon: <AlertOutlined />, label: 'مراقبة المحتوى', group: 'main' },
  { key: 'audit', icon: <AuditOutlined />, label: 'سجل التدقيق', group: 'main' },
  { key: 'moderation', icon: <SafetyCertificateOutlined />, label: 'الإشراف السريع', group: 'main' },
  { key: 'messaging', icon: <MessageOutlined />, label: 'مركز الرسائل', group: 'main' },
  { key: 'groups', icon: <TeamOutlined />, label: 'إدارة المجموعات', group: 'main' },
  { key: 'posts', icon: <FileTextOutlined />, label: 'المنشورات والتغريدات', group: 'main' },
  // System — مدموجة: الإعلانات + أعلام + نسخ + أمان + إشعارات + سجلات + وسائط + بنية
  { key: 'announcements', icon: <NotificationOutlined />, label: 'الإعلانات', group: 'system' },
  { key: 'featureflags', icon: <ExperimentOutlined />, label: 'أعلام الميزات', group: 'system' },
  { key: 'backups', icon: <CloudUploadOutlined />, label: 'النسخ الاحتياطية', group: 'system' },
  { key: 'sys-backups', icon: <CloudUploadOutlined />, label: 'النسخ المتقدم', group: 'system' },
  { key: 'sys-config', icon: <SettingOutlined />, label: 'إعدادات النظام', group: 'system' },
  { key: 'sys-cache', icon: <DatabaseOutlined />, label: 'الكاش', group: 'system' },
  { key: 'sys-audit', icon: <AuditOutlined />, label: 'التدقيق المفصّل', group: 'system' },
  { key: 'sys-migrations', icon: <CloudServerOutlined />, label: 'الترحيلات', group: 'system' },
  { key: 'security', icon: <SafetyOutlined />, label: 'مركز الأمان', group: 'system' },
  { key: 'security-ops', icon: <SafetyOutlined />, label: 'عمليات الأمان', group: 'system' },
  { key: 'sec-sso', icon: <KeyOutlined />, label: 'الدخول الموحد SSO', group: 'system' },
  { key: 'sec-mfa', icon: <SafetyCertificateOutlined />, label: 'المصادقة MFA', group: 'system' },
  { key: 'sec-roles', icon: <TeamOutlined />, label: 'الأدوار', group: 'system' },
  { key: 'sec-sessions', icon: <MonitorOutlined />, label: 'الجلسات', group: 'system' },
  { key: 'sec-apikeys', icon: <KeyOutlined />, label: 'مفاتيح API', group: 'system' },
  { key: 'settings', icon: <SettingOutlined />, label: 'الإعدادات', group: 'system' },
  { key: 'notifications', icon: <BellOutlined />, label: 'الإشعارات', group: 'system' },
  { key: 'logs', icon: <FileSearchOutlined />, label: 'سجل النظام الحي', group: 'system' },
  { key: 'media', icon: <VideoCameraOutlined />, label: 'مركز الوسائط', group: 'system' },
  { key: 'infrastructure', icon: <CloudServerOutlined />, label: 'البنية التحتية', group: 'system' },
  { key: 'calls', icon: <BarChartOutlined />, label: 'سجل المكالمات', group: 'sovereign' },
  { key: 'calls-monitor', icon: <PhoneOutlined />, label: 'مراقبة المكالمات', group: 'sovereign' },
  { key: 'monitor', icon: <MonitorOutlined />, label: 'المراقبة الحية', group: 'sovereign' },
  { key: 'system-health', icon: <HeartOutlined />, label: 'صحة النظام', group: 'system' },
  { key: 'diagnostics', icon: <SettingOutlined />, label: 'التشخيص', group: 'sovereign' },
  { key: 'browser-settings', icon: <GlobalOutlined />, label: 'إعدادات المتصفح', group: 'sovereign' },
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
type ThemeMode = 'light' | 'dark' | 'system';
function getInitialTheme(): ThemeMode {
  const saved = localStorage.getItem('yns-theme') as ThemeMode | null;
  if (saved === 'light' || saved === 'dark' || saved === 'system') return saved;
  return 'light';
}
function resolveTheme(mode: ThemeMode): 'light' | 'dark' {
  if (mode !== 'system') return mode;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(() => authStore.isAuthenticated());
  const [currentPage, setCurrentPage] = useState<PageKey>('dashboard');
  const [loginLoading, setLoginLoading] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [apiUp, setApiUp] = useState(true);
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => getInitialTheme());
  const resolvedTheme = themeMode === 'system' ? resolveTheme('system') : themeMode;
  const adminUser = authStore.user();
  // عداد فشل متتالٍ لفحص صحة الخادم: لا نقلب «متصل» إلى «غير متصل» من طلة
  // عابرة واحدة — فوسيط المعاينة أو انقطاع شبكة مؤقت قد يفشل دورة واحدة فقط.
  const healthFailsRef = useRef(0);

  // تطبيق الثيم على <html data-theme> + persist
  useEffect(() => {
    const t = resolveTheme(themeMode);
    document.documentElement.dataset.theme = t;
    localStorage.setItem('yns-theme', themeMode);
  }, [themeMode]);

  // تتبع تفضيل النظام عند وضع "system"
  useEffect(() => {
    if (themeMode !== 'system') return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => { document.documentElement.dataset.theme = mq.matches ? 'dark' : 'light'; };
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [themeMode]);

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
      case 'authority': return <AuthorityTab />;
      case 'content': return <ContentManagement />;
      case 'reports': return <Reports />;
      case 'audit': return <AuditLog />;
      case 'sys-audit': return <SysAudit />;
      case 'moderation': return <ModerationCenter />;
      case 'messaging': return <MessagingCenter />;
      case 'groups': return <GroupsManagement />;
      case 'posts': return <PostsManagement />;
      case 'announcements': return <Announcements />;
      case 'featureflags': return <FeatureFlags />;
      case 'backups': return <Backups />;
      case 'sys-backups': return <SysBackups />;
      case 'sys-config': return <SysConfig />;
      case 'sys-cache': return <SysCache />;
      case 'sys-migrations': return <SysMigrations />;
      case 'security': return <SecurityCenter />;
      case 'security-ops': return <SecurityTab />;
      case 'sec-sso': return <SecSSO />;
      case 'sec-mfa': return <SecMFA />;
      case 'sec-roles': return <SecRoles />;
      case 'sec-sessions': return <SecSessions />;
      case 'sec-apikeys': return <SecApiKeys />;
      case 'settings': return <SettingsPage />;
      case 'notifications': return <NotificationsCenter />;
      case 'logs': return <SystemLogs />;
      case 'media': return <MediaCenter />;
      case 'infrastructure': return <InfrastructureCenter />;
      case 'calls': return <CallHistory />;
      case 'calls-monitor': return <CallsMonitor />;
      case 'system-health': return <SystemHealth />;
      case 'monitor': return <MasterOverview />;
      case 'diagnostics': return <Diagnostics />;
      case 'browser-settings': return <BrowserSettings />;
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
        ? <span>{m.label} <Badge count={pendingCount} size="small" color="#E0A83C" offset={[10, 0]} /></span>
        : m.key === 'notifications'
        ? <span>{m.label} <Badge dot color="red" offset={[10, 0]} /></span>
        : m.label,
    })),
  }));

  const activeMenu = menuItems.find(m => m.key === currentPage);

  return (
    <ConfigProvider
      direction="rtl"
      theme={buildYounesTheme(resolvedTheme)}
    >
      <Layout style={{ minHeight: '100vh', background: resolvedTheme === 'dark' ? '#06110D' : '#F7F8FA' }}>
        <Sider 
          theme="dark" 
          collapsible 
          breakpoint="lg"
          collapsedWidth={80}
          width={260} 
          className="yns-sider"
          style={{
            position: 'sticky',
            top: 0,
            height: '100vh',
            overflow: 'auto',
            boxShadow: '2px 0 8px rgba(0,0,0,0.15)'
          }}
        >
          <div className="yns-brand" style={{ padding: '20px 16px' }}>
            <span className="admin-brand-icon admin-brand-icon--image" style={{ width: 40, height: 40 }}>
              <img src="/admin-master-icon.svg" alt="شعار يونس" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
            </span>
            <span className="yns-brand-text" style={{ display: 'flex', flexDirection: 'column' }}>
              <strong style={{ fontSize: '18px', lineHeight: '1.2' }}>يونس ماستر</strong>
              <small style={{ color: '#D4B16A', fontSize: '12px' }}>الإدارة السيادية</small>
            </span>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[currentPage]}
            items={groupedMenu}
            onClick={({ key }: any) => setCurrentPage(key as PageKey)}
            style={{ borderRight: 0, background: 'transparent' }}
            className="yns-menu"
          />
          <div className="yns-sider-footer" style={{ marginTop: 'auto', padding: '16px', borderTop: '1px solid rgba(255,255,255,0.1)' }}>
            <span className={apiUp ? 'yns-dot up' : 'yns-dot down'} />
            <span style={{ marginLeft: 8, color: 'rgba(255,255,255,0.65)' }}>{apiUp ? 'النظام يعمل' : 'النظام متوقف'}</span>
          </div>
        </Sider>
        <Layout style={{ transition: 'all 0.2s' }}>
          <Header className="yns-header" style={{ padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: resolvedTheme === 'dark' ? '#141C24' : '#fff', boxShadow: '0 1px 4px rgba(0,0,0,0.05)', position: 'sticky', top: 0, zIndex: 10, height: 64 }}>
            <div className="yns-header-titles" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ color: resolvedTheme === 'dark' ? '#9AAEBB' : '#5A6B7D', fontSize: 14 }}>
                {activeMenu ? groupLabels[activeMenu.group] : 'العمليات'}
              </div>
              <span style={{ color: '#D4B16A' }}>/</span>
              <div style={{ fontWeight: 600, fontSize: 18, color: resolvedTheme === 'dark' ? '#fff' : '#0F1B2D' }}>
                {activeMenu?.label || 'يونس'}
              </div>
            </div>
            <Space size={16} align="center" style={{ flexWrap: 'nowrap' }}>
              {/* مجموعة التحكم — ترويسة مرتبة بأزرار حبوب فاخرة */}
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                padding: 3, borderRadius: 9999,
                background: resolvedTheme === 'dark' ? '#1E2936' : '#F0F2F5',
                border: `1px solid ${resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB'}`,
              }}>
                <Tooltip title="فاتح">
                  <Button
                    size="small" shape="circle"
                    type={themeMode === 'light' ? 'primary' : 'text'}
                    icon={<BulbOutlined />}
                    onClick={() => setThemeMode('light')}
                    style={themeMode === 'light' ? { background: '#B78A2E', borderColor: '#B78A2E', color: '#fff' } : {}}
                  />
                </Tooltip>
                <Tooltip title="ليلي">
                  <Button
                    size="small" shape="circle"
                    type={themeMode === 'dark' ? 'primary' : 'text'}
                    icon={<MoonOutlined />}
                    onClick={() => setThemeMode('dark')}
                    style={themeMode === 'dark' ? { background: '#0F1B2D', borderColor: '#0F1B2D', color: '#D4B16A' } : {}}
                  />
                </Tooltip>
                <Tooltip title="تابع للنظام">
                  <Button
                    size="small" shape="circle"
                    type={themeMode === 'system' ? 'primary' : 'text'}
                    icon={<LaptopOutlined />}
                    onClick={() => setThemeMode('system')}
                    style={themeMode === 'system' ? { background: '#B78A2E', borderColor: '#B78A2E', color: '#fff' } : {}}
                  />
                </Tooltip>
              </div>

              <Divider type="vertical" style={{ height: 24, margin: 0, borderColor: resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB' }} />

              <span className={apiUp ? 'yns-status up' : 'yns-status down'} style={{ height: 28, borderRadius: 9999, padding: '0 12px', fontSize: 13, background: apiUp ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', border: `1px solid ${apiUp ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)'}` }}>
                <span className={apiUp ? 'yns-dot up' : 'yns-dot down'} />
                {apiUp ? 'متصل' : 'غير متصل'}
              </span>

              {pendingCount > 0 && (
                <Badge count={pendingCount} color="#B78A2E" size="small" style={{ fontWeight: 700 }}>
                  <Button size="small" shape="round" onClick={() => setCurrentPage('approvals')} style={{ fontWeight: 600, borderColor: '#B78A2E', color: '#B78A2E' }}>
                    موافقات
                  </Button>
                </Badge>
              )}

              <Tooltip title="الإشعارات">
                <Badge dot color="red">
                  <Button shape="circle" type="text" icon={<BellOutlined style={{ fontSize: 18 }} />} onClick={() => setCurrentPage('notifications')} />
                </Badge>
              </Tooltip>

              <Divider type="vertical" style={{ height: 24, margin: 0, borderColor: resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB' }} />
              
              {adminUser?.username && (
                <Dropdown
                  trigger={['click']}
                  placement="bottomLeft"
                  dropdownRender={() => (
                    <div style={{
                      width: 300,
                      background: resolvedTheme === 'dark' ? '#141C24' : '#fff',
                      border: `1px solid ${resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB'}`,
                      borderRadius: 16,
                      overflow: 'hidden',
                      boxShadow: resolvedTheme === 'dark' ? '0 12px 40px rgba(0,0,0,0.5)' : '0 12px 40px rgba(15,27,45,0.15)',
                    }}>
                      {/* رأس المستخدم — تدرج ذهبي/كحلي فاخر */}
                      <div style={{
                        padding: '24px 20px 20px',
                        background: 'linear-gradient(135deg, #0F1B2D 0%, #1E2936 50%, #2C3A4A 100%)',
                        color: '#fff',
                        position: 'relative',
                        overflow: 'hidden',
                      }}>
                        <div style={{
                          position: 'absolute', top: -20, left: -20, width: 100, height: 100,
                          background: 'radial-gradient(circle, rgba(183,138,46,0.25) 0%, transparent 70%)',
                          borderRadius: '50%',
                        }} />
                        <Space size={16} align="center">
                          <Avatar size={56} style={{
                            background: 'linear-gradient(135deg, #B78A2E 0%, #D4B16A 100%)',
                            color: '#0A0F14', fontWeight: 800, fontSize: 24, flexShrink: 0,
                            border: '3px solid rgba(255,255,255,0.2)',
                          }}>
                            {(adminUser.displayName || adminUser.username || '؟').trim().charAt(0).toUpperCase()}
                          </Avatar>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ fontWeight: 800, fontSize: 16, lineHeight: 1.2, color: '#fff', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                              {adminUser.displayName || adminUser.username}
                            </div>
                            <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.7)', display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
                              <Tag color="gold" style={{ margin: 0, fontSize: 11, lineHeight: '18px', border: 'none', color: '#0A0F14', fontWeight: 700 }}>
                                {(adminUser as any).role || 'ADMIN'}
                              </Tag>
                              <span style={{ fontFamily: 'monospace' }}>#{ (adminUser as any).redId || '—'}</span>
                            </div>
                          </div>
                        </Space>
                      </div>
                      {/* إحصائيات سريعة */}
                      <div style={{
                        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12,
                        padding: 16, background: resolvedTheme === 'dark' ? 'rgba(255,255,255,0.02)' : '#F7F8FA',
                        borderBottom: `1px solid ${resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB'}`,
                      }}>
                        <div style={{ textAlign: 'center', padding: '12px 8px', background: resolvedTheme === 'dark' ? '#1E2936' : '#fff', borderRadius: 12, border: `1px solid ${resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB'}`, transition: 'all 0.2s', cursor: 'pointer' }} onClick={() => setCurrentPage('approvals')}>
                          <div style={{ fontSize: 20, fontWeight: 800, color: '#B78A2E' }}>{pendingCount}</div>
                          <div style={{ fontSize: 12, color: resolvedTheme === 'dark' ? '#9AAEBB' : '#5A6B7D', marginTop: 4 }}>موافقات معلقة</div>
                        </div>
                        <div style={{ textAlign: 'center', padding: '12px 8px', background: resolvedTheme === 'dark' ? '#1E2936' : '#fff', borderRadius: 12, border: `1px solid ${resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB'}` }}>
                          <div style={{ fontSize: 20, fontWeight: 800, color: apiUp ? '#10b981' : '#B91C1C' }}>{apiUp ? 'متصل' : 'مقطوع'}</div>
                          <div style={{ fontSize: 12, color: resolvedTheme === 'dark' ? '#9AAEBB' : '#5A6B7D', marginTop: 4 }}>حالة الخادم</div>
                        </div>
                      </div>
                      {/* إجراءات */}
                      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <Button type="text" block icon={<UserOutlined />} style={{ textAlign: 'right', justifyContent: 'flex-start', height: 40, borderRadius: 8 }}
                          onClick={() => setCurrentPage('users')}>
                          إدارة المستخدمين
                        </Button>
                        <Button type="text" block icon={<SettingOutlined />} style={{ textAlign: 'right', justifyContent: 'flex-start', height: 40, borderRadius: 8 }}
                          onClick={() => setCurrentPage('browser-settings')}>
                          إعدادات المتصفح
                        </Button>
                        <div style={{ height: 1, background: resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB', margin: '8px 0' }} />
                        <Button danger block icon={<LogoutOutlined />} onClick={logout} style={{ height: 40, borderRadius: 8, fontWeight: 600 }}>
                          تسجيل الخروج
                        </Button>
                      </div>
                    </div>
                  )}
                >
                  <Button
                    shape="round"
                    style={{
                      height: 40, padding: '0 12px 0 8px', gap: 10,
                      background: resolvedTheme === 'dark' ? '#1E2936' : '#fff',
                      borderColor: resolvedTheme === 'dark' ? '#2C3A4A' : '#E6E8EB', color: resolvedTheme === 'dark' ? '#F1F5F9' : '#0F1B2D',
                      fontWeight: 600, display: 'inline-flex', alignItems: 'center',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.02)'
                    }}
                  >
                    <Avatar size={28} style={{
                      background: 'linear-gradient(135deg, #B78A2E 0%, #D4B16A 100%)',
                      color: '#0A0F14', fontWeight: 800, fontSize: 14, flexShrink: 0,
                    }}>
                      {(adminUser.displayName || adminUser.username || '؟').trim().charAt(0).toUpperCase()}
                    </Avatar>
                    <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {adminUser.username}
                    </span>
                    <DownOutlined style={{ fontSize: 10, opacity: 0.6 }} />
                  </Button>
                </Dropdown>
              )}
            </Space>
          </Header>
          <Content className="yns-content" style={{ padding: '24px', margin: 0, minHeight: 280 }}>
            {/* حد أخطاء لكل صفحة: عطل في قسم واحد لا يُسقط اللوحة كلها */}
            <ErrorBoundary resetKey={currentPage}>
              <Suspense fallback={
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: 400, gap: 16 }}>
                  <Spin size="large" />
                  <div style={{ color: resolvedTheme === 'dark' ? '#9AAEBB' : '#5A6B7D' }}>جاري التحميل...</div>
                </div>
              }>
                <div className="yns-page" key={currentPage} style={{ animation: 'fadeIn 0.3s ease-in-out' }}>
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
