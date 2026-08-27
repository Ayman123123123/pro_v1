import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Card, Col, Descriptions, Row, Space, Statistic, Tag, Typography, Button, message } from 'antd';
import { ApiOutlined, GlobalOutlined, LockOutlined, ReloadOutlined, SafetyCertificateOutlined, WifiOutlined } from '@ant-design/icons';
import { apiFetch, asArray, authStore, probeBackend, type BackendProbe } from '../api';

/**
 * فحص بيئة المتصفح للوحة الإدارة.
 *
 * الغرض تشخيصي بحت: يجيب على «هل المشكلة في المتصفح أم في الخادم؟» دون أن
 * يعرض أي سرّ. القيم الحساسة تُعرض كوجود/عدم وجود فقط:
 *   • Access JWT في `sessionStorage` — تُعرض الحالة، لا القيمة.
 *   • Refresh Token في كوكي HttpOnly — لا يمكن لـ JS قراءته أصلًا.
 *
 * الصفحة **لا تمنح** أي صلاحية. تحديد الأدوار يحدث في `SecurityConfig` على
 * الخادم؛ ولا يُغيّر أي شيء هنا نتيجة تحقّق الخادم.
 */

function enabledLabel(value: boolean) { return value ? 'مفعّل' : 'غير مفعّل'; }

export default function BrowserSettings() {
  const [refreshing, setRefreshing] = useState(false);
  const [health, setHealth] = useState<BackendProbe>({ state: 'CHECKING', hint: 'جاري الفحص' });
  const [sessionCount, setSessionCount] = useState<number | null>(null);
  const [sessionNote, setSessionNote] = useState<string | null>(null);

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
    setSessionNote(null);
    try {
      setHealth(await probeBackend(3000));
    } catch {
      setHealth({ state: 'DOWN', hint: 'تعذر إكمال فحص الخادم' });
    }

    // عدّ الجلسات مستقل عن فحص الصحة: فشل أحدهما لا يُخفي نتيجة الآخر.
    try {
      const response = await apiFetch('/api/admin/sessions');
      if (response.ok) {
        const data = await response.json().catch(() => []);
        // asArray يوحّد الأشكال الثلاثة التي يعيدها الخادم: مصفوفة مباشرة،
        // أو صفحة Spring (`content`)، أو غلاف (`sessions`).
        setSessionCount(asArray(data).length);
      } else if (response.status === 403) {
        setSessionCount(null);
        setSessionNote('الجلسات متاحة للمسؤول المصرّح فقط');
        message.warning('الجلسات متاحة للمسؤول المصرّح فقط');
      } else {
        setSessionCount(null);
        setSessionNote(`تعذر قراءة الجلسات (HTTP ${response.status})`);
      }
    } catch {
      setSessionCount(null);
      setSessionNote('تعذر الوصول إلى الخادم لقراءة الجلسات');
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const healthTag = health.state === 'READY'
    ? <Tag color="green">جاهز {health.status || ''}</Tag>
    : <Tag color={health.state === 'DOWN' ? 'red' : 'gold'}>{health.hint}</Tag>;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Typography.Title level={2} style={{ margin: 0 }}><GlobalOutlined /> إعدادات المتصفح والوصول</Typography.Title>
        <Typography.Text type="secondary">فحص آمن لبيئة لوحة الإدارة. لا تعرض الصفحة Access JWT أو Refresh Token أو كلمات المرور.</Typography.Text>
      </div>

      <Alert
        type="info"
        showIcon
        message="الصلاحيات لا تُمنح من المتصفح"
        description="المتصفح يرسل الجلسة الحالية فقط. تحديد دور ADMIN وصلاحيات الخادم يتم في SecurityConfig على الباك-إند، ولا يُتجاوز عبر أدوات المطور."
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title="اتصال المتصفح"
              value={enabledLabel(browser.online)}
              prefix={<WifiOutlined />}
              valueStyle={{ color: browser.online ? '#14C79A' : '#F25C5C' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title="سياق HTTPS"
              value={enabledLabel(browser.secure)}
              prefix={<LockOutlined />}
              valueStyle={{ color: browser.secure ? '#14C79A' : '#E0B551' }}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title="جلسات المسؤول"
              value={sessionCount ?? '—'}
              prefix={<SafetyCertificateOutlined />}
            />
            {sessionNote && <Typography.Text type="warning" style={{ fontSize: 12 }}>{sessionNote}</Typography.Text>}
          </Card>
        </Col>
      </Row>

      <Card
        title="بيانات البيئة غير الحساسة"
        extra={<Button icon={<ReloadOutlined />} loading={refreshing} onClick={() => void refresh()}>إعادة الفحص</Button>}
      >
        <Descriptions bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="النطاق الحالي">{browser.origin}</Descriptions.Item>
          <Descriptions.Item label="الخادم">{healthTag}</Descriptions.Item>
          <Descriptions.Item label="ملفات الارتباط">
            {enabledLabel(browser.cookiesEnabled)} — القيم الحساسة HttpOnly وغير مقروءة لـ JS
          </Descriptions.Item>
          <Descriptions.Item label="اللغة">{browser.language}</Descriptions.Item>
          <Descriptions.Item label="Access JWT">
            {authStore.access() ? 'موجود في sessionStorage (القيمة مخفية)' : 'غير موجود'}
          </Descriptions.Item>
          <Descriptions.Item label="Refresh Token">
            كوكي HttpOnly على مسار /api/auth — لا يمكن لـ JS قراءته
          </Descriptions.Item>
          <Descriptions.Item label="User-Agent" span={2}>{browser.userAgent}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="إرشادات الأمان" extra={<ApiOutlined />}>
        <Typography.Paragraph>
          افتح لوحة الإدارة من عنوان Nginx الموثق، وسجّل الدخول بحساب ADMIN معتمد. لا تحفظ الرموز في لقطات الشاشة،
          ولا تمنح المتصفح صلاحيات نظام تشغيل أو وصولًا إلى ملفات الأسرار.
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary">
          Kill Switch، المسح عن بُعد، التحكم في PSTN، وإدارة الأدوار تبقى خلف مسارات ADMIN منفصلة تحتاج تأكيدًا وتدقيقًا؛
          هذه الصفحة للفحص لا لتجاوز تلك الحواجز.
        </Typography.Paragraph>
      </Card>
    </Space>
  );
}
