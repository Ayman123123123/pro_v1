import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Tag } from 'antd';
import { apiFetch } from '../../api';
import LogStreamerTab from './LogStreamerTab';

export default function InfrastructureTab() {
  const [health, setHealth] = useState<any>(null);
  useEffect(() => { apiFetch('/health').then(async r => setHealth({ ok: r.ok, body: await r.json().catch(() => ({})) })); }, []);
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
