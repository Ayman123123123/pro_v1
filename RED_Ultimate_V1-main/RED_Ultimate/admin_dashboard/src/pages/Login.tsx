import React, { useCallback, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography, Space, Tag, Badge, Divider } from 'antd';
import {
  ApiOutlined,
  CloudServerOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { adminLogin } from '../api';
import { usePolling } from '../hooks/usePolling';

interface LoginProps {
  onLogin?: (username: string, password: string) => Promise<void>;
  onSuccess?: () => void;
  isLoading?: boolean;
}

type HealthState = 'CHECKING' | 'UP' | 'DOWN';

export default function Login({ onLogin, onSuccess, isLoading }: LoginProps) {
  const [error, setError] = useState('');
  const [internalLoading, setInternalLoading] = useState(false);
  const [health, setHealth] = useState<HealthState>('CHECKING');
  const loading = isLoading ?? internalLoading;
  // عداد الفشل المتتالي: لا نعلن «غير متصل» من أول فشل عابر — فوسيط المعاينة
  // أو بدء تشغيل الخادم قد يبطئ الطلب الأول فقط.
  const failsRef = useRef(0);

  const probe = useCallback(async () => {
    const ctrl = new AbortController();
    const kill = window.setTimeout(() => ctrl.abort(), 8000);
    try {
      const r = await fetch('/health', { signal: ctrl.signal });
      const data = r.ok ? await r.json().catch(() => ({})) : null;
      const status = String(data?.status ?? '').toUpperCase();
      const ok = !!data && (status === 'UP' || status === 'HEALTHY' || status === 'DEGRADED');
      if (ok) {
        failsRef.current = 0;
        setHealth('UP');
      } else {
        failsRef.current += 1;
        if (failsRef.current >= 2) setHealth('DOWN');
      }
    } catch {
      failsRef.current += 1;
      if (failsRef.current >= 2) setHealth('DOWN');
    } finally {
      window.clearTimeout(kill);
    }
  }, []);
  usePolling(probe, 4000);

  const healthMeta = useMemo(() => {
    if (health === 'UP') return { color: 'success' as const, text: 'الخادم متصل' };
    if (health === 'DOWN') return { color: 'error' as const, text: 'الخادم غير متصل' };
    return { color: 'processing' as const, text: 'جاري الاتصال بالسيرفر' };
  }, [health]);

  const submit = async (values: { username: string; password: string }) => {
    if (onLogin) {
      try {
        setError('');
        await onLogin(values.username, values.password);
      } catch (e: any) {
        setError(e.message || 'تعذر تسجيل الدخول');
      }
      return;
    }
    setInternalLoading(true);
    setError('');
    try {
      await adminLogin(values.username, values.password);
      onSuccess?.();
    } catch (e: any) {
      setError(e.message || 'تعذر تسجيل الدخول');
    } finally {
      setInternalLoading(false);
    }
  };

  const featureCards = [
    { icon: <SafetyCertificateOutlined />, title: 'سلطة محلية', desc: 'موافقات الحسابات والأجهزة بلا هاتف أو OTP' },
    { icon: <LockOutlined />, title: 'إدارة أمنية', desc: 'Kill Switch، مسح عن بُعد، تدقيق، وجلسات' },
    { icon: <ApiOutlined />, title: 'مراكز موحدة', desc: 'المستخدمون، المحتوى، DINSTAR، الوسائط، الرسائل' },
    { icon: <CloudServerOutlined />, title: 'Local-first', desc: 'Nginx + Backend + SFU + PostgreSQL + Mongo + Redis' },
  ];

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: '#020617', direction: 'rtl', overflow: 'hidden' }}>
      <div style={{ flex: 1, background: 'radial-gradient(circle at 20% 20%, rgba(0,201,150,0.16), transparent 30%), linear-gradient(135deg, #020617 0%, #0F172A 52%, #111827 100%)', display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '48px 56px', position: 'relative' }}>
        <div style={{ position: 'absolute', top: -120, right: -90, width: 360, height: 360, background: 'radial-gradient(circle, rgba(0,201,140,0.18) 0%, transparent 68%)', borderRadius: '50%' }} />
        <div style={{ position: 'absolute', bottom: -100, left: -80, width: 300, height: 300, background: 'radial-gradient(circle, rgba(232,184,74,0.12) 0%, transparent 70%)', borderRadius: '50%' }} />
        <div style={{ position: 'relative', zIndex: 1, maxWidth: 620 }}>
          <Space align="center" size={14} style={{ marginBottom: 32 }}>
            <div style={{ width: 54, height: 54, background: 'linear-gradient(135deg, #00C896 0%, #35CBE0 100%)', borderRadius: 16, display: 'grid', placeItems: 'center', color: '#020617', fontWeight: 900, fontSize: 24, boxShadow: '0 10px 32px rgba(0,201,140,0.32)' }}>◆</div>
            <div>
              <div style={{ color: '#00C896', fontWeight: 900, fontSize: 24, letterSpacing: 1 }}>YOUNES MASTER</div>
              <div style={{ color: '#64748B', fontSize: 11, letterSpacing: 2, marginTop: -4 }}>SOVEREIGN ADMIN CONSOLE</div>
            </div>
          </Space>

          <Typography.Title level={1} style={{ color: '#F8FAFC', fontSize: 42, lineHeight: 1.18, marginBottom: 16, fontWeight: 900 }}>
            لوحة واحدة معتمدة<br />
            <span style={{ background: 'linear-gradient(90deg, #00C896, #35CBE0, #E8B84A)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' as any }}>لكل عمليات يونس</span>
          </Typography.Title>
          <Typography.Paragraph style={{ color: '#94A3B8', fontSize: 16, lineHeight: 1.9, maxWidth: 560 }}>
            تم اعتماد النسخة الحديثة ودمج وظائف النسخ القديمة داخلها: تسجيل دخول آمن، مراقبة حية، إدارة المستخدمين، الموافقات، المحتوى، DINSTAR، الوسائط، الإشعارات، النسخ الاحتياطي، والتدقيق.
          </Typography.Paragraph>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 12, marginTop: 28 }}>
            {featureCards.map(item => (
              <div key={item.title} style={{ display: 'flex', gap: 12, alignItems: 'center', background: 'rgba(15,23,42,0.62)', border: '1px solid rgba(30,41,59,0.9)', borderRadius: 14, padding: '13px 15px', backdropFilter: 'blur(10px)' }}>
                <div style={{ width: 34, height: 34, borderRadius: 10, display: 'grid', placeItems: 'center', background: 'rgba(0,201,140,0.12)', color: '#00C896', fontSize: 18 }}>{item.icon}</div>
                <div><div style={{ color: '#E2E8F0', fontWeight: 700, fontSize: 13 }}>{item.title}</div><div style={{ color: '#64748B', fontSize: 11, lineHeight: 1.5 }}>{item.desc}</div></div>
              </div>
            ))}
          </div>

          <Space wrap style={{ marginTop: 30 }}>
            <Tag color="green" style={{ borderRadius: 20, padding: '3px 11px' }}>LOCAL MODE</Tag>
            <Tag color="cyan" style={{ borderRadius: 20, padding: '3px 11px' }}>RTL MODERN</Tag>
            <Tag color="gold" style={{ borderRadius: 20, padding: '3px 11px' }}>NO LEGACY PANELS</Tag>
            <Tag color="blue" style={{ borderRadius: 20, padding: '3px 11px' }}>v1.0.0-YOUNES</Tag>
          </Space>
        </div>
      </div>

      <div style={{ width: 500, background: '#030712', display: 'grid', placeItems: 'center', padding: 34, borderLeft: '1px solid #1E293B' }}>
        <Space direction="vertical" align="center" size={22} style={{ width: '100%', maxWidth: 390 }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ width: 60, height: 60, background: 'linear-gradient(135deg, rgba(0,201,140,0.95), rgba(15,23,42,0.95))', borderRadius: 18, display: 'grid', placeItems: 'center', margin: '0 auto 12px', border: '1px solid rgba(0,201,140,0.35)', boxShadow: '0 0 30px rgba(0,201,140,0.22)' }}>
              <ThunderboltOutlined style={{ color: '#DFFCF4', fontSize: 28 }} />
            </div>
            <Typography.Title level={3} style={{ color: '#F1F5F9', margin: 0, fontWeight: 800 }}>دخول المسؤول السيادي</Typography.Title>
            <Space style={{ marginTop: 8 }}>
              <Badge status={healthMeta.color} text={<span style={{ color: health === 'DOWN' ? '#FCA5A5' : '#94A3B8', fontSize: 12 }}>{healthMeta.text}</span>} />
            </Space>
          </div>

          <Card style={{ width: '100%', borderColor: '#1E293B', background: '#0F172A', borderRadius: 18, boxShadow: '0 20px 60px rgba(0,0,0,0.45)' }} styles={{ body: { padding: 28 } }}>
            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 20, borderRadius: 10 }} />}
            {health === 'DOWN' && <Alert type="warning" showIcon message="تعذر الوصول إلى /health" description="يمكنك محاولة الدخول إذا كان البروكسي أو الخادم يبدأان الآن." style={{ marginBottom: 20, borderRadius: 10 }} action={<Button size="small" onClick={() => { setHealth('CHECKING'); void probe(); }}>إعادة الفحص</Button>} />}
            <Form layout="vertical" onFinish={submit} size="large">
              <Form.Item name="username" rules={[{ required: true, message: 'أدخل اسم المستخدم' }]} initialValue="red_admin">
                <Input prefix={<UserOutlined style={{color:'#64748B'}} />} placeholder="اسم المستخدم — red_admin" autoComplete="username"
                  style={{ background: '#1E293B', borderColor: '#334155', color: '#fff', height: 48, borderRadius: 10 }} />
              </Form.Item>
              <Form.Item name="password" rules={[{ required: true, message: 'أدخل كلمة المرور' }]}>
                <Input.Password prefix={<LockOutlined style={{color:'#64748B'}} />} placeholder="كلمة المرور" autoComplete="current-password"
                  style={{ background: '#1E293B', borderColor: '#334155', color: '#fff', height: 48, borderRadius: 10 }} />
              </Form.Item>
              <Button htmlType="submit" type="primary" block loading={loading}
                style={{ background: 'linear-gradient(90deg, #00C896, #00A878)', color: '#020617', fontWeight: 900, height: 49, borderRadius: 11, border: 'none', fontSize: 16, boxShadow: '0 8px 22px rgba(0,201,140,0.28)' }}>
                دخول آمن →
              </Button>
            </Form>
            <Divider style={{ borderColor: '#1E293B', margin: '20px 0 14px' }} />
            <Space align="center" style={{ width: '100%', justifyContent: 'center' }}>
              <SafetyCertificateOutlined style={{ color: '#00C896' }} />
              <Typography.Text style={{ color: '#64748B', fontSize: 11 }}>Access JWT + Refresh Rotation · ADMIN only</Typography.Text>
            </Space>
          </Card>

          {import.meta.env.DEV && (
            <Typography.Text style={{ color: '#64748B', fontSize: 12, textAlign: 'center', lineHeight: 1.7 }}>
              التطوير المحلي: <b>red_admin</b> أو <b>younes_sovereign</b> · كلمة المرور <b>SovereignAdmin1</b>
            </Typography.Text>
          )}
          <Typography.Text style={{ color: '#334155', fontSize: 11, textAlign: 'center', lineHeight: 1.7 }}>
            نسيت كلمة المرور؟ استخدم حساب مسؤول آخر لإصدار كلمة مؤقتة من مركز المستخدمين. لا يوجد استرداد ذاتي للمسؤول السيادي.
          </Typography.Text>
        </Space>
      </div>
    </div>
  );
}
