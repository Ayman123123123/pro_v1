import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Form, Input, InputNumber, Modal, Row, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { ApiOutlined, CaretRightOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert } from 'antd';
import { apiFetch } from '../api';

type Config = {
  mode: 'OFF' | 'LEARN' | 'MAINTAIN';
  windowStartMinute: number; windowEndMinute: number;
  minDurationSeconds: number; maxDurationSeconds: number;
  minIntervalMinutes: number; maxIntervalMinutes: number;
  dailyCapPerPort: number; enabledPorts?: string;
  // SMS comprehensive
  smsMode?: 'OFF' | 'LEARN' | 'MAINTAIN';
  smsDailyCapPerPort?: number; smsMinIntervalMinutes?: number; smsMaxIntervalMinutes?: number;
  smsTemplate?: string; autoLearnFromCdr?: boolean; autoLearnFromInbound?: boolean;
  poolSize?: number; poolActiveSize?: number; poolTotalSize?: number;
  todayTotal?: number; todayFailed?: number;
  nextEligibleAt?: string | null; zone?: string;
};
type PoolRow = { id: string; number: string; label?: string | null; source: string; active: boolean; added_at: string; last_used_at?: string | null; success_count?: number; fail_count?: number; notes?: string | null };
type CallRow = { id: string; port?: number | null; number: string; mode: string; status: string; duration_seconds?: number | null; direction?: string; started_at: string };

const minutesToHHMM = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
const hhmmToMinutes = (s: string) => { const [h, m] = s.split(':').map(Number); return (h || 0) * 60 + (m || 0); };
const jsonOrThrow = async (r: Response) => { const b = await r.json().catch(() => ({})); if (!r.ok) throw new Error(b?.error || b?.message || `HTTP ${r.status}`); return b; };

/** 🧠 Human Behavior → Phone Number Learning — Call mode */
export default function NumberLearningCard() {
  const [cfg, setCfg] = useState<Config | null>(null);
  const [pool, setPool] = useState<PoolRow[]>([]);
  const [calls, setCalls] = useState<CallRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [addText, setAddText] = useState('');
  const [probe, setProbe] = useState<any>(null);

  const json = useCallback(async (r: Response) => jsonOrThrow(r), []);

  const loadAll = useCallback(async () => {
    setBusy(true);
    try {
      const [c, p, k] = await Promise.all([
        apiFetch('/api/admin/dinstar/human-behavior/number-learning'),
        apiFetch('/api/admin/dinstar/human-behavior/number-learning/pool'),
        apiFetch('/api/admin/dinstar/human-behavior/number-learning/calls?limit=30'),
      ]);
      setCfg(await json(c)); setPool(await json(p)); setCalls(await json(k));
    } catch (e: any) { message.error(e.message || 'فشل تحميل Number Learning'); }
    finally { setBusy(false); }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const saveConfig = async (patch: Partial<Config>) => {
    try {
      const merged = { ...(cfg || {} as Config), ...patch };
      if (merged.maxDurationSeconds < merged.minDurationSeconds) return message.error('أقصى مدة يجب أن تكون ≥ أدنى مدة');
      if (merged.maxIntervalMinutes < merged.minIntervalMinutes) return message.error('أقصى فاصل يجب أن يكون ≥ أدنى فاصل');
      const updated = await json(await apiFetch('/api/admin/dinstar/human-behavior/number-learning/config', { method: 'PUT', body: JSON.stringify(patch) }));
      setCfg(updated); message.success('تم حفظ التكوين');
    } catch (e: any) { message.error(e.message); }
  };

  const addNumbers = async () => {
    const numbers = addText.split(/[\n,،]+/).map(s => s.trim()).filter(Boolean).map(n => ({ number: n }));
    if (!numbers.length) return;
    try {
      const res = await json(await apiFetch('/api/admin/dinstar/human-behavior/number-learning/pool', { method: 'POST', body: JSON.stringify({ numbers }) }));
      message.success(`أُضيف ${res.inserted} وتجاهُل ${res.skipped}`);
      setAddOpen(false); setAddText(''); loadAll();
    } catch (e: any) { message.error(e.message); }
  };

  const triggerNow = async () => {
    try {
      const res = await json(await apiFetch('/api/admin/dinstar/human-behavior/number-learning/trigger', { method: 'POST', body: JSON.stringify({}) }));
      if (res.status === 'CAPPED') message.warning(`بلغ السقف اليومي (${res.cap})`);
      else message.success(`مكالمة تعلّم انطلقت على المنفذ ${res.port} لمدة ${res.durationSeconds}s`);
      setTimeout(loadAll, 1500);
    } catch (e: any) { message.error(e.message); }
  };

  const runProbe = async () => {
    try { setProbe(await json(await apiFetch('/api/admin/dinstar/human-behavior/probe'))); }
    catch (e: any) { message.error(e.message); }
  };

  const [poolQuery, setPoolQuery] = useState('');
  const filteredPool = pool.filter(r => !poolQuery || r.number.includes(poolQuery) || (r.label || '').toLowerCase().includes(poolQuery.toLowerCase()));

  const exportCallsCsv = () => {
    const header = 'started_at,port,number,mode,direction,duration,status\n';
    const rows = calls.map(c => `${c.started_at},${c.port ?? ''},${c.number},${c.mode},${c.direction || 'OUTBOUND'},${c.duration_seconds ?? ''},${c.status}`).join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `number-learning-calls-${new Date().toISOString().slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(url);
  };

  const modeTag = cfg?.mode === 'OFF'
    ? <Tag color="default">OFF</Tag>
    : <Tag color={cfg?.mode === 'LEARN' ? 'processing' : 'success'}>{cfg?.mode}</Tag>;
  const smsTag = (cfg?.smsMode || 'OFF') === 'OFF'
    ? <Tag color="default">SMS OFF</Tag>
    : <Tag color={cfg?.smsMode === 'LEARN' ? 'processing' : 'success'}>SMS {cfg?.smsMode}</Tag>;

  return (
    <Card title={<Space>🧠 Human Behavior — Phone Number Learning {modeTag} {smsTag}</Space>}
          extra={<Space>
            <Button size="small" icon={<ReloadOutlined />} onClick={loadAll} loading={busy}>تحديث</Button>
            <Button size="small" icon={<CaretRightOutlined />} onClick={triggerNow} disabled={!cfg || cfg.mode === 'OFF'}>تشغيل الآن</Button>
            <Button size="small" icon={<ApiOutlined />} onClick={runProbe}>فحص API البوابة</Button>
          </Space>}>
      {cfg && (
        <>
          <Row gutter={[12, 12]}>
            <Col span={6}>
              <Form.Item label="وضع التعلّم" style={{ marginBottom: 8 }}>
                <Select value={cfg.mode} onChange={(v: string) => saveConfig({ mode: v as Config['mode'] })}
                        options={[{ value: 'OFF', label: 'OFF — متوقف' }, { value: 'LEARN', label: 'LEARN — بناء سلوك جديد' }, { value: 'MAINTAIN', label: 'MAINTAIN — صيانة سلوك قائم' }]} />
              </Form.Item>
              <Typography.Text type="secondary">المجمّع: {cfg.poolSize ?? pool.length} رقم · اليوم: {cfg.todayTotal ?? 0} مكالمة ({cfg.todayFailed ?? 0} فاشلة)</Typography.Text>
            </Col>
            <Col span={4}>
              <Form.Item label="بداية النافذة"><Input type="time" value={minutesToHHMM(cfg.windowStartMinute)} onChange={e => e.target.value && saveConfig({ windowStartMinute: hhmmToMinutes(e.target.value) })} /></Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item label="نهاية النافذة"><Input type="time" value={minutesToHHMM(cfg.windowEndMinute)} onChange={e => e.target.value && saveConfig({ windowEndMinute: hhmmToMinutes(e.target.value) })} /></Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label="مدة المكالمة (ث)">
                <Space.Compact>
                  <InputNumber min={1} max={600} value={cfg.minDurationSeconds} onChange={(v: number | null) => v && saveConfig({ minDurationSeconds: v })} />
                  <InputNumber min={1} max={900} value={cfg.maxDurationSeconds} onChange={(v: number | null) => v && saveConfig({ maxDurationSeconds: v })} />
                </Space.Compact>
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item label="الفاصل بين المكالمات (د)">
                <Space.Compact>
                  <InputNumber min={1} max={1440} value={cfg.minIntervalMinutes} onChange={(v: number | null) => v && saveConfig({ minIntervalMinutes: v })} />
                  <InputNumber min={1} max={1440} value={cfg.maxIntervalMinutes} onChange={(v: number | null) => v && saveConfig({ maxIntervalMinutes: v })} />
                </Space.Compact>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={[12, 12]}>
            <Col span={6}>
              <Form.Item label="سقف يومي/منفذ">
                <InputNumber min={1} max={100} value={cfg.dailyCapPerPort} onChange={(v: number | null) => v && saveConfig({ dailyCapPerPort: v })} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="المنافذ المفعّلة (CSV فارغ=الكل، 0-15)">
                <Input placeholder="0,2,5,11" defaultValue={cfg.enabledPorts} onBlur={e => saveConfig({ enabledPorts: e.target.value.trim() })} />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item label="التوقيت">
                <Space><Tag>{cfg.zone}</Tag>{cfg.nextEligibleAt ? <Tag color="blue">الأهلية القادمة: {new Date(cfg.nextEligibleAt).toLocaleTimeString()}</Tag> : <Tag>جاهز الآن</Tag>}</Space>
              </Form.Item>
            </Col>
          </Row>

          {/* SMS Call mode — comprehensive */}
          <Card size="small" type="inner" title="📨 وضع SMS" style={{ marginBottom: 12 }}>
            <Row gutter={[12, 12]}>
              <Col span={6}>
                <Form.Item label="وضع SMS" style={{ marginBottom: 8 }}>
                  <Select value={cfg.smsMode || 'OFF'} onChange={(v: string) => saveConfig({ smsMode: v as Config['smsMode'] })}
                          options={[{ value: 'OFF', label: 'OFF' }, { value: 'LEARN', label: 'LEARN' }, { value: 'MAINTAIN', label: 'MAINTAIN' }]} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item label="سقف SMS/منفذ"><InputNumber min={1} max={50} value={cfg.smsDailyCapPerPort ?? 4} onChange={(v: number | null) => v && saveConfig({ smsDailyCapPerPort: v })} style={{ width: '100%' }} /></Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="فاصل SMS (د)">
                  <Space.Compact>
                    <InputNumber min={1} max={1440} value={cfg.smsMinIntervalMinutes ?? 60} onChange={(v: number | null) => v && saveConfig({ smsMinIntervalMinutes: v })} />
                    <InputNumber min={1} max={1440} value={cfg.smsMaxIntervalMinutes ?? 240} onChange={(v: number | null) => v && saveConfig({ smsMaxIntervalMinutes: v })} />
                  </Space.Compact>
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item label="قالب SMS"><Input value={cfg.smsTemplate || 'مرحبا — رسالة تعلم'} onChange={e => saveConfig({ smsTemplate: e.target.value })} maxLength={160} /></Form.Item>
              </Col>
            </Row>
            <Space>
              <span>تعلّم تلقائي:</span>
              <span>من CDR</span><Switch checked={!!cfg.autoLearnFromCdr} onChange={v => saveConfig({ autoLearnFromCdr: v })} />
              <span>من الوارد</span><Switch checked={cfg.autoLearnFromInbound ?? true} onChange={v => saveConfig({ autoLearnFromInbound: v })} />
            </Space>
          </Card>

          <Table rowKey="id" size="small" dataSource={filteredPool} pagination={{ pageSize: 5 }}
                 title={() => (
                   <Space wrap>
                     <span>مجمّع الأرقام المتعلَّمة ({cfg.poolActiveSize ?? pool.length}/{cfg.poolTotalSize ?? pool.length})</span>
                     <Input.Search size="small" placeholder="بحث رقم/تسمية" value={poolQuery} onChange={e => setPoolQuery(e.target.value)} style={{ width: 180 }} allowClear />
                     <Button size="small" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>إضافة أرقام</Button>
                   </Space>)}
                 columns={[
                   { title: 'الرقم', dataIndex: 'number', render: (v: string) => <Typography.Text copyable>{v}</Typography.Text> },
                   { title: 'المصدر', dataIndex: 'source', render: (v: string) => <Tag>{v}</Tag> },
                   { title: 'آخر استخدام', dataIndex: 'last_used_at', render: (v: string | null) => v ? new Date(v).toLocaleDateString() : '—' },
                   { title: '✓/✗', render: (_: unknown, r: PoolRow) => <span>{r.success_count ?? 0}/{r.fail_count ?? 0}</span> },
                   { title: 'نشط', dataIndex: 'active',
                     render: (_: boolean, row: PoolRow) => (
                       <Switch checked={row.active} onChange={async (on: boolean) => {
                         try { await json(await apiFetch(`/api/admin/dinstar/human-behavior/number-learning/pool/${row.id}`, { method: 'PATCH', body: JSON.stringify({ active: on }) })); loadAll(); }
                         catch (e: any) { message.error(e.message); }
                       }} />) },
                   { title: '', dataIndex: 'id',
                     render: (id: string) => (
                       <Button size="small" danger type="text" onClick={async () => {
                         try { await apiFetch(`/api/admin/dinstar/human-behavior/number-learning/pool/${id}`, { method: 'DELETE' }); loadAll(); }
                         catch (e: any) { message.error(e.message); }
                       }}>حذف</Button>) },
                 ]} />

          <Table style={{ marginTop: 12 }} rowKey="id" size="small" dataSource={calls}
                 title={() => <Space>آخر مكالمات التعلّم <Button size="small" onClick={exportCallsCsv}>تصدير CSV</Button></Space>}
                 columns={[
                   { title: 'الوقت', dataIndex: 'started_at', render: (v: string) => new Date(v).toLocaleString() },
                   { title: 'المنفذ', dataIndex: 'port', render: (v: number | null) => v == null ? '—' : v + 1 },
                   { title: 'الرقم', dataIndex: 'number' },
                   { title: 'الاتجاه', dataIndex: 'direction', render: (v: string) => <Tag>{v || 'OUTBOUND'}</Tag> },
                   { title: 'المدة', dataIndex: 'duration_seconds', render: (v: number | null) => v == null ? '—' : `${v}s` },
                   { title: 'الحالة', dataIndex: 'status', render: (v: string) => <Tag color={v === 'FAILED' ? 'red' : v === 'ORIGINATED' ? 'blue' : 'gold'}>{v}</Tag> },
                 ]} />

          {probe && (
            <Alert type={Object.keys(probe.reachable || {}).length ? 'info' : 'warning'}
                   style={{ marginTop: 12 }}
                   message={`فحص API البوابة (${probe.host})`}
                   description={<pre style={{ margin: 0, fontSize: 11 }}>{JSON.stringify(probe.details ?? probe, null, 2)}</pre>} />
          )}

          <Modal open={addOpen} onCancel={() => setAddOpen(false)} onOk={addNumbers} okText="إضافة" cancelText="إلغاء"
                 title="إضافة أرقام إلى المجمّع">
            <Typography.Paragraph type="secondary">رقم في كل سطر (يمني محلي أو دولي) — حتى 500</Typography.Paragraph>
            <Input.TextArea rows={8} value={addText} onChange={e => setAddText(e.target.value)}
                            placeholder={'777123456\n0096771234567'} />
          </Modal>
        </>
      )}
    </Card>
  );
}

