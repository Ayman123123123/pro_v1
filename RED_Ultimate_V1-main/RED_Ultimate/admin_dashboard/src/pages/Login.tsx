import React, { useCallback, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography, Space, Tag, Badge, Divider } from 'antd';
import {
  ApiOutlined,
  CloudServerOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { adminLogin, probeBackend, type BackendProbe } from '../api';
import { usePolling } from '../hooks/usePolling';

interface LoginProps {
  onLogin?: (username: string, password: string) => Promise<void>;
  onSuccess?: () => void;
  isLoading?: boolean;
}

export default function Login({ onLogin, onSuccess, isLoading }: LoginProps) {
  const [error, setError] = useState('');
  const [internalLoading, setInternalLoading] = useState(false);
  const [probe, setProbe] = useState<BackendProbe>({ state: 'CHECKING', hint: 'جاري الاتصال بالسيرفر' });
  const loading = isLoading ?? internalLoading;
  // عداد الفشل المتتالي: لا نعلن «غير متصل» من أول فشل عابر — فوسيط المعاينة
  // أو بدء تشغيل الخادم قد يبطئ الطلب الأول فقط.
  const failsRef = useRef(0);

  const refreshProbe = useCallback(async () => {
    const next = await probeBackend();
    if (next.state === 'READY' || next.state === 'LIVE') {
      failsRef.current = 0;
      setProbe(next);
      return;
    }
    failsRef.current += 1;
    if (failsRef.current >= 2) setProbe(next);
  }, []);
  usePolling(refreshProbe, 4000);

  const healthMeta = useMemo(() => {
    if (probe.state === 'READY') return { color: 'success' as const, text: 'الخادم متصل' };
    if (probe.state === 'LIVE') return { color: 'warning' as const, text: 'الخادم يعمل — جاري التجهيز' };
    if (probe.state === 'DOWN') return { color: 'error' as const, text: 'الخادم غير متصل' };
    return { color: 'processing' as const, text: 'جاري الاتصال بالسيرفر' };
  }, [probe]);

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
        <div style={{ position: 'absolute', top: -120, right: -90, width: 360, height: 360, background: 'radial-gradient(circle, rgba(183,138,46,0.18) 0%, transparent 68%)', borderRadius: '50%' }} />
        <div style={{ position: 'absolute', bottom: -100, left: -80, width: 300, height: 300, background: 'radial-gradient(circle, rgba(224, 168, 60,0.12) 0%, transparent 70%)', borderRadius: '50%' }} />
        <div style={{ position: 'relative', zIndex: 1, maxWidth: 620 }}>
          <Space align="center" size={14} style={{ marginBottom: 32 }}>
            <img src="/admin-master-icon.svg" alt="شعار يونس" style={{ width: 54, height: 54, borderRadius: 16, boxShadow: '0 10px 32px rgba(183,138,46,0.32)' }} />
            <div>
              <div style={{ color: '#B78A2E', fontWeight: 900, fontSize: 24, letterSpacing: 1 }}>YOUNES MASTER</div>
              <div style={{ color: '#64748B', fontSize: 11, letterSpacing: 2, marginTop: -4 }}>SOVEREIGN ADMIN CONSOLE</div>
            </div>
          </Space>

          <Typography.Title level={1} style={{ color: '#F8FAFC', fontSize: 42, lineHeight: 1.18, marginBottom: 16, fontWeight: 900 }}>
            لوحة واحدة معتمدة<br />
            <span style={{ background: 'linear-gradient(90deg, #B78A2E, #4FC3F7, #E0A83C)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' as any }}>لكل عمليات يونس</span>
          </Typography.Title>
          <Typography.Paragraph style={{ color: '#94A3B8', fontSize: 16, lineHeight: 1.9, maxWidth: 560 }}>
            تم اعتماد النسخة الحديثة ودمج وظائف النسخ القديمة داخلها: تسجيل دخول آمن، مراقبة حية، إدارة المستخدمين، الموافقات، المحتوى، DINSTAR، الوسائط، الإشعارات، النسخ الاحتياطي، والتدقيق.
          </Typography.Paragraph>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 12, marginTop: 28 }}>
            {featureCards.map(item => (
              <div key={item.title} style={{ display: 'flex', gap: 12, alignItems: 'center', background: 'rgba(15,23,42,0.62)', border: '1px solid rgba(30,41,59,0.9)', borderRadius: 14, padding: '13px 15px', backdropFilter: 'blur(10px)' }}>
                <div style={{ width: 34, height: 34, borderRadius: 10, display: 'grid', placeItems: 'center', background: 'rgba(183,138,46,0.12)', color: '#B78A2E', fontSize: 18 }}>{item.icon}</div>
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
            <img src="/admin-master-icon.svg" alt="" style={{ width: 60, height: 60, borderRadius: 18, margin: '0 auto 12px', display: 'block', border: '1px solid rgba(183,138,46,0.35)', boxShadow: '0 0 30px rgba(183,138,46,0.22)' }} />
            <Typography.Title level={3} style={{ color: '#F1F5F9', margin: 0, fontWeight: 800 }}>دخول المسؤول السيادي</Typography.Title>
            <Space style={{ marginTop: 8 }}>
              <Badge status={healthMeta.color} text={<span style={{ color: probe.state === 'DOWN' ? '#FCA5A5' : '#94A3B8', fontSize: 12 }}>{healthMeta.text}</span>} />
            </Space>
          </div>

          <Card style={{ width: '100%', borderColor: '#1E293B', background: '#0F172A', borderRadius: 18, boxShadow: '0 20px 60px rgba(0,0,0,0.45)' }} styles={{ body: { padding: 28 } }}>
            {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 20, borderRadius: 10 }} />}
            {!error && probe.state === 'DOWN' && (
              <Alert
                type="warning"
                showIcon
                message="الخادم غير متصل"
                description={probe.hint}
                style={{ marginBottom: 20, borderRadius: 10 }}
                action={<Button size="small" onClick={() => { failsRef.current = 0; setProbe({ state: 'CHECKING', hint: 'جاري الاتصال بالسيرفر' }); void refreshProbe(); }}>إعادة الفحص</Button>}
              />
            )}
            {!error && probe.state === 'LIVE' && (
              <Alert
                type="info"
                showIcon
                message="الخادم يبدأ الآن"
                description="يمكنك المحاولة بعد ثوانٍ. كلمة المرور من RED_ADMIN_PASSWORD في ملف .env."
                style={{ marginBottom: 20, borderRadius: 10 }}
              />
            )}
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
                style={{ background: 'linear-gradient(90deg, #B78A2E, #9A7524)', color: '#020617', fontWeight: 900, height: 49, borderRadius: 11, border: 'none', fontSize: 16, boxShadow: '0 8px 22px rgba(183,138,46,0.28)' }}>
                دخول آمن ←
              </Button>
            </Form>
            <Divider style={{ borderColor: '#1E293B', margin: '20px 0 14px' }} />
            <Space align="center" style={{ width: '100%', justifyContent: 'center' }}>
              <SafetyCertificateOutlined style={{ color: '#B78A2E' }} />
              <Typography.Text style={{ color: '#64748B', fontSize: 11 }}>Access JWT + Refresh Rotation · ADMIN only</Typography.Text>
            </Space>
          </Card>

          <Typography.Text style={{ color: '#64748B', fontSize: 12, textAlign: 'center', lineHeight: 1.7 }}>
            Docker: اسم المستخدم من <b>RED_ADMIN_USERNAME</b> وكلمة المرور من <b>RED_ADMIN_PASSWORD</b> في ملف <b>RED_Ultimate/.env</b>
          </Typography.Text>
          <Typography.Text style={{ color: '#334155', fontSize: 11, textAlign: 'center', lineHeight: 1.7 }}>
            لا يوجد استرداد ذاتي للمسؤول السيادي. كلمة المرور ليست في الواجهة.
          </Typography.Text>
        </Space>
      </div>
    </div>
  );
}
