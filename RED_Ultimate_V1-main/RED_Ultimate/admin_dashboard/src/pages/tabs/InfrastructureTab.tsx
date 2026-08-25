import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Space, Tag } from 'antd';
import { apiFetch } from '../../api';
import LogStreamerTab from './LogStreamerTab';

type HealthBody = {
  brand?: string;
  displayName?: string;
  status?: string;
  version?: string;
  service?: string;
  db?: string;
  timestamp?: string;
  services?: Record<string, { status?: string; detail?: string; bucket?: string; database?: string; error?: string }>;
  flyway?: { latestVersion?: string | null; appliedCount?: number; error?: string | null };
};

export default function InfrastructureTab() {
  const [health, setHealth] = useState<{ ok: boolean; body: HealthBody } | null>(null);
  const [authority, setAuthority] = useState<any>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let alive = true;
    const load = async () => {
      try {
        const [h, a] = await Promise.all([
          apiFetch('/health'),
          apiFetch('/api/identity/authority'),
        ]);
        const body = await h.json().catch(() => ({}));
        const auth = a.ok ? await a.json().catch(() => null) : null;
        if (!alive) return;
        setHealth({ ok: h.ok, body });
        setAuthority(auth);
        setError(h.ok ? '' : `HTTP ${h.status}`);
      } catch {
        if (alive) {
          setHealth({ ok: false, body: {} });
          setError('تعذّر الوصول إلى الخادم');
        }
      }
    };
    void load();
    return () => { alive = false; };
  }, []);

  const services = Object.entries(health?.body?.services || {});
  const flyway = health?.body?.flyway;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="البنية المحلية"
        extra={<Tag color={health?.ok ? 'green' : 'red'}>{health?.ok ? (health.body.status || 'HEALTHY') : 'CHECKING / DOWN'}</Tag>}
      >
        {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="العلامة">{health?.body?.brand || 'YOUNES'} — {health?.body?.displayName || 'يونس'}</Descriptions.Item>
          <Descriptions.Item label="الإصدار">{health?.body?.version || '—'}</Descriptions.Item>
          <Descriptions.Item label="الخدمة">{health?.body?.service || 'backend'}</Descriptions.Item>
          <Descriptions.Item label="Flyway">
            {flyway
              ? `V${flyway.latestVersion || '—'} · ${flyway.appliedCount ?? 0} ترحيل`
              : 'غير متاح على هذا الخادم'}
          </Descriptions.Item>
          <Descriptions.Item label="سلطة الهوية">
            {authority
              ? `${authority.algorithm || '—'} · ${authority.version || '—'} · ${authority.curve || ''}`
              : 'تعذر القراءة'}
          </Descriptions.Item>
          <Descriptions.Item label="النمط">Local-first — بدون دومين أثناء التطوير</Descriptions.Item>
          <Descriptions.Item label="الوصول الخارجي">WireGuard ثم TLS + Domain عند الإطلاق</Descriptions.Item>
        </Descriptions>
        {services.length > 0 && (
          <Space wrap style={{ marginTop: 12 }}>
            {services.map(([name, svc]) => (
              <Tag key={name} color={svc?.status === 'UP' ? 'green' : svc?.status === 'DEGRADED' ? 'orange' : 'red'}>
                {name}: {svc?.status || 'UNKNOWN'}{svc?.bucket ? ` (${svc.bucket})` : ''}
              </Tag>
            ))}
          </Space>
        )}
      </Card>
      <LogStreamerTab />
    </Space>
  );
}
