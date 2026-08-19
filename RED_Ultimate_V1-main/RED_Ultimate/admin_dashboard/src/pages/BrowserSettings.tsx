import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Tag, Typography, Button, message } from 'antd';
import { ApiOutlined, GlobalOutlined, LockOutlined, ReloadOutlined, SafetyCertificateOutlined, WifiOutlined } from '@ant-design/icons';
import { apiFetch, authStore, probeBackend } from '../api';

function browserValue(value: boolean) { return value ? 'مفعّل' : 'غير مفعّل'; }

export default function BrowserSettings() {
  const [refreshing, setRefreshing] = useState(false);
  const [health, setHealth] = useState<{ state: string; status?: string; hint: string }>({ state: 'CHECKING', hint: 'جاري الفحص' });
  const [sessionCount, setSessionCount] = useState<number | null>(null);

  const browser = useMemo(() => ({
    online: navigator.onLine,
    origin: window.location.origin,
    secure: window.isSecureContext,
    cookiesEnabled: navigator.cookieEnabled,
    language: navigator.language,
    userAgent: navigator.userAgent,
  }), []);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    try {
      const next = await probeBackend(3000);
      setHealth(next);
      const response = await apiFetch('/api/admin/sessions');
      if (response.ok) {
        const data = await response.json().catch(() => []);
        const sessions = Array.isArray(data) ? data : (Array.isArray(data?.content) ? data.content : (Array.isArray(data?.sessions) ? data.sessions : []));
        setSessionCount(sessions.length);
      } else if (response.status === 403) {
        message.warning('الجلسات متاحة للمسؤول المصرّح فقط');
      }
    } catch {
      setHealth({ state: 'DOWN', hint: 'تعذر إكمال الفحص' });
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2} style={{ margin: 0 }}><GlobalOutlined /> إعدادات المتصفح والوصول</Typography.Title>
        <Typography.Text type="secondary">فحص آمن لبيئة لوحة الإدارة. لا تعرض الصفحة Access JWT أو Refresh Token أو كلمات المرور.</Typography.Text>
      </div>

      <Alert type="info" showIcon message="الصلاحيات لا تُمنح من المتصفح" description="المتصفح يرسل الجلسة الحالية فقط. تحديد دور ADMIN وصلاحيات الخادم يتم من SecurityConfig والـ Backend، ولا يتم تجاوز المصادقة عبر أدوات المطور أو منح وصول شامل تلقائياً." />

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Card><Statistic title="اتصال المتصفح" value={browserValue(browser.online)} prefix={<WifiOutlined />} valueStyle={{ color: browser.online ? '#52c41a' : '#ff4d4f' }} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="سياق HTTPS" value={browserValue(browser.secure)} prefix={<LockOutlined />} valueStyle={{ color: browser.secure ? '#52c41a' : '#faad14' }} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="جلسات المسؤول" value={sessionCount ?? '—'} prefix={<SafetyCertificateOutlined />} /></Card></Col>
      </Row>

      <Card title="بيانات البيئة غير الحساسة" extra={<Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void refresh()}>إعادة الفحص</Button>}>
        <Descriptions bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="النطاق الحالي">{browser.origin}</Descriptions.Item>
          <Descriptions.Item label="الخادم">{health.state === 'READY' ? <Tag color="green">جاهز {health.status || ''}</Tag> : <Tag color={health.state === 'DOWN' ? 'red' : 'gold'}>{health.hint}</Tag>}</Descriptions.Item>
          <Descriptions.Item label="ملفات الارتباط">{browserValue(browser.cookiesEnabled)} — القيم الحساسة HttpOnly أو غير معروضة</Descriptions.Item>
          <Descriptions.Item label="اللغة">{browser.language}</Descriptions.Item>
          <Descriptions.Item label="Access JWT">{authStore.access() ? 'موجود في sessionStorage (القيمة مخفية)' : 'غير موجود'}</Descriptions.Item>
          <Descriptions.Item label="Refresh Token">محفوظ بالطريقة التي يحددها الخادم (القيمة مخفية)</Descriptions.Item>
          <Descriptions.Item label="User-Agent" span={2}>{browser.userAgent}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="إرشادات الأمان" extra={<ApiOutlined />}>
        <Typography.Paragraph>للدخول إلى الإعدادات عبر المتصفح، افتح لوحة الإدارة من عنوان Nginx الموثق، سجّل الدخول بحساب ADMIN المعتمد، ثم استخدم صفحة «إعدادات المتصفح والوصول». لا تحفظ الرموز في لقطات الشاشة، ولا تمنح المتصفح صلاحيات نظام تشغيل أو وصولاً إلى ملفات الأسرار.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">عمليات Kill Switch، المسح عن بُعد، التحكم في PSTN، وإدارة الأدوار تبقى خلف مسارات ADMIN منفصلة وتحتاج تأكيداً وتدقيقاً؛ هذه الصفحة للفحص وليست وسيلة لتجاوز تلك الحواجز.</Typography.Paragraph>
      </Card>
    </Space>
  );
}
