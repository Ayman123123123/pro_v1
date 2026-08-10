import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Tag } from 'antd';
import { apiFetch } from '../../api';

export default function MediaTab() {
  const [data, setData] = useState<any>(null);
  const [online, setOnline] = useState(false);
  useEffect(() => { apiFetch('/api/master/v1/media/active-calls').then(async r => { setOnline(r.ok); if (r.ok) setData(await r.json()); }); }, []);
  return <Card title="WebRTC / mediasoup SFU" extra={<Tag color={online ? 'green' : 'red'}>{online ? 'ONLINE' : 'UNAVAILABLE'}</Tag>}>
    <Descriptions bordered column={1}>
      <Descriptions.Item label="محرك الوسائط">mediasoup + WebRTC</Descriptions.Item>
      <Descriptions.Item label="البيانات الحية"><pre>{JSON.stringify(data || {}, null, 2)}</pre></Descriptions.Item>
      <Descriptions.Item label="ملاحظة">الفيديو يعمل داخل يونس عبر WebRTC؛ مسار DINSTAR مخصص للصوت الهاتفي.</Descriptions.Item>
    </Descriptions>
  </Card>;
}
