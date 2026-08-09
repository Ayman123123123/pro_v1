import React, { useEffect, useState } from 'react';
import { Card, Col, Progress, Row, Statistic, Tag } from 'antd';
import { ApiOutlined, DatabaseFilled, SafetyCertificateFilled, ThunderboltFilled } from '@ant-design/icons';
import { apiFetch } from '../api';

const MasterOverview: React.FC = () => {
  const [stats,setStats]=useState<any>({}); const [slots,setSlots]=useState<any[]>([]);
  useEffect(()=>{ const load=async()=>{ const [s,d]=await Promise.all([apiFetch('/api/master/v1/stats/realtime'),apiFetch('/api/master/v1/hardware/dinstar/slots')]); if(s.ok)setStats(await s.json()); if(d.ok)setSlots(await d.json()); }; load(); const t=setInterval(load,5000); return()=>clearInterval(t); },[]);
  const signals=slots.map(x=>Number(x.signal||0)).filter(Number.isFinite); const signal=signals.length?Math.round(signals.reduce((a,b)=>a+b,0)/signals.length):0;
  return <div style={{padding:24}}>
    <h1>YOUNES Sovereign Master Control</h1>
    <Row gutter={[16,16]}>
      <Col span={6}><Card><Statistic title="المستخدمون المتصلون" value={stats.active_users||0} prefix={<ThunderboltFilled/>}/><Tag color="green">LIVE</Tag></Card></Col>
      <Col span={6}><Card><Statistic title="طلبات الموافقة" value={stats.pending_approvals||0} prefix={<SafetyCertificateFilled/>}/><Tag color="orange">AUTHORITY</Tag></Card></Col>
      <Col span={6}><Card><Statistic title="متوسط إشارة DINSTAR" value={signal} suffix="%" prefix={<ApiOutlined/>}/><Progress percent={signal} showInfo={false}/><Tag color={slots.length?'green':'red'}>{slots.length?'LIVE HARDWARE':'UNAVAILABLE'}</Tag></Card></Col>
      <Col span={6}><Card><Statistic title="PostgreSQL" value={stats.db_health||'UNKNOWN'} prefix={<DatabaseFilled/>}/><Tag color={stats.db_health==='UP'?'green':'red'}>REAL CHECK</Tag></Card></Col>
    </Row>
  </div>;
};
export default MasterOverview;
