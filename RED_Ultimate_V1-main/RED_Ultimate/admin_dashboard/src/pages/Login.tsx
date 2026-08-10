import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography, Space, Tag } from 'antd';
import { LockOutlined, UserOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { adminLogin } from '../api';

interface LoginProps {
  onLogin?: (username: string, password: string) => Promise<void>;
  onSuccess?: () => void;
  isLoading?: boolean;
}
export default function Login({ onLogin, onSuccess, isLoading }: LoginProps) {
  const [error, setError] = useState('');
  const [internalLoading, setInternalLoading] = useState(false);
  const loading = isLoading ?? internalLoading;
  const submit = async (values: { username: string; password: string }) => {
    if (onLogin) {
      try { setError(''); await onLogin(values.username, values.password); }
      catch (e: any) { setError(e.message || 'تعذر تسجيل الدخول'); }
      return;
    }
    setInternalLoading(true); setError('');
    try { await adminLogin(values.username, values.password); onSuccess?.(); }
    catch (e: any) { setError(e.message || 'تعذر تسجيل الدخول'); }
    finally { setInternalLoading(false); }
  };
  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: '#020617', direction: 'rtl' }}>
      <div style={{ flex: 1, background: 'linear-gradient(135deg, #020617 0%, #0F172A 50%, #1E293B 100%)', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '48px 56px', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: -80, right: -80, width: 300, height: 300, background: 'radial-gradient(circle, rgba(0,201,140,0.12) 0%, transparent 70%)', borderRadius: '50%' }} />
        <div style={{ position: 'absolute', bottom: -60, left: -60, width: 240, height: 240, background: 'radial-gradient(circle, rgba(232,184,74,0.08) 0%, transparent 70%)', borderRadius: '50%' }} />
        <div style={{ position: 'relative', zIndex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 32 }}>
            <div style={{ width: 48, height: 48, background: 'linear-gradient(135deg, #00C896 0%, #35CBE0 100%)', borderRadius: 12, display: 'grid', placeItems: 'center', color: '#020617', fontWeight: 900, fontSize: 22, boxShadow: '0 8px 24px rgba(0,201,140,0.3)' }}>◆</div>
            <div>
              <div style={{ color: '#00C896', fontWeight: 800, fontSize: 22, letterSpacing: 1 }}>YOUNES</div>
              <div style={{ color: '#64748B', fontSize: 11, letterSpacing: 2, marginTop: -4 }}>SOVEREIGN PLATFORM</div>
            </div>
          </div>
          <Typography.Title level={1} style={{ color: '#F1F5F9', fontSize: 36, lineHeight: 1.2, marginBottom: 16, fontWeight: 800 }}>
            المنصة السيادية<br />
            <span style={{ background: 'linear-gradient(90deg, #00C896, #E8B84A)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' as any }}>لإدارة يونس</span>
          </Typography.Title>
          <Typography.Paragraph style={{ color: '#94A3B8', fontSize: 16, lineHeight: 1.8, maxWidth: 460 }}>
            وصول سيادي محلي — هوية RED مستقلة — تشفير طرفي — 20 لوحة تحكم موحدة — لوحة واحدة تحكم كل شيء: مستخدمون، محتوى، DINSTAR، أمان، وسائط.
          </Typography.Paragraph>
          <Space direction="vertical" size={12} style={{ marginTop: 28, width: '100%' }}>
            {[
              { icon: '🛡️', title: 'سيادي محلي', desc: 'بدون هاتف أو SIM — RED ID' },
              { icon: '🔐', title: 'مشفر طرفياً', desc: 'مفاتيح لا تغادر الجهاز' },
              { icon: '📡', title: 'DINSTAR 8G', desc: '8 شرائح — Yemen Mobile/Sabafon/YOU' },
            ].map(item => (
              <div key={item.title} style={{ display: 'flex', gap: 12, alignItems: 'center', background: 'rgba(15,23,42,0.6)', border: '1px solid rgba(30,41,59,0.8)', borderRadius: 12, padding: '12px 16px' }}>
                <div style={{ fontSize: 22 }}>{item.icon}</div>
                <div><div style={{ color: '#E2E8F0', fontWeight: 600, fontSize: 13 }}>{item.title}</div><div style={{ color: '#64748B', fontSize: 11 }}>{item.desc}</div></div>
              </div>
            ))}
          </Space>
          <div style={{ marginTop: 32, display: 'flex', gap: 8 }}>
            <Tag color="green" style={{ borderRadius: 20, padding: '2px 10px' }}>● LOCAL MODE</Tag>
            <Tag color="gold" style={{ borderRadius: 20, padding: '2px 10px' }}>20 لوحة</Tag>
            <Tag color="blue" style={{ borderRadius: 20, padding: '2px 10px' }}>v1.0.0-YOUNES</Tag>
          </div>
        </div>
      </div>
      <div style={{ width: 480, background: '#030712', display: 'grid', placeItems: 'center', padding: 32, borderLeft: '1px solid #1E293B' }}>
        <Space direction="vertical" align="center" size={24} style={{ width: '100%', maxWidth: 380 }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ width: 56, height: 56, background: 'linear-gradient(135deg, #00C896, #0F172A)', borderRadius: 14, display: 'grid', placeItems: 'center', margin: '0 auto 12px', border: '1px solid rgba(0,201,140,0.3)', boxShadow: '0 0 24px rgba(0,201,140,0.2)' }}>
              <SafetyCertificateOutlined style={{ color: '#00C896', fontSize: 26 }} />
            </div>
            <Typography.Title level={3} style={{ color: '#F1F5F9', margin: 0, fontWeight: 700 }}>دخول المسؤول السيادي</Typography.Title>
            <Typography.Text style={{ color: '#64748B', fontSize: 13 }}>الوصول للوحة الموحدة 20 صفحة</Typography.Text>
          </div>
          <Card style={{ width: '100%', borderColor: '#1E293B', background: '#0F172A', borderRadius: 16, boxShadow: '0 16px 48px rgba(0,0,0,0.4)' }} styles={{ body: { padding: 28 } }}>
            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 20, borderRadius: 10 }} />}
            <Form layout="vertical" onFinish={submit} size="large">
              <Form.Item name="username" rules={[{ required: true, message: 'أدخل اسم المستخدم' }]}>
                <Input prefix={<UserOutlined style={{color:'#64748B'}} />} placeholder="اسم المستخدم — red_admin" autoComplete="username"
                  style={{ background: '#1E293B', borderColor: '#334155', color: '#fff', height: 48, borderRadius: 10 }} />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: 'أدخل كلمة المرور' }]}>
                <Input.Password prefix={<LockOutlined style={{color:'#64748B'}} />} placeholder="كلمة المرور" autoComplete="current-password"
                  style={{ background: '#1E293B', borderColor: '#334155', color: '#fff', height: 48, borderRadius: 10 }} />
              </Form.Item>
              <Button htmlType="submit" type="primary" block loading={loading}
                style={{ background: 'linear-gradient(90deg, #00C896, #00A878)', color: '#020617', fontWeight: 800, height: 48, borderRadius: 10, border: 'none', fontSize: 16, boxShadow: '0 8px 20px rgba(0,201,140,0.3)' }}>
                دخول آمن →
              </Button>
            </Form>
            <div style={{ marginTop: 18, textAlign: 'center', display: 'flex', justifyContent: 'center', gap: 8, alignItems: 'center' }}>
              <SafetyCertificateOutlined style={{ color: '#00C896' }} />
              <Typography.Text style={{ color: '#475569', fontSize: 11 }}>اتصال مشفّر · سلطة يونس المحلية · 20 صفحة موحدة</Typography.Text>
            </div>
          </Card>
          <Typography.Text style={{ color: '#334155', fontSize: 11, textAlign: 'center' }}>
            نسيت كلمة المرور؟ تواصل مع مدير النظام — لا يوجد استرداد ذاتي للمسؤول السيادي
          </Typography.Text>
        </Space>
      </div>
    </div>
  );
}
