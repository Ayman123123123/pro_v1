import React, { useState } from 'react';
import { LockOutlined, UserOutlined, LoadingOutlined } from '@ant-design/icons';

const Login = ({ onLogin, isLoading }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('يرجى إدخال اسم المستخدم وكلمة المرور');
      return;
    }
    onLogin(username.trim(), password);
  };

  return (
    <div className="login-shell">
      <div className="login-bg-orb login-bg-orb--1"></div>
      <div className="login-bg-orb login-bg-orb--2"></div>
      
      <div className="login-card">
        <div className="login-brand">
          <div className="login-emblem">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="currentColor" opacity="0.3"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <h1 className="login-title">يونس ماستر</h1>
          <p className="login-subtitle">لوحة تحكم المسؤول المحلي الآمنة</p>
        </div>

        <div className="login-card-content">
          <div className="login-security-badge">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" className="login-security-icon">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" fill="currentColor"/>
            </svg>
            <span>اتصال مشفر AES-256</span>
          </div>

          <form onSubmit={handleSubmit} className="login-form">
            <div className="form-group">
              <label htmlFor="username" className="form-label">اسم المستخدم</label>
              <div className="input-wrapper">
                <UserOutlined className="input-icon" />
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="اسم المستخدم"
                  autoComplete="username"
                  disabled={isLoading}
                  className="form-input"
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="password" className="form-label">كلمة المرور</label>
              <div className="input-wrapper">
                <LockOutlined className="input-icon" />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="كلمة المرور"
                  autoComplete="current-password"
                  disabled={isLoading}
                  className="form-input"
                />
                <button
                  type="button"
                  className="input-toggle-password"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                  disabled={isLoading}
                >
                  {showPassword ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  )}
                </button>
              </div>
            </div>

            {error && (
              <div className="login-error">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                </svg>
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={isLoading}
              className="login-btn"
            >
              {isLoading ? (
                <>
                  <LoadingOutlined spin className="login-btn-icon" />
                  <span>جارٍ التحقق...</span>
                </>
              ) : (
                <>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
                    <polyline points="10 17 15 12 10 7"/>
                    <line x1="15" y1="12" x2="3" y2="12"/>
                  </svg>
                  <span>دخول الآمن</span>
                </>
              )}
            </button>
          </form>

          <div className="login-footer">
            <div className="login-version">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                <circle cx="12" cy="12" r="10"/>
              </svg>
              <span>الإصدار 1.0.0 | بنية تحتية محلية</span>
            </div>
            <div className="login-patents">
              <span>حقوق الملكية الفكرية &copy; 2026 يونس سيستمز</span>
            </div>
          </div>
        </div>
      </div>

      <style>{`

        .login-shell {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(135deg, #050A16 0%, #0A1628 50%, #0D1B2A 100%);
          position: relative;
          overflow: hidden;
          padding: 20px;
        }

        .login-bg-orb {
          position: absolute;
          border-radius: 50%;
          filter: blur(80px);
          pointer-events: none;
        }

        .login-bg-orb--1 {
          width: 500px;
          height: 500px;
          background: radial-gradient(circle, rgba(0, 201, 140, 0.15) 0%, transparent 70%);
          top: -200px;
          right: -100px;
        }

        .login-bg-orb--2 {
          width: 400px;
          height: 400px;
          background: radial-gradient(circle, rgba(232, 184, 74, 0.12) 0%, transparent 70%);
          bottom: -150px;
          left: -100px;
        }

        .login-card {
          width: 100%;
          max-width: 420px;
          background: linear-gradient(145deg, rgba(17, 34, 64, 0.95) 0%, rgba(10, 22, 40, 0.98) 100%);
          border: 1px solid rgba(0, 201, 140, 0.3);
          border-radius: 20px;
          padding: 40px;
          position: relative;
          backdrop-filter: blur(20px);
          box-shadow: 
            0 25px 50px -12px rgba(0, 0, 0, 0.5),
            inset 0 1px 0 rgba(255, 255, 255, 0.05),
            0 0 60px rgba(0, 201, 140, 0.1);
        }

        .login-card::before {
          content: '';
          position: absolute;
          top: -1px;
          left: -1px;
          right: -1px;
          bottom: -1px;
          border-radius: 21px;
          background: linear-gradient(135deg, rgba(0, 201, 140, 0.3) 0%, transparent 50%, rgba(53, 203, 224, 0.2) 100%);
          z-index: -1;
        }

        .login-brand {
          text-align: center;
          margin-bottom: 32px;
        }

        .login-emblem {
          width: 64px;
          height: 64px;
          margin: 0 auto 16px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: linear-gradient(135deg, rgba(0, 201, 140, 0.2) 0%, rgba(53, 203, 224, 0.2) 100%);
          border: 2px solid rgba(0, 201, 140, 0.4);
          border-radius: 16px;
          color: var(--yns-green);
          box-shadow: 0 10px 40px rgba(0, 201, 140, 0.2);
          animation: emblem-pulse 3s ease-in-out infinite;
        }

        @keyframes emblem-pulse {
          0%, 100% { box-shadow: 0 10px 40px rgba(0, 201, 140, 0.2); }
          50% { box-shadow: 0 10px 60px rgba(0, 201, 140, 0.35); }
        }

        .login-title {
          font-family: 'Tajawal', 'Cairo', sans-serif;
          font-size: 1.75rem;
          font-weight: 800;
          color: var(--yns-text);
          margin-bottom: 8px;
          letter-spacing: 1px;
        }

        .login-subtitle {
          font-size: 0.875rem;
          color: var(--yns-text-secondary);
          margin-bottom: 24px;
        }

        .login-security-badge {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          padding: 8px 16px;
          background: rgba(0, 201, 140, 0.1);
          border: 1px solid rgba(0, 201, 140, 0.2);
          border-radius: 100px;
          color: var(--yns-green);
          font-size: 0.8125rem;
          font-weight: 600;
          margin-bottom: 28px;
        }

        .login-security-icon {
          color: var(--yns-green);
        }

        .login-form {
          display: flex;
          flex-direction: column;
          gap: 20px;
        }

        .input-wrapper {
          position: relative;
        }

        .input-icon {
          position: absolute;
          left: 14px;
          top: 50%;
          transform: translateY(-50%);
          color: var(--yns-text-muted);
          z-index: 1;
        }

        .input-wrapper .form-input {
          padding-left: 44px;
          background: rgba(10, 22, 40, 0.8);
          border: 1px solid rgba(30, 58, 95, 0.8);
          border-radius: 12px;
          padding: 14px 14px 14px 44px;
          font-size: 1rem;
          transition: all 0.2s ease;
        }

        .input-wrapper .form-input:focus {
          border-color: var(--yns-green);
          box-shadow: 0 0 0 3px rgba(0, 201, 140, 0.15), inset 0 0 20px rgba(0, 201, 140, 0.05);
          background: rgba(10, 22, 40, 1);
        }

        .input-toggle-password {
          position: absolute;
          right: 14px;
          top: 50%;
          transform: translateY(-50%);
          background: none;
          border: none;
          color: var(--yns-text-muted);
          cursor: pointer;
          padding: 4px;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: color 0.2s ease;
        }

        .input-toggle-password:hover {
          color: var(--yns-text-secondary);
        }

        .login-error {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 12px 16px;
          background: rgba(255, 107, 107, 0.1);
          border: 1px solid rgba(255, 107, 107, 0.3);
          border-radius: 12px;
          color: #FF6B6B;
          font-size: 0.875rem;
          animation: shake 0.5s ease-in-out;
        }

        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          25% { transform: translateX(-4px); }
          75% { transform: translateX(4px); }
        }

        .login-btn {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 10px;
          width: 100%;
          padding: 14px 24px;
          background: linear-gradient(135deg, var(--yns-green) 0%, #00A878 100%);
          color: var(--yns-dark);
          border: none;
          border-radius: 12px;
          font-family: 'Cairo', 'Tajawal', sans-serif;
          font-size: 1rem;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.3s ease;
          box-shadow: 0 4px 15px rgba(0, 201, 140, 0.3);
          margin-top: 8px;
        }

        .login-btn:hover:not(:disabled) {
          background: linear-gradient(135deg, #00E6A0 0%, var(--yns-green) 100%);
          transform: translateY(-2px);
          box-shadow: 0 8px 25px rgba(0, 201, 140, 0.4);
        }

        .login-btn:active:not(:disabled) {
          transform: translateY(0);
        }

        .login-btn:disabled {
          opacity: 0.7;
          cursor: not-allowed;
        }

        .login-btn-icon {
          color: var(--yns-dark);
        }

        .login-footer {
          margin-top: 28px;
          padding-top: 20px;
          border-top: 1px solid rgba(30, 58, 95, 0.5);
          display: flex;
          justify-content: space-between;
          align-items: center;
          font-size: 0.75rem;
          color: var(--yns-text-muted);
        }

        .login-version {
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .login-patents {
          text-align: right;
        }

        @media (max-width: 480px) {
          .login-card {
            padding: 28px 24px;
          }

          .login-title {
            font-size: 1.5rem;
          }

          .login-footer {
            flex-direction: column;
            gap: 8px;
            text-align: center;
          }

          .login-patents {
            text-align: center;
          }
        }
      `}</style>
    </div>
  );
};

export default Login;
