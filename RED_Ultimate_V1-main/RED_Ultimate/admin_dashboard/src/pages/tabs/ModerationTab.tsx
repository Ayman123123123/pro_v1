import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { apiFetch } from '../../api';

type Report = { id:string; reporterRedId:string; reportedRedId?:string; category:string; details?:string; status:string; createdAt:string };

export default function ModerationTab() {
  const [reports,setReports]=useState<Report[]>([]); const [loading,setLoading]=useState(false); const [error,setError]=useState('');
  const load=async()=>{ setLoading(true); setError(''); try { const r=await apiFetch('/api/admin/moderation/reports?status=OPEN'); if(!r.ok) throw new Error(`HTTP ${r.status}`); setReports(await r.json()); } catch(e:any){setError(e.message||'تعذر تحميل البلاغات');} finally{setLoading(false);} };
  useEffect(()=>{load();},[]);
  const resolve=async(id:string,status:'RESOLVED'|'DISMISSED')=>{ const r=await apiFetch(`/api/admin/moderation/reports/${id}?status=${status}`,{method:'PATCH'}); if(!r.ok){message.error(`HTTP ${r.status}`);return;} message.success(status==='RESOLVED'?'تمت معالجة البلاغ':'تم رفض البلاغ'); load(); };
  const exportCsv = () => {
    const rows = [['الفئة','المبلغ','المبلغ عنه','التفاصيل','الوقت'], ...reports.map(r=>[r.category, r.reporterRedId, r.reportedRedId||'—', (r.details||'—').replace(/,/g,'؛'), new Date(r.createdAt).toLocaleString('ar')])];
    const csv = rows.map(row=>row.map(v=>`"${String(v).replace(/"/g,'""')}"`).join(',')).join('\n');
    const blob = new Blob(['\uFEFF'+csv], {type:'text/csv;charset=utf-8;'}); const url=URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download=`younes-reports-${new Date().toISOString().slice(0,10)}.csv`; a.click(); URL.revokeObjectURL(url);
  };
  return <Card title="الثقة والسلامة" extra={<Space><Button icon={<DownloadOutlined/>} onClick={exportCsv} disabled={!reports.length}>تصدير CSV</Button><Button onClick={load}>تحديث</Button></Space>}>
    <Typography.Paragraph type="secondary">بلاغات المستخدمين الحقيقية؛ لا تُنفذ عقوبة تلقائية دون مراجعة مسؤول.</Typography.Paragraph>
    {error&&<Alert type="error" message={error} style={{marginBottom:12}}/>}
    <Table rowKey="id" loading={loading} dataSource={reports} columns={[
      {title:'الفئة',dataIndex:'category',render:(v:string)=><Tag color="red">{v}</Tag>},
      {title:'المُبلّغ',dataIndex:'reporterRedId'}, {title:'الحساب المُبلّغ عنه',dataIndex:'reportedRedId'},
      {title:'التفاصيل',dataIndex:'details',render:(v?:string)=>v||'—'},
      {title:'الوقت',dataIndex:'createdAt',render:(v:string)=>new Date(v).toLocaleString('ar')},
      {title:'الإجراء',render:(_:unknown,r:Report)=><Space><Button type="primary" onClick={()=>resolve(r.id,'RESOLVED')}>تمت المعالجة</Button><Button onClick={()=>resolve(r.id,'DISMISSED')}>رفض البلاغ</Button></Space>}
    ]}/>
  </Card>;
}
