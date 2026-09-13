import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
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
  responseTimeMs?: number;
  services?: Record<string, { status?: string; detail?: string; bucket?: string; database?: string; error?: string }>;
  flyway?: { latestVersion?: string | null; appliedCount?: number; error?: string | null };
};

function statusTag(status: string | undefined, ok: boolean | undefined): { color: string; label: string } {
  const s = String(status || '').toUpperCase();
  if (ok && (s === 'UP' || s === 'HEALTHY')) return { color: 'success', label: status || 'HEALTHY' };
  if (s === 'DEGRADED') return { color: 'warning', label: 'DEGRADED — يعمل جزئيًا' };
  if (ok === undefined) return { color: 'default', label: 'جارٍ الفحص…' };
  return { color: 'error', label: s || 'DOWN' };
}

export default function InfrastructureTab() {
  const [health, setHealth] = useState<{ ok: boolean; body: HealthBody } | null>(null);
  const [authority, setAuthority] = useState<any>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const aliveRef = useRef(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [h, a] = await Promise.all([
        apiFetch('/health'),
        apiFetch('/api/identity/authority'),
      ]);
      const body = await h.json().catch(() => ({}));
      const auth = a.ok ? await a.json().catch(() => null) : null;
      if (!aliveRef.current) return;
      setHealth({ ok: h.ok, body });
      setAuthority(auth);
      setError(h.ok ? '' : `الخادم يجيب بحالة ${body?.status || h.status} (HTTP ${h.status})`);
      setUpdatedAt(new Date());
    } catch {
      if (!aliveRef.current) return;
      setHealth({ ok: false, body: {} });
      setError('تعذّر الوصول إلى الخادم');
    } finally {
      if (aliveRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    aliveRef.current = true;
    void load();
    return () => { aliveRef.current = false; };
  }, [load]);

  const services = Object.entries(health?.body?.services || {});
  const flyway = health?.body?.flyway;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title="البنية المحلية"
        extra={
          <Space>
            {updatedAt && (
              <span style={{ fontSize: 12, color: '#8A9FB2' }}>
                آخر تحديث: {updatedAt.toLocaleString('ar', { hour12: false })}
              </span>
            )}
            <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>
              تحديث
            </Button>
            <Tag color={statusTag(health?.body?.status, health ? health.ok : undefined).color}>
              {statusTag(health?.body?.status, health ? health.ok : undefined).label}
            </Tag>
          </Space>
        }
      >
        {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="العلامة">{health?.body?.brand || 'YOUNES'} — {health?.body?.displayName || 'يونس'}</Descriptions.Item>
          <Descriptions.Item label="الإصدار">{health?.body?.version || '—'}</Descriptions.Item>
          <Descriptions.Item label="الخدمة">{health?.body?.service || 'backend'}</Descriptions.Item>
          <Descriptions.Item label="زمن استجابة الفحص">
            {health?.body?.responseTimeMs != null ? `${health.body.responseTimeMs}ms` : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Flyway">
            {flyway
              ? (flyway.error
                ? `غير مقروء مؤقتًا (${flyway.error})`
                : `V${flyway.latestVersion || '—'} · ${flyway.appliedCount ?? 0} ترحيل`)
              : 'غير متاح على هذا الخادم'}
          </Descriptions.Item>
          <Descriptions.Item label="سلطة الهوية">
            {authority
              ? [authority.algorithm || '—', authority.version || '—', authority.curve].filter(Boolean).join(' · ')
              : 'تعذر القراءة'}
          </Descriptions.Item>
          <Descriptions.Item label="النمط">Local-first — بدون دومين أثناء التطوير</Descriptions.Item>
          <Descriptions.Item label="الوصول الخارجي">WireGuard ثم TLS + Domain عند الإطلاق</Descriptions.Item>
        </Descriptions>
        {services.length > 0 && (
          <Space wrap style={{ marginTop: 12 }}>
            {services.map(([name, svc]) => (
              <Tag key={name} color={svc?.status === 'UP' ? 'success' : svc?.status === 'DEGRADED' ? 'warning' : 'error'}>
                {name}: {svc?.status || 'UNKNOWN'}{svc?.bucket ? ` (${svc.bucket})` : ''}{svc?.error && svc.status !== 'UP' ? ` — ${svc.error}` : ''}
              </Tag>
            ))}
          </Space>
        )}
      </Card>
      <LogStreamerTab />
    </Space>
  );
}
