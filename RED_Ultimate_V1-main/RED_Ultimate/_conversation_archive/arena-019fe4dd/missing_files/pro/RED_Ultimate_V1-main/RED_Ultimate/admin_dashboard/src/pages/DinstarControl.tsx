import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Input, Modal, Progress, Row, Space, Table, Tag, Typography, message } from 'antd';
import { ApiOutlined, HistoryOutlined, ReloadOutlined, SafetyCertificateOutlined, SignalFilled, ToolOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

type Port = { index:number; radioType?:string; status?:string; callState?:string; signal?:number; signalRaw?:number; gprs?:string; numberMasked?:string; imsiMasked?:string; iccidMasked?:string; operator?:string };
type Discovery = { success:boolean; gatewayIp:string; model:string; status:string; portsDetected?:number; message?:string };

export default function DinstarControl() {
  const [ports,setPorts]=useState<Port[]>([]); const [discovery,setDiscovery]=useState<Discovery|null>(null); const [capabilities,setCapabilities]=useState<Record<string,unknown>>({});
  const [cdr,setCdr]=useState<any[]>([]); const [loading,setLoading]=useState(false); const [ussdPort,setUssdPort]=useState<number|null>(null); const [ussd,setUssd]=useState('');
  const json=async(r:Response)=>{const b=await r.json().catch(()=>({}));if(!r.ok)throw new Error(b?.error||b?.message||`HTTP ${r.status}`);return b;};
  const load=async()=>{setLoading(true);try{
    const [d,c,s]=await Promise.all([apiFetch('/api/admin/dinstar/discover'),apiFetch('/api/admin/dinstar/capabilities'),apiFetch('/api/admin/dinstar/status')]);
    setDiscovery(await json(d));setCapabilities(await json(c));setPorts(await json(s));
  }catch(e:any){message.error(e.message||'تعذر الاتصال بالبوابة');}finally{setLoading(false);}};
  useEffect(()=>{load();const t=setInterval(load,15000);return()=>clearInterval(t);},[]);
  const reset=(port:number)=>Modal.confirm({title:`إعادة تشغيل وحدة المنفذ ${port+1}`,content:'يقطع أي مكالمة نشطة على هذا المنفذ فقط. هل تريد المتابعة؟',okType:'danger',onOk:async()=>{try{await json(await apiFetch(`/api/admin/dinstar/ports/${port}/reset`,{method:'POST'}));message.success('تم إرسال reset موثق للوحدة');setTimeout(load,3000);}catch(e:any){message.error(e.message);}}});
  const sendUssd=async()=>{if(ussdPort==null)return;try{await json(await apiFetch(`/api/admin/dinstar/ports/${ussdPort}/ussd`,{method:'POST',body:JSON.stringify({code:ussd})}));message.success('تم إرسال USSD');setUssdPort(null);setUssd('');}catch(e:any){message.error(e.message);}};
  const loadCdr=async()=>{try{const b=await json(await apiFetch('/api/admin/dinstar/cdr'));setCdr(b.cdr||b.query||[]);}catch(e:any){message.error(e.message);}};
  return <div style={{padding:20}}>
    <Row justify="space-between" align="middle"><div><Typography.Title level={2}>DINSTAR UC2000-VE-8T</Typography.Title><Typography.Text type="secondary">جسر يونس الصوتي إلى 8 شرائح — التحكم الموثق فقط</Typography.Text></div><Button loading={loading} icon={<ReloadOutlined/>} onClick={load}>تحديث موثق</Button></Row>
    <Alert style={{margin:'14px 0'}} type={discovery?.success?'success':'warning'} showIcon message={discovery?.success?`${discovery.model} متصل على ${discovery.gatewayIp}`:(discovery?.message||'البوابة غير متصلة')} description="المكالمات تخرج حصراً عبر Backend → Asterisk → PJSIP → DINSTAR. لا تستخدم اللوحة endpoint اتصال مباشرًا غير موثق."/>
    <Card title={<><SafetyCertificateOutlined/> حدود الأمان والقدرات</>} style={{marginBottom:16}}><Descriptions size="small" column={{xs:1,md:3}}>
      <Descriptions.Item label="Voice">Asterisk/PJSIP فقط</Descriptions.Item><Descriptions.Item label="SMS/USSD API">{capabilities.ussd?'موثق':'غير متاح'}</Descriptions.Item><Descriptions.Item label="Port Info">{capabilities.portInfo?'موثق':'غير متاح'}</Descriptions.Item>
      <Descriptions.Item label="Firmware">واجهة DINSTAR الأصلية فقط</Descriptions.Item><Descriptions.Item label="Network/Firewall">واجهة الجهاز + VLAN إدارة</Descriptions.Item><Descriptions.Item label="Factory Reset"><Tag color="red">محظور من يونس</Tag></Descriptions.Item>
    </Descriptions></Card>
    <Row gutter={[12,12]}>{ports.map(port=><Col xs={24} sm={12} lg={6} key={port.index}><Card title={`SIM ${port.index+1}`} extra={<Tag color={port.status==='REGISTERED'?'green':'orange'}>{port.status||'UNKNOWN'}</Tag>}>
      <div style={{textAlign:'center'}}><SignalFilled style={{fontSize:34,color:(port.signal||0)>55?'#00C896':'#E8B84A'}}/><Progress percent={port.signal||0} strokeColor="#00C896"/><Space wrap><Tag>{port.radioType||'UNKNOWN'}</Tag><Tag color="blue">{port.callState||'UNKNOWN'}</Tag><Tag>{port.gprs||'UNKNOWN'}</Tag></Space></div>
      <Descriptions column={1} size="small" style={{marginTop:10}}><Descriptions.Item label="Number">{port.numberMasked||'غير معروف'}</Descriptions.Item><Descriptions.Item label="IMSI">{port.imsiMasked||'—'}</Descriptions.Item><Descriptions.Item label="ICCID">{port.iccidMasked||'—'}</Descriptions.Item></Descriptions>
      <Space style={{marginTop:8}}><Button size="small" icon={<ApiOutlined/>} onClick={()=>{setUssdPort(port.index);setUssd('');}}>USSD</Button><Button size="small" danger icon={<ToolOutlined/>} onClick={()=>reset(port.index)}>Reset module</Button></Space>
    </Card></Col>)}</Row>
    {!ports.length&&<Card><Typography.Text type="secondary">لا توجد بيانات منافذ. تحقق من عنوان الجهاز وكلمة API ومن أن الحاسوب/الخادم يصل إلى شبكة 192.168.11.0/24.</Typography.Text></Card>}
    <Card title={<><HistoryOutlined/> CDR من الجهاز</>} extra={<Button onClick={loadCdr}>تحميل CDR</Button>} style={{marginTop:16}}><Table size="small" rowKey={(r:any)=>`${r.port}-${r.start_date}-${r.destination_number}`} dataSource={cdr} columns={[
      {title:'المنفذ',dataIndex:'port'},{title:'الاتجاه',dataIndex:'direction'},{title:'المصدر',dataIndex:'source_number'},{title:'الوجهة',dataIndex:'destination_number'},{title:'البدء',dataIndex:'start_date'},{title:'المدة',dataIndex:'duration'}
    ]}/></Card>
    <Modal open={ussdPort!=null} title={`USSD — SIM ${(ussdPort??0)+1}`} onCancel={()=>setUssdPort(null)} onOk={sendUssd} okButtonProps={{disabled:!/^[*#0-9]{2,30}$/.test(ussd)}}><Input value={ussd} onChange={e=>setUssd(e.target.value)} placeholder="مثال *101#"/><Alert style={{marginTop:12}} type="warning" message="قد يعرض USSD الرصيد أو معلومات حساسة؛ النتيجة لا تُسجل كنص في Audit."/></Modal>
  </div>;
}
