import React, { useEffect, useState } from 'react';
import { Button, Card, InputNumber, message, Space, Switch, Table, Tag, Typography } from 'antd';
import { apiFetch } from '../../api';

export default function PstnAccessTab() {
  const [users, setUsers] = useState<any[]>([]);
  const [limits, setLimits] = useState<Record<string, number>>({});
  const load = async () => {
    const response = await apiFetch('/api/admin/users');
    if (!response.ok) return message.error('تعذر تحميل المستخدمين');
    const data = await response.json(); setUsers(data);
    setLimits(Object.fromEntries(data.map((u:any) => [u.id, u.pstnDailyLimit || 10])));
  };
  useEffect(() => { load(); }, []);
  const update = async (user:any, enabled:boolean) => {
    const dailyLimit = enabled ? (limits[user.id] || 10) : 0;
    const response = await apiFetch('/api/admin/users/pstn', { method:'PUT', body: JSON.stringify({ userId:user.id, enabled, dailyLimit }) });
    if (!response.ok) return message.error('فشل تحديث صلاحية الاتصال');
    message.success(enabled ? 'تم تفعيل الاتصال اليمني' : 'تم إلغاء الاتصال اليمني'); load();
  };
  return <Card title="صلاحيات الاتصال عبر DINSTAR">
    <Typography.Paragraph>لا يحصل أي حساب على رصيد الشريحة تلقائياً. حدد صلاحية وعدداً يومياً لكل مستخدم.</Typography.Paragraph>
    <Table rowKey="id" dataSource={users} columns={[
      {title:'معرّف يونس', dataIndex:'redId', render:(v:string)=><Typography.Text copyable>{v}</Typography.Text>},
      {title:'المستخدم', render:(_:any,u:any)=><>@{u.username}<br/><small>{u.displayName}</small></>},
      {title:'الحالة', dataIndex:'status', render:(v:string)=><Tag color={v==='APPROVED'?'green':'orange'}>{v}</Tag>},
      {title:'الحد اليومي', render:(_:any,u:any)=><InputNumber min={1} max={1000} value={limits[u.id] || 10} onChange={v=>setLimits({...limits,[u.id]:v || 10})} disabled={u.status!=='APPROVED'} />},
      {title:'PSTN', render:(_:any,u:any)=><Space><Switch checked={u.pstnEnabled} disabled={u.status!=='APPROVED'} onChange={v=>update(u,v)} /><span>{u.pstnEnabled ? `${u.pstnDailyLimit}/يوم` : 'معطل'}</span></Space>}
    ]} />
  </Card>;
}
