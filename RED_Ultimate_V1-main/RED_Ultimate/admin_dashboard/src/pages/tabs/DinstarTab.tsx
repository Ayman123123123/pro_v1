import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Progress, Tag, Button, Switch, Space, Statistic, message, Tooltip, Badge } from 'antd';
import { MobileOutlined, SignalFilled, ReloadOutlined, CheckCircleOutlined, WarningOutlined, ApiOutlined } from '@ant-design/icons';
import { apiFetch } from '../../api';

interface PortSlot {
  index: number;
  radioType?: string;
  status?: string;
  callState?: string;
  signal?: number;
  signalRaw?: number;
  gprs?: string;
  numberMasked?: string;
  imsiMasked?: string;
  iccidMasked?: string;
  operator?: string;
}

interface Discovery {
  success: boolean;
  gatewayIp: string;
  model: string;
  status: string;
  portsDetected?: number;
  message?: string;
}

/**
 * Yemen mobile operators — CORRECTED mapping (Wikipedia + ITU E.164)
 * | Prefix | Operator                        |
 * |--------|---------------------------------|
 * | 71     | سبأفون (Sabafon)               |
 * | 73     | يو / YOU (formerly MTN Yemen)   |
 * | 77, 78 | يمن موبايل (Yemen Mobile)      |
 * | 70     | واي (Y Telecom)                |
 * | 10     | يمن 4G (Yemen 4G)              |
 */
const YEMEN_OPERATORS: Record<string, { arabic: string; english: string; color: string }> = {
  'Sabafon':   { arabic: 'سبأفون',     english: 'Sabafon',             color: '#E53935' },
  'MTN':       { arabic: 'يو',          english: 'YOU (Yemeni Omani)', color: '#FFB300' },
  'YOU':       { arabic: 'يو',          english: 'YOU (Yemeni Omani)', color: '#FFB300' },
  'YemenMobile':{arabic: 'يمن موبايل',  english: 'Yemen Mobile',       color: '#43A047' },
  'Yemen Mobile':{arabic:'يمن موبايل',  english: 'Yemen Mobile',       color: '#43A047' },
  'YTelecom':  { arabic: 'واي',         english: 'Y Telecom',          color: '#1E88E5' },
  'Y Telecom': { arabic: 'واي',         english: 'Y Telecom',          color: '#1E88E5' },
  'HiTel':     { arabic: 'واي',         english: 'Y Telecom',          color: '#1E88E5' },
  'Yemen4G':   { arabic: 'يمن 4G',      english: 'Yemen 4G',           color: '#7C4DFF' },
  'Yemen 4G':  { arabic: 'يمن 4G',      english: 'Yemen 4G',           color: '#7C4DFF' },
};

/** Resolve operator name: maps old/wrong names to correct Yemen operator */
function resolveOperator(raw?: string): { arabic: string; english: string; color: string } {
  if (!raw || raw === 'UNKNOWN' || raw === 'غير معروف') return { arabic: 'غير معروف', english: 'Unknown', color: '#757575' };
  if (YEMEN_OPERATORS[raw]) return YEMEN_OPERATORS[raw];
  for (const key of Object.keys(YEMEN_OPERATORS)) {
    if (raw.includes(key)) return YEMEN_OPERATORS[key];
  }
  return { arabic: raw, english: raw, color: '#757575' };
}

const DinstarTab: React.FC = () => {
    const [slots, setSlots] = useState<PortSlot[]>([]);
    const [discovery, setDiscovery] = useState<Discovery | null>(null);
    const [loading, setLoading] = useState(false);
    const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

    const refresh = async () => {
        setLoading(true);
        try {
            const [dResp, sResp] = await Promise.allSettled([
                apiFetch('/api/admin/dinstar/discover'),
                apiFetch('/api/admin/dinstar/status')
            ]);
            if (dResp.status === 'fulfilled' && dResp.value.ok) setDiscovery(await dResp.value.json());
            if (sResp.status === 'fulfilled' && sResp.value.ok) setSlots(await sResp.value.json());
            setLastRefresh(new Date());
        } catch { message.error('تعذر الاتصال بالبوابة'); }
        finally { setLoading(false); }
    };

    useEffect(() => { refresh(); const it = setInterval(refresh, 5000); return () => clearInterval(it); }, []);

    const avgSignal = slots.length > 0 ? Math.round(slots.reduce((s, p) => s + (p.signal || 0), 0) / slots.length) : 0;
    const registeredCount = slots.filter(s => s.status === 'REGISTERED').length;
    const activeCallCount = slots.filter(s => s.callState === 'ACTIVE' || s.callState === 'DIALING').length;

    const signalColor = (v: number) => v > 60 ? '#52c41a' : v > 30 ? '#faad14' : '#f5222d';
    const statusColor = (s?: string) => s === 'REGISTERED' ? 'green' : s === 'IDLE' ? 'blue' : 'orange';

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
                <div>
                    <h2 style={{ color: '#fff' }}>🔴 DINSTAR UC2000-VE-8G (GSM Gateway)</h2>
                    <Space>
                        <Tag color={discovery?.success ? 'green' : 'red'}>{discovery?.success ? 'متصل' : 'غير متصل'}</Tag>
                        {discovery?.gatewayIp && <Tag>IP: {discovery.gatewayIp}</Tag>}
                        <Tag>آخر تحديث: {lastRefresh.toLocaleTimeString('ar')}</Tag>
                    </Space>
                </div>
                <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>تحديث</Button>
            </div>

            {/* Summary Stats */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col span={6}>
                    <Card>
                        <Statistic title="المنافذ المسجّلة" value={registeredCount} suffix={`/ ${slots.length}`}
                            prefix={<CheckCircleOutlined />} valueStyle={{ color: '#52c41a' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="المكالمات النشطة" value={activeCallCount}
                            prefix={<ApiOutlined />} valueStyle={{ color: activeCallCount > 0 ? '#722ed1' : '#52c41a' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="متوسط الإشارة" value={avgSignal} suffix="%"
                            prefix={<SignalFilled />} valueStyle={{ color: signalColor(avgSignal) }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="نوع الشبكة" value="GSM"
                            prefix={<MobileOutlined />} valueStyle={{ color: '#1890ff' }} />
                    </Card>
                </Col>
            </Row>

            {/* Port Grid */}
            <Row gutter={[16, 16]}>
                {slots.map(slot => {
                    const op = resolveOperator(slot.operator);
                    return (
                    <Col span={6} key={slot.index}>
                        <Card style={{ background: '#1f1f1f', border: '1px solid #333' }}
                            extra={<Tag color={statusColor(slot.status)}>{slot.status || 'UNKNOWN'}</Tag>}>
                            <Space align="start">
                                <Badge status={slot.status === 'REGISTERED' ? 'success' : 'warning'}>
                                    <MobileOutlined style={{ fontSize: 32, color: signalColor(slot.signal || 0) }} />
                                </Badge>
                                <div>
                                    <b style={{ color: '#fff' }}>SIM {slot.index + 1}</b>
                                    <div style={{ fontSize: 11, color: op.color, fontWeight: 600 }}>{op.arabic}</div>
                                    <div style={{ fontSize: 11, color: slot.callState === 'ACTIVE' ? '#722ed1' : '#888' }}>
                                        {slot.callState || 'IDLE'}
                                    </div>
                                </div>
                            </Space>
                            <div style={{ marginTop: 16 }}>
                                <Tooltip title={`Raw: ${slot.signalRaw || 0}/31`}>
                                    <div style={{ color: '#aaa', fontSize: 12 }}>Signal: {slot.signal}%</div>
                                </Tooltip>
                                <Progress percent={slot.signal || 0} showInfo={false}
                                    strokeColor={signalColor(slot.signal || 0)} size="small" />
                            </div>
                            <div style={{ marginTop: 8, fontSize: 11, color: '#666' }}>
                                <div>{slot.numberMasked || '—'}</div>
                                <div style={{ color: op.color, fontWeight: 600 }}>{op.arabic}</div>
                                <div>GPRS: {slot.gprs || 'UNKNOWN'}</div>
                            </div>
                        </Card>
                    </Col>
                    );
                })}
            </Row>

            {!slots.length && (
                <Card style={{ marginTop: 16 }}>
                    <Space direction="vertical" align="center" style={{ width: '100%' }}>
                        <WarningOutlined style={{ fontSize: 48, color: '#faad14' }} />
                        <div style={{ color: '#aaa' }}>لا توجد بيانات منافذ. تحقق من:</div>
                        <ul style={{ color: '#888', textAlign: 'right' }}>
                            <li>عنوان IP ومنفذ API (443/HTTPS)</li>
                            <li>كلمة مرور API (admin:admin)</li>
                            <li>تفعيل "New Version API" من واجهة الجهاز</li>
                            <li>وصول الشبكة إلى 192.168.11.0/24</li>
                        </ul>
                    </Space>
                </Card>
            )}
        </div>
    );
};

export default DinstarTab;
