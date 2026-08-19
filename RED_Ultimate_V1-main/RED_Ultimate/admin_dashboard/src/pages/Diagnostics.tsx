import { useCallback, useEffect, useState } from 'react';
import { Button, Card, List, Spin, Tag, message } from 'antd';
import { apiFetch } from '../api';

type DiagnosticStatus = 'UNKNOWN' | 'READY' | 'ERROR';
interface DiagnosticResult {
  id: string;
  system: string;
  status: DiagnosticStatus;
  detail: string;
}

const initialResults: DiagnosticResult[] = [
  { id: 'backend', system: 'خادم الـ Backend وقاعدة البيانات', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'media', system: 'خادم Media SFU والمكالمات النشطة', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'pstn', system: 'محرك المكالمات الهاتفية PSTN / Asterisk', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'dinstar', system: 'بوابة DINSTAR UC2000-VE-8G للأجهزة السيادية', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'storage', system: 'تخزين الوسائط MinIO S3', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'redis', system: 'خادم التخزين المؤقت Redis Cache', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'postgres', system: 'PostgreSQL / قاعدة الحسابات', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'flyway', system: 'ترحيلات Flyway (V1–V29)', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
  { id: 'identity', system: 'سلطة الهوية ECDSA', status: 'UNKNOWN', detail: 'لم يُفحص بعد' },
];

async function probe(path: string): Promise<Response> {
  return apiFetch(path, { method: 'GET' });
}

/**
 * تشخيص منظومة يونس الشامل
 * فحص حي لكافة الخدمات والواجهات وصحة النظام
 */
export default function Diagnostics() {
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<DiagnosticResult[]>(initialResults);

  const runTests = useCallback(async () => {
    setLoading(true);
    const checks: Array<{ id: string; path: string; label: string; service?: string }> = [
      { id: 'backend', path: '/health', label: 'خادم الـ Backend وقاعدة البيانات' },
      { id: 'media', path: '/api/master/v1/media/active-calls', label: 'خادم Media SFU والمكالمات النشطة' },
      { id: 'pstn', path: '/api/admin/dinstar/capabilities', label: 'محرك المكالمات الهاتفية PSTN / Asterisk' },
      { id: 'dinstar', path: '/api/admin/dinstar/discover', label: 'بوابة DINSTAR UC2000-VE-8G للأجهزة السيادية' },
      // 🛢️ فحص MinIO الحقيقي عبر قسم services.minio في /health (وليس مسارًا عامًا)
      { id: 'storage', path: '/health', label: 'تخزين الوسائط MinIO S3', service: 'minio' },
      // ⚡ فحص Redis الحقيقي عبر قسم services.redis في /health
      { id: 'redis', path: '/health', label: 'خادم التخزين المؤقت Redis Cache', service: 'redis' },
      { id: 'postgres', path: '/health', label: 'PostgreSQL / قاعدة الحسابات', service: 'postgresql' },
      { id: 'flyway', path: '/health', label: 'ترحيلات Flyway (V1–V29)', service: 'flyway' },
      { id: 'identity', path: '/api/identity/authority', label: 'سلطة الهوية ECDSA' },
    ];

    const next = await Promise.all(
      checks.map(async ({ id, path, label, service }) => {
        try {
          const response = await probe(path);
          const body = await response.json().catch(() => ({}));
          if (!response.ok) {
            return { id, system: label, status: 'ERROR' as const, detail: `HTTP ${response.status}` };
          }
          // قراءة حالة الخدمة الجزئية من خريطة services عند الطلب
          if (service === 'flyway') {
            const fw = body?.flyway;
            if (!fw) {
              return { id, system: label, status: 'ERROR' as const, detail: 'لا توجد بيانات Flyway من الخادم' };
            }
            if (fw.error) {
              return { id, system: label, status: 'ERROR' as const, detail: fw.error };
            }
            return {
              id, system: label, status: 'READY' as const,
              detail: `V${fw.latestVersion || '—'} · ${fw.appliedCount ?? 0} ترحيل مطبّق`,
            };
          }
          if (service) {
            const svc = body?.services?.[service];
            if (!svc) {
              return { id, system: label, status: 'ERROR' as const, detail: 'لا توجد بيانات فحص من الخادم' };
            }
            if (svc.status === 'UP') {
              return { id, system: label, status: 'READY' as const, detail: svc.bucket ? `الحاوية ${svc.bucket} متاحة` : (svc.detail || 'متصل ويستجيب') };
            }
            if (svc.status === 'DEGRADED') {
              return { id, system: label, status: 'ERROR' as const, detail: svc.detail || svc.error || 'الخدمة متدهورة' };
            }
            return { id, system: label, status: 'ERROR' as const, detail: svc.error || svc.detail || 'الخدمة معطلة' };
          }
          if (id === 'identity') {
            const algo = String(body.algorithm || '');
            const ok = algo === 'ECDSA_P256_SHA256' || algo === 'SHA256withECDSA';
            return {
              id, system: label,
              status: ok ? 'READY' as const : 'ERROR' as const,
              detail: ok ? `${algo} · ${body.version || 'v1'} · ${body.curve || ''}` : (algo || 'خوارزمية غير متوقعة'),
            };
          }
          const status: DiagnosticStatus =
            body.status === 'OFFLINE' || body.status === 'DOWN' ? 'ERROR' : 'READY';
          return { id, system: label, status, detail: body.status || 'استجاب الخادم بنجاح وبكفاءة عالية' };
        } catch (failure) {
          return {
            id,
            system: label,
            status: 'ERROR' as const,
            detail: failure instanceof Error ? failure.message : 'تعذر الاتصال',
          };
        }
      })
    );

    setResults(next);
    setLoading(false);
    if (next.every((result) => result.status === 'READY')) {
      message.success('اكتمل الفحص الحي بنجاح — كافة مكونات المنظومة تعمل بكفاءة');
    } else {
      message.warning('اكتمل الفحص مع تنبيهات لبعض الخدمات');
    }
  }, []);

  useEffect(() => {
    void runTests();
  }, [runTests]);

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ color: '#E0A83C', marginBottom: 8 }}>تشخيص منظومة يونس السيادية</h2>
      <p style={{ color: '#8A9FB2', marginBottom: 20 }}>
        فحص حي ومباشر لكافة الخوادم والبوابات وقواعد البيانات وشبكات الاتصال.
      </p>
      <Button
        type="primary"
        onClick={() => void runTests()}
        loading={loading}
        style={{ marginBottom: 20, background: '#00C896', borderColor: '#00C896' }}
      >
        {loading ? 'جارٍ الفحص…' : 'بدء الفحص الحي الشامل'}
      </Button>
      <Spin spinning={loading}>
        <List
          grid={{ gutter: 16, column: 1 }}
          dataSource={results}
          renderItem={(item) => (
            <List.Item>
              <Card style={{ background: '#081525', borderColor: '#17344A' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
                  <span style={{ color: '#F1F7FA', fontWeight: 500 }}>{item.system}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Tag color={item.status === 'READY' ? 'success' : item.status === 'ERROR' ? 'error' : 'default'}>
                      {item.status === 'READY' ? 'متاح ونشط' : item.status === 'ERROR' ? 'تنبيه اتصال' : 'غير مفحوص'}
                    </Tag>
                    <small style={{ color: '#8A9FB2' }}>{item.detail}</small>
                  </div>
                </div>
              </Card>
            </List.Item>
          )}
        />
      </Spin>
    </div>
  );
}
