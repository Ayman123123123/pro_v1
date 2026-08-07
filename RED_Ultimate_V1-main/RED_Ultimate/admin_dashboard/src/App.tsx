import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import './styles.css';

// ===== ADMIN USER DATA =====
const adminUser = {
  name: 'أحمد علي',
  role: 'مسؤول رئيسي',
  avatar: 'AY',
  permissions: ['users.read', 'users.write', 'devices.read', 'devices.write', 'groups.read', 'groups.write', 'messages.read', 'calls.read', 'approvals.read', 'settings.read']
};

// ===== MASTER LAYOUT =====
const MasterLayout: React.FC<{ children: React.ReactNode; userName: string }> = ({ children, userName }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeNav, setActiveNav] = useState('dashboard');
  const [notifications, setNotifications] = useState([
    { id: 1, type: 'approval', message: 'طلب新規 3', time: 'منذ 5د', read: false },
    { id: 2, type: 'call', message: 'مكالمةfail 1', time: 'منذ 12د', read: false },
    { id: 3, type: 'message', message: 'رسالة جديدة 8', time: 'منذ ساعة', read: true },
  ]);
  const [showUserMenu, setShowUserMenu] = useState(false);

  const navItems = [
    { id: 'dashboard', label: 'الرئيسية', icon: 'dashboard', badge: null },
    { id: 'users', label: 'المستخدمين', icon: 'users', badge: 3 },
    { id: 'devices', label: 'الأجهزة', icon: 'devices', badge: 12 },
    { id: 'groups', label: 'المجموعات', icon: 'groups', badge: null },
    { id: 'messages', label: 'مركز الرسائل', icon: 'messages', badge: null },
    { id: 'calls', label: 'المكالمات', icon: 'calls', badge: 2 },
    { id: 'approvals', label: 'التصديقات', icon: 'approvals', badge: 5 },
    { id: 'settings', label: 'الإعدادات', icon: 'settings', badge: null },
  ];

  const unreadCount = notifications.filter(n => !n.read).length;

  const handleLogout = () => {
    localStorage.removeItem('yns_admin_token');
    window.location.href = '/login';
  };

  return (
    <div className="admin-shell">
      {/* Sidebar */}
      <aside className={`admin-sider ${sidebarOpen ? 'open' : ''}`}>
        <div className="admin-sider-header">
          <div className="admin-brand-logo">
            <div className="admin-brand-icon">
              <span>ي</span>
            </div>
            <div className="admin-brand-text">
              <span className="admin-brand-name">يونس ماستر</span>
              <span className="admin-brand-subtitle">لوحة الإدارة المحلية</span>
            </div>
          </div>
        </div>

        <nav className="admin-sider-nav">
          <div className="nav-section-title">الرئيسية</div>
          {navItems.map(item => (
            <div
              key={item.id}
              className={`nav-item ${activeNav === item.id ? 'active' : ''}`}
              onClick={() => { setActiveNav(item.id); setSidebarOpen(false); }}
            >
              <span className="nav-item-icon">
                {getNavIcon(item.icon)}
              </span>
              <span>{item.label}</span>
              {item.badge && (
                <span className={`nav-badge ${getBadgeType(item.id)}`}>
                  {item.badge}
                </span>
              )}
            </div>
          ))}
        </nav>

        <div className="admin-sider-footer">
          <button className="nav-item logout-item" onClick={handleLogout}>
            <span className="nav-item-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
            </span>
            <span>تسجيل الخروج</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="admin-main">
        <header className="admin-header">
          <div className="admin-header-left">
            <button className="sidebar-toggle" onClick={() => setSidebarOpen(!sidebarOpen)}>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="3" y1="12" x2="21" y2="12" />
                <line x1="3" y1="6" x2="21" y2="6" />
                <line x1="3" y1="18" x2="21" y2="18" />
              </svg>
            </button>
            <h1 className="admin-header-title">
              {navItems.find(n => n.id === activeNav)?.label || 'لوحة التحكم'}
            </h1>
          </div>

          <div className="admin-header-right">
            <button className="header-icon-btn" title="البحث">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8" />
                <line x1="21" y1="21" x2="16.65" y2="16.65" />
              </svg>
            </button>

            <button className="header-icon-btn" title="الإشعارات">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
              {unreadCount > 0 && (
                <span className="notification-badge">{unreadCount}</span>
              )}
            </button>

            <div className="admin-user-wrapper">
              <button className="admin-user-menu" onClick={() => setShowUserMenu(!showUserMenu)}>
                <div className="admin-user-avatar">
                  <span>{adminUser.avatar}</span>
                </div>
                <div className="admin-user-info">
                  <span className="admin-user-name">{userName || adminUser.name}</span>
                  <span className="admin-user-role">{adminUser.role}</span>
                </div>
              </button>

              {showUserMenu && (
                <div className="admin-user-dropdown">
                  <div className="dropdown-header">
                    <span>{adminUser.name}</span>
                    <span className="dropdown-role">{adminUser.role}</span>
                  </div>
                  <div className="dropdown-divider" />
                  <button className="dropdown-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                    الملف الشخصي
                  </button>
                  <button className="dropdown-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="12" cy="12" r="3" />
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
                    </svg>
                    الإعدادات
                  </button>
                  <div className="dropdown-divider" />
                  <button className="dropdown-item logout-dropdown" onClick={handleLogout}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                      <polyline points="16 17 21 12 16 7" />
                      <line x1="21" y1="12" x2="9" y2="12" />
                    </svg>
                    تسجيل الخروج
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <div className="admin-content">
          {children}
        </div>

        <footer className="admin-footer">
          <div className="footer-content">
            <span>© 2026 يونس سيستمز - 판권 محفوظة</span>
            <span className="footer-version">v1.0.0</span>
          </div>
        </footer>
      </main>

      <style>{`
        .admin-sider-footer {
          padding: 16px;
          border-top: 1px solid var(--yns-border);
        }

        .logout-item {
          width: 100%;
          justify-content: flex-start;
          color: var(--yns-error);
        }

        .logout-item:hover {
          background: rgba(255, 107, 107, 0.1);
          border-radius: var(--radius-md);
        }

        .admin-sider-header {
          padding: 16px;
          border-bottom: 1px solid var(--yns-border);
          background: linear-gradient(180deg, var(--yns-surface) 0%, var(--yns-navy) 100%);
        }

        .admin-brand-logo {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .admin-brand-icon {
          width: 44px;
          height: 44px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(135deg, var(--yns-green) 0%, var(--yns-blue) 100%);
          border-radius: var(--radius-md);
          color: var(--yns-dark);
          font-size: 1.5rem;
          font-weight: 900;
          box-shadow: 0 4px 15px rgba(0, 201, 140, 0.3);
        }

        .admin-brand-text {
          display: flex;
          flex-direction: column;
        }

        .admin-brand-name {
          font-family: 'Tajawal', 'Cairo', sans-serif;
          font-size: 1.125rem;
          font-weight: 800;
          color: var(--yns-gold);
          letter-spacing: 0.5px;
        }

        .admin-brand-subtitle {
          font-size: 0.75rem;
          color: var(--yns-text-muted);
        }

        .sidebar-toggle {
          background: none;
          border: none;
          color: var(--yns-text-secondary);
          padding: 8px;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .sidebar-toggle:hover {
          background: var(--yns-surface);
          color: var(--yns-text);
        }

        .header-icon-btn {
          position: relative;
          background: var(--yns-surface);
          border: 1px solid var(--yns-border);
          color: var(--yns-text-secondary);
          width: 40px;
          height: 40px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .header-icon-btn:hover {
          border-color: var(--yns-text-muted);
          color: var(--yns-text);
        }

        .notification-badge {
          position: absolute;
          top: -4px;
          right: -4px;
          background: var(--yns-error);
          color: white;
          font-size: 0.6875rem;
          font-weight: 700;
          min-width: 18px;
          height: 18px;
          border-radius: 9px;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0 4px;
          animation: pulse 2s ease infinite;
        }

        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.7; }
        }

        .admin-user-wrapper {
          position: relative;
        }

        .admin-user-menu {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 6px 12px 6px 6px;
          background: var(--yns-surface);
          border: 1px solid var(--yns-border);
          border-radius: 12px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .admin-user-menu:hover {
          border-color: var(--yns-text-muted);
        }

        .admin-user-avatar {
          width: 36px;
          height: 36px;
          border-radius: 10px;
          background: linear-gradient(135deg, var(--yns-gold) 0%, #D4A545 100%);
          color: var(--yns-dark);
          display: flex;
          align-items: center;
          justify-content: center;
          font-family: 'Tajawal', 'Cairo', sans-serif;
          font-weight: 700;
          font-size: 0.875rem;
        }

        .admin-user-info {
          display: flex;
          flex-direction: column;
        }

        .admin-user-name {
          font-weight: 600;
          font-size: 0.9375rem;
          color: var(--yns-text);
        }

        .admin-user-role {
          font-size: 0.75rem;
          color: var(--yns-text-muted);
        }

        .admin-user-dropdown {
          position: absolute;
          top: calc(100% + 8px);
          right: 0;
          width: 240px;
          background: var(--yns-surface);
          border: 1px solid var(--yns-border);
          border-radius: var(--radius-lg);
          box-shadow: var(--shadow-lg);
          z-index: 100;
          overflow: hidden;
        }

        .dropdown-header {
          padding: 16px;
          background: var(--yns-navy);
          display: flex;
          flex-direction: column;
        }

        .dropdown-header span:first-child {
          font-weight: 600;
          color: var(--yns-text);
        }

        .dropdown-role {
          font-size: 0.75rem;
          color: var(--yns-text-muted);
        }

        .dropdown-divider {
          height: 1px;
          background: var(--yns-border);
        }

        .dropdown-item {
          display: flex;
          align-items: center;
          gap: 12px;
          width: 100%;
          padding: 12px 16px;
          background: none;
          border: none;
          color: var(--yns-text-secondary);
          font-size: 0.9375rem;
          cursor: pointer;
          transition: all 0.2s ease;
          text-align: left;
        }

        .dropdown-item:hover {
          background: var(--yns-surface);
          color: var(--yns-text);
        }

        .logout-dropdown {
          color: var(--yns-error);
        }

        .logout-dropdown:hover {
          background: rgba(255, 107, 107, 0.1);
        }

        .admin-footer {
          height: 48px;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0 24px;
          background: var(--yns-navy);
          border-top: 1px solid var(--yns-border);
        }

        .footer-content {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 16px;
          font-size: 0.8125rem;
          color: var(--yns-text-muted);
        }

        .footer-version {
          padding: 2px 8px;
          background: var(--yns-surface);
          border-radius: 4px;
          font-family: monospace;
          font-size: 0.75rem;
        }

        @media (max-width: 768px) {
          .admin-user-info {
            display: none;
          }

          .admin-footer {
            padding: 0 16px;
          }
        }
      `}</style>
    </div>
  );
};

// Icon helper
const getNavIcon = (icon: string) => {
  switch (icon) {
    case 'dashboard':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="3" y="3" width="7" height="9" rx="1" />
          <rect x="14" y="3" width="7" height="5" rx="1" />
          <rect x="14" y="12" width="7" height="9" rx="1" />
          <rect x="3" y="16" width="7" height="5" rx="1" />
        </svg>
      );
    case 'users':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      );
    case 'devices':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="5" y="2" width="14" height="20" rx="2" />
          <line x1="12" y1="18" x2="12" y2="18.01" />
        </svg>
      );
    case 'groups':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      );
    case 'messages':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      );
    case 'calls':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
        </svg>
      );
    case 'approvals':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
          <polyline points="22 4 12 14.01 9 11.01" />
        </svg>
      );
    case 'settings':
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
      );
    default:
      return (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="16" />
          <line x1="8" y1="12" x2="16" y2="12" />
        </svg>
      );
  }
};

const getBadgeType = (navId: string) => {
  switch (navId) {
    case 'approvals':
      return 'warning';
    case 'calls':
      return 'info';
    default:
      return 'default';
  }
};

// ===== APP =====
const App: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [userName] = useState(adminUser.name);
  const navigate = useNavigate();

  useEffect(() => {
    const token = localStorage.getItem('yns_admin_token');
    if (token) {
      setIsAuthenticated(true);
    }
    setIsLoading(false);
  }, []);

  const handleLogin = async (username: string, password: string) => {
    setIsLoading(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1500));
      const mockToken = 'yns_admin_' + Date.now();
      localStorage.setItem('yns_admin_token', mockToken);
      setIsAuthenticated(true);
      navigate('/');
    } catch (error) {
      console.error('Login failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('yns_admin_token');
    setIsAuthenticated(false);
    navigate('/login');
  };

  if (isLoading) {
    return (
      <div className="app-loading">
        <div className="loading-spinner" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Login onLogin={handleLogin} isLoading={isLoading} />;
  }

  return (
    <MasterLayout userName={userName}>
      <Dashboard />
    </MasterLayout>
  );
};

export default App;
