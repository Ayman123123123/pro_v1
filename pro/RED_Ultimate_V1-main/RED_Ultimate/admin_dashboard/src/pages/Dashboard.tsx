import React, { useEffect, useState } from 'react';
import { Alert, Card, Col, Row, Spin, Statistic } from 'antd';
import { MessageOutlined, SafetyOutlined, UserOutlined, HddOutlined } from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { apiFetch } from '../api';

// Register only the primitives this dashboard uses. Importing the full ECharts bundle
// made the lazily loaded dashboard unnecessarily large on slower admin connections.
echarts.use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<any>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const load = async () => {
      try {
        const [monitor, master] = await Promise.allSettled([
          apiFetch('/api/admin/monitor/stats'),
          apiFetch('/api/master/v1/stats/realtime')
        ]);
        const merged: Record<string, unknown> = {};
        const failures: string[] = [];
        for (const [name, result] of [['monitor', monitor], ['master', master]] as const) {
          if (result.status === 'rejected') { failures.push(`${name}: network`); continue; }
          if (!result.value.ok) { failures.push(`${name}: HTTP ${result.value.status}`); continue; }
          Object.assign(merged, await result.value.json());
        }
        if (Object.keys(merged).length > 0) setStats((previous:any) => ({ ...(previous || {}), ...merged }));
        setError(failures.length ? `بعض مصادر المقاييس غير متاحة (${failures.join('، ')})` : '');
      } catch (e:any) { setError(`تعذر قراءة المقاييس: ${e?.message || 'خطأ غير معروف'}`); }
    };
    load(); const timer = setInterval(load, 5000); return () => clearInterval(timer);
  }, []);

  if (!stats && !error) return <Spin size="large" style={{display:'block',margin:'100px auto'}}/>;
  if (!stats) return <Alert type="error" message={error} showIcon/>;
  const chart = { tooltip:{}, xAxis:{type:'category',data:['المستخدمون','الرسائل 24س','ذاكرة JVM %']}, yAxis:{type:'value'}, series:[{type:'bar',data:[stats.active_users||0,stats.messages_24h||0,stats.jvm_memory_percent||0],itemStyle:{color:'#00C896'}}] };
  return <div>
    {error && <Alert type="warning" message={error} showIcon style={{marginBottom:12}}/>}
    <Row gutter={[16,16]}>
      <Col span={6}><Card><Statistic title="المستخدمون المتصلون" value={stats.active_users||0} prefix={<UserOutlined/>}/></Card></Col>
      <Col span={6}><Card><Statistic title="رسائل آخر 24 ساعة" value={stats.messages_24h||0} prefix={<MessageOutlined/>}/></Card></Col>
      <Col span={6}><Card><Statistic title="طلبات الموافقة" value={stats.pending_approvals||0} prefix={<SafetyOutlined/>}/></Card></Col>
      <Col span={6}><Card><Statistic title="ذاكرة JVM" value={stats.jvm_memory_percent||0} suffix="%" prefix={<HddOutlined/>}/></Card></Col>
    </Row>
    <Row gutter={16} style={{marginTop:16}}><Col span={16}><Card title="مقاييس حية — لا بيانات تجريبية"><ReactEChartsCore echarts={echarts} option={chart}/></Card></Col>
      <Col span={8}><Card title="صحة المنظومة"><Alert message={`PostgreSQL: ${stats.db_health||'UNKNOWN'}`} type={stats.db_health==='UP'?'success':'error'} showIcon/><Alert message={`Uptime: ${Math.round((stats.uptime_ms||0)/1000)} sec`} type="info" showIcon style={{marginTop:8}}/><Alert message={`CPU cores: ${stats.cpu_cores||0}`} type="info" showIcon style={{marginTop:8}}/></Card></Col></Row>
  </div>;
};
export default Dashboard;
