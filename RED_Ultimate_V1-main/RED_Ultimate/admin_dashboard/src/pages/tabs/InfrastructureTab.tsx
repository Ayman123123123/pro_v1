import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Tag } from 'antd';
import { apiFetch } from '../../api';
import LogStreamerTab from './LogStreamerTab';

export default function InfrastructureTab() {
  const [health, setHealth] = useState<any>(null);
  // الحارس `alive` يمنع setState بعد تفكيك المكوّن: التنقل السريع بين
  // الصفحات كان يترك الطلب معلّقًا ثم يكتب في مكوّن مُزال (تحذير React
  // وتسريب مرجع). والطلب نفسه لا يُلغى تلقائيًا.
  useEffect(() => {
    let alive = true;
    apiFetch('/health')
      .then(async r => {
        const body = await r.json().catch(() => ({}));
        if (alive) setHealth({ ok: r.ok, body });
      })
      .catch(() => { if (alive) setHealth({ ok: false, body: { error: 'تعذّر الوصول إلى الخادم' } }); });
    return () => { alive = false; };
  }, []);
  return <>
    <Card title="البنية المحلية" style={{marginBottom:16}} extra={<Tag color={health?.ok ? 'green':'red'}>{health?.ok ? 'HEALTHY':'CHECKING / DOWN'}</Tag>}>
      <Descriptions bordered column={1}>
        <Descriptions.Item label="النمط">Local-first — بدون دومين أثناء التطوير</Descriptions.Item>
        <Descriptions.Item label="الوصول الخارجي">WireGuard ثم TLS + Domain عند الإطلاق</Descriptions.Item>
        <Descriptions.Item label="Health"><pre>{JSON.stringify(health?.body || {}, null, 2)}</pre></Descriptions.Item>
      </Descriptions>
    </Card>
    <LogStreamerTab />
  </>;
}
