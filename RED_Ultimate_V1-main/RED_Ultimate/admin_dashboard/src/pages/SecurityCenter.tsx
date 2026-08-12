import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Button, Modal, Input, Alert, Tag, Space, Table, message, Tabs, Typography, InputNumber, Switch } from 'antd';
import { SafetyOutlined, WarningOutlined, DeleteOutlined, LockOutlined, PhoneOutlined, KeyOutlined, AuditOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { activateKillSwitch, requestSecurityWipe, updatePstnAccess, getPstnUsers, getAuditLog, apiFetch } from '../api';

// 🔴 مدموج من SecurityTab + PstnAccessTab القديمتين — كل الميزات في صفحة واحدة موحدة — بيانات حقيقية
export default function SecurityCenter() {
  const [killModal, setKillModal] = useState(false);
  const [wipeModal, setWipeModal] = useState(false);
  const [targetUserId, setTargetUserId] = useState('');
  const [reason, setReason] = useState('');
  const [events, setEvents] = useState<any[]>([]);
  const [pstnUsers, setPstnUsers] = useState<any[]>([]);
  const [limits, setLimits] = useState<Record<string, number>>({});
  const [operational, setOperational] = useState<any>(null);

  const loadAudit = async () => {
    try { const r: any = await getAuditLog({ page: 0, size: 20 }); setEvents(Array.isArray(r?.content) ? r.content : (Array.isArray(r) ? r : [])); } catch { /* ignore */ }
  };
  const loadPstn = async () => {
    try {
      const data: any = await getPstnUsers();
      const arr: any[] = Array.isArray(data) ? data : (data?.content ?? []);
      setPstnUsers(arr);
      setLimits(Object.fromEntries(arr.map((u: any) => [u.id, u.pstnDailyLimit || 10])));
    } catch {
      message.error('تعذر تحميل صلاحيات PSTN');
    }
  };
  const loadOperational = async () => {
    try {
      const response = await apiFetch('/api/admin/operations/overview');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      setOperational(await response.json());
    } catch { message.error('تعذر تحميل مقاييس الأمان الحية'); }
  };
  useEffect(() => { loadAudit(); loadPstn(); loadOperational(); }, []);

  const handleKill = async () => {
    if (!reason.trim()) return message.error('أدخل سبب تفعيل Kill Switch');
    try { await activateKillSwitch(reason); message.success('تم تفعيل Kill Switch — تم مسح كل الأجهزة وإبطال الجلسات'); setKillModal(false); setReason(''); loadAudit(); }
    catch (e:any) { message.error(e.message||'فشل Kill Switch'); }
  };
  const handleWipe = async () => {
    if (!targetUserId.trim()) return message.error('أدخل User ID');
    try { await requestSecurityWipe(targetUserId); message.success('تم إرسال أمر المسح للجهاز'); setWipeModal(false); loadAudit(); }
    catch (e:any) { message.error(e.message||'فشل المسح'); }
  };
  const updatePstn = async (user:any, enabled:boolean) => {
    const dailyLimit = enabled ? (limits[user.id]||10) : 0;
    try { await updatePstnAccess(user.id, enabled, dailyLimit); message.success(enabled ? 'تم تفعيل الاتصال اليمني' : 'تم إلغاء الاتصال'); loadPstn(); }
    catch (e:any) { message.error(e.message||'فشل تحديث الصلاحية'); }
  };

  return (
    <Space direction="vertical" size="large" style={{width:'100%'}}>
      <div>
        <Typography.Title level={2} style={{color:'#FF4D4F', margin:0}}><SafetyOutlined /> مركز الأمان السيادي — موحد</Typography.Title>
        <Typography.Text type="secondary">Kill Switch + مسح عن بُعد + صلاحيات PSTN + سجل التدقيق — مدموج من SecurityTab/PstnAccessTab القديمتين — بيانات حقيقية</Typography.Text>
      </div>

      <Alert type="error" showIcon message="تحذير سيادي" description="Kill Switch يمسح كل الأجهزة فوراً ويلغي كل الجلسات — لا يُستخدم إلا بأمر إداري موثق. كل إجراء يُسجل في Audit." />

      <Tabs
        type="card"
        items={[
          {
            key: 'emergency',
            label: <Space><WarningOutlined /> الطوارئ</Space>,
            children: (
              <Space direction="vertical" size="middle" style={{width:'100%'}}>
                <Row gutter={[16,16]}>
                  <Col xs={24} md={12} xl={6}><Card><Statistic title="تنبيهات أمنية آخر 24 ساعة" value={operational?.moderation?.securityAlerts24h ?? 0} prefix={<SafetyOutlined />} valueStyle={{color:(operational?.moderation?.securityAlerts24h ?? 0) > 0 ? '#ff4d4f' : '#52c41a'}} /></Card></Col>
                  <Col xs={24} md={12} xl={6}><Card><Statistic title="الأجهزة الملغاة" value={operational?.devices?.revoked ?? 0} prefix={<LockOutlined />} valueStyle={{color:'#ff4d4f'}} /></Card></Col>
                  <Col xs={24} md={12} xl={6}><Card><Statistic title="جلسات التجديد النشطة" value={operational?.devices?.activeRefreshSessions ?? 0} prefix={<SafetyOutlined />} valueStyle={{color:'#1890ff'}} /></Card></Col>
                  <Col xs={24} md={12} xl={6}><Card><Statistic title="بلاغات قيد المعالجة" value={operational?.moderation?.openReports ?? 0} prefix={<ExclamationCircleOutlined />} valueStyle={{color:(operational?.moderation?.openReports ?? 0) > 0 ? '#faad14' : '#52c41a'}} /></Card></Col>
                </Row>
                <Row gutter={[16,16]}>
                  <Col span={12}>
                    <Card title="⚡ إجراءات الطوارئ" extra={<ExclamationCircleOutlined style={{color:'#ff4d4f'}} />}>
                      <Space direction="vertical" style={{width:'100%'}}>
                        <Button danger block icon={<WarningOutlined />} size="large" onClick={()=>setKillModal(true)}>🔴 KILL SWITCH — مسح كل الأجهزة</Button>
                        <Button type="primary" danger block icon={<DeleteOutlined />} onClick={()=>setWipeModal(true)}>مسح عن بُعد — جهاز واحد</Button>
                        <Typography.Text type="secondary" style={{fontSize:11}}>يتطلب مصادقة ADMIN + سبب موثق — يُسجل في audit_events</Typography.Text>
                      </Space>
                    </Card>
                  </Col>
                  <Col span={12}>
                    <Card title={<Space><AuditOutlined /> أحداث الأمان الأخيرة</Space>} extra={<Space><Button size="small" onClick={loadOperational}>المقاييس</Button><Button size="small" onClick={loadAudit}>التدقيق</Button></Space>}>
                      <Table dataSource={events} rowKey="id" size="small" pagination={{pageSize:6}} locale={{emptyText:'لا توجد أحداث'}} columns={[
                        {title:'الإجراء', dataIndex:'action', render:(v:string)=><Tag color={v?.includes('KILL')?'red':'blue'}>{v}</Tag>},
                        {title:'الهدف', dataIndex:'targetId', render:(v:string)=>v||'—'},
                        {title:'المدير', dataIndex:'actorId', render:(v:string)=>v||'SYSTEM'},
                        {title:'الوقت', dataIndex:'createdAt', render:(v:string)=>v?new Date(v).toLocaleString('ar'):'—'},
                      ]} />
                    </Card>
                  </Col>
                </Row>
                <Modal title="⚠️ تأكيد Kill Switch" open={killModal} onCancel={()=>setKillModal(false)} onOk={handleKill} okButtonProps={{danger:true}} okText="تأكيد المسح الشامل">
                  <Alert type="error" showIcon message="سيتم مسح كل الأجهزة فوراً!" />
                  <Input.TextArea style={{marginTop:16}} placeholder="سبب تفعيل Kill Switch — يُسجل في التدقيق" value={reason} onChange={e=>setReason(e.target.value)} rows={3} />
                </Modal>
                <Modal title="مسح عن بُعد — جهاز واحد" open={wipeModal} onCancel={()=>setWipeModal(false)} onOk={handleWipe} okButtonProps={{danger:true}}>
                  <Input placeholder="Target User ID (UUID أو RED ID)" value={targetUserId} onChange={e=>setTargetUserId(e.target.value)} prefix={<KeyOutlined />} />
                  <Typography.Text type="secondary" style={{fontSize:11, display:'block', marginTop:8}}>يمكن نسخه من جدول المستخدمين — RED ID</Typography.Text>
                </Modal>
              </Space>
            ),
          },
          {
            key: 'pstn',
            label: <Space><PhoneOutlined /> صلاحيات PSTN اليمني</Space>,
            children: (
              <Card title="صلاحيات الاتصال عبر DINSTAR — التحكم بالرصيد">
                <Typography.Paragraph>لا يحصل أي حساب على رصيد الشريحة تلقائياً. حدد صلاحية وعدداً يومياً لكل مستخدم معتمد — مدموج من PstnAccessTab القديمة.</Typography.Paragraph>
                <Table rowKey="id" dataSource={pstnUsers} pagination={{pageSize:10}} scroll={{x:900}} columns={[
                  {title:'معرّف يونس', dataIndex:'redId', render:(v:string)=><Typography.Text copyable>{v}</Typography.Text>},
                  {title:'المستخدم', render:(_:any,u:any)=><><b>@{u.username}</b><br/><small>{u.displayName}</small></>},
                  {title:'الحالة', dataIndex:'status', render:(v:string)=><Tag color={v==='APPROVED'?'green':'orange'}>{v}</Tag>},
                  {title:'الحد اليومي', render:(_:any,u:any)=><InputNumber min={1} max={1000} value={limits[u.id]||10} onChange={v=>setLimits({...limits,[u.id]:v||10})} disabled={u.status!=='APPROVED'} />},
                  {title:'PSTN', render:(_:any,u:any)=><Space><Switch checked={u.pstnEnabled} disabled={u.status!=='APPROVED'} onChange={v=>updatePstn(u,v)} /><span>{u.pstnEnabled?`${u.pstnDailyLimit}/يوم`:'معطل'}</span></Space>},
                ]} />
              </Card>
            ),
          },
        ]}
      />
    </Space>
  );
}
