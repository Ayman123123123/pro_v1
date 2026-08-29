import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Descriptions, Divider, Empty, Form, Input,
  InputNumber, Modal, Progress, Row, Select, Space, Statistic, Table, Tag,
  Tooltip, Typography, message,
} from 'antd';
import {
  ApiOutlined, DeleteOutlined, DisconnectOutlined, HistoryOutlined, MessageOutlined,
  PlusOutlined, RadarChartOutlined, ReloadOutlined, SafetyCertificateOutlined, SignalFilled,
  ToolOutlined, PhoneOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';
import { usePolling } from '../hooks/usePolling';
import NumberLearningCard from './NumberLearningCard';
import SmsInbox from '../components/SmsInbox';
import CallHistory from './CallHistory';
import WebRtcDialer from '../components/WebRtcDialer';

/**
 * صفحة بوابات DINSTAR — أسطول UC2000-VE.
 *
 * تعرض عدة أجهزة معًا وتفصل بوضوح بين ثلاث حالات كانت مخلوطة سابقًا:
 * «مسجّلة على الشبكة» و«صالحة لحمل مكالمة» و«لا يوجد قياس إشارة».
 */

type Port = {
  index: number; radioType?: string; status?: string; callState?: string;
  signal?: number | null; signalRaw?: number | null; signalDbm?: number | null;
  signalUsable?: boolean; signalLabel?: string; gprs?: string;
  numberMasked?: string; imsiMasked?: string; iccidMasked?: string; operator?: string;
};
type Gateway = {
  id: string; name: string; model: string; host: string; scheme: string; apiPort: number;
  portCount: number; enabled: boolean; healthState: string; routingPriority: number;
  pjsipEndpoint?: string; siteLabel?: string; serialNumber?: string;
  firmwareVersion?: string; macAddress?: string | null; consecutiveFailures?: number;
};
type FleetPorts = {
  gateways: { gateway: Gateway; ports: Port[]; error?: string | null }[];
  totals: { gateways: number; online: number; ports: number; registered: number; usable: number };
};
/**
 * حدود الرسائل من وثيقة Dinstar HTTP API v1.1 الرسمية — مطابقة لـ
 * DinstarHardwareService.MAX_SMS_RECIPIENTS / MAX_SMS_TEXT_BYTES.
 * الرقم 32 الوارد في الوثيقة يخصّ query_sms_result وحده لا الإرسال.
 */
const SMS_MAX_RECIPIENTS = 128;
const SMS_MAX_TEXT_BYTES = 1500;

/**
 * هل حالة المنفذ تعني «مسجّل على الشبكة»؟
 * القيمة الخام من الجهاز تأتي أحيانًا REGISTER_OK (UC2000 عبر
 * get_port_info) وأحيانًا REGISTERED/Mobile Registered — كلها سواء.
 */
function isRegistered(status?: string): boolean {
  return status === 'REGISTERED' || status === 'REGISTER_OK' || status === 'Mobile Registered';
}

type ModelInfo = {
  model: string; portCount: number; simSlots: number; supportsVolte: boolean;
  radioCapability: string; codecs: string[];
};
/**
 * نتيجة فحص عنوان قبل تسجيله.
 * `confidence` مجموع إشارات مستقلة (0..100) لا مجرد «ردّ/لم يردّ»:
 * ضمّ جهاز غير مقصود إلى الأسطول يعني ابتلاعه مكالمات حقيقية صامتًا.
 */
type ProbeResult = {
  reachable: boolean; host?: string; model?: string; portCount?: number;
  serialNumber?: string | null; firmwareVersion?: string | null;
  macAddress?: string | null; registeredPorts?: number;
  confidence: number; signals: string[]; adoptable?: boolean; message?: string;
};

/** مشغلو اليمن — ألوان رسمية راقية بلا أخضر ساطع */
const YEMEN_OP: Record<string, { ar: string; clr: string }> = {
  Sabafon: { ar: 'سبأفون', clr: '#B91C1C' },      // أحمر عقيق
  MTN: { ar: 'يو', clr: '#B78A2E' },              // ذهبي مطفي
  YOU: { ar: 'يو', clr: '#B78A2E' },
  YemenMobile: { ar: 'يمن موبايل', clr: '#1E3A8A' }, // كحلي ملكي
  'Yemen Mobile': { ar: 'يمن موبايل', clr: '#1E3A8A' },
  YTelecom: { ar: 'واي', clr: '#6B21A8' },       // بنفسجي ملكي
  'Y Telecom': { ar: 'واي', clr: '#6B21A8' },
  HiTel: { ar: 'واي', clr: '#6B21A8' },
};
const resolveOp = (o?: string) => {
  if (!o || o === 'UNKNOWN') return { ar: 'غير معروف', clr: '#757575' };
  if (YEMEN_OP[o]) return YEMEN_OP[o];
  const hit = Object.keys(YEMEN_OP).find((k) => o.includes(k));
  return hit ? YEMEN_OP[hit] : { ar: o, clr: '#757575' };
};

/** ألوان تصنيف الإشارة — لوحة رسمية راقية بلا أخضر (ذهبي/كحلي/أزرق ملكي) */
const SIGNAL_LABEL: Record<string, { ar: string; clr: string }> = {
  EXCELLENT: { ar: 'ممتازة', clr: '#B78A2E' }, // ذهبي مطفي — فاخر
  GOOD: { ar: 'جيدة', clr: '#1976D2' },       // أزرق ملكي
  FAIR: { ar: 'مقبولة', clr: '#8A6D3B' },     // برونزي
  WEAK: { ar: 'ضعيفة', clr: '#C0842B' },     // عنبري
  UNUSABLE: { ar: 'غير كافية', clr: '#B91C1C' }, // أحمر عميق
  NO_SIGNAL: { ar: 'لا يوجد قياس', clr: '#6B7D8F' },
  OUT_OF_RANGE: { ar: 'قراءة شاذة', clr: '#6B7D8F' },
};

const HEALTH: Record<string, { ar: string; badge: 'success' | 'warning' | 'error' | 'default' }> = {
  ONLINE: { ar: 'متصلة', badge: 'success' },
  DEGRADED: { ar: 'متذبذبة', badge: 'warning' },
  OFFLINE: { ar: 'ساقطة', badge: 'error' },
  UNKNOWN: { ar: 'غير معروفة', badge: 'default' },
};

export default function DinstarControl() {
  const [fleet, setFleet] = useState<Gateway[]>([]);
  const [fleetPorts, setFleetPorts] = useState<FleetPorts | null>(null);
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [capabilities, setCapabilities] = useState<Record<string, unknown>>({});
  const [cdr, setCdr] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [ussdTarget, setUssdTarget] = useState<{gatewayId: string, port: number} | null>(null);
  const [ussd, setUssd] = useState('');
  const [dialPort, setDialPort] = useState<number | null>(null);
  const [dialHost, setDialHost] = useState('');
  const [dialNumber, setDialNumber] = useState('');
  const [dialBusy, setDialBusy] = useState(false);
  const [dialResult, setDialResult] = useState<any>(null);
  const [smsTo, setSmsTo] = useState('');
  const [smsGateway, setSmsGateway] = useState<string | undefined>(undefined);
  /**
   * منفذ الإرسال — أي شريحة يخرج منها SMS.
   *
   * الواجهة كانت ترسل `gatewayHost` وحده بلا `port`، فالجهاز يختار المنفذ
   * الأول المتاح دائمًا. النتيجة أن كل الرسائل تخرج من شريحة واحدة: رصيدها
   * وحدها يُستهلك، والمستلم يرى رقمًا واحدًا لا الرقم المقصود. الباكإند
   * يقبل `port` للأدمن أصلًا (`effectivePorts = portList`) — الواجهة فقط
   * لم تكن ترسله.
   *
   * `undefined` = اترك الاختيار للجهاز (السلوك القديم، صريح الآن).
   */
  const [smsPort, setSmsPort] = useState<number | undefined>(undefined);
  const [smsText, setSmsText] = useState('');
  const [smsSending, setSmsSending] = useState(false);
  const [smsInbox, setSmsInbox] = useState<any[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [routeOpen, setRouteOpen] = useState(false);
  const [routeResult, setRouteResult] = useState<any>(null);
  const [dialerOpen, setDialerOpen] = useState(false);
  const [probing, setProbing] = useState(false);
  const [probeResult, setProbeResult] = useState<ProbeResult | null>(null);
  const [form] = Form.useForm();
  const [routeForm] = Form.useForm();

  const json = async (r: Response) => {
    const b = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(b?.error || b?.message || `HTTP ${r.status}`);
    return b;
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [f, p, m, c] = await Promise.all([
        apiFetch('/api/admin/dinstar/fleet'),
        apiFetch('/api/admin/dinstar/fleet/ports'),
        apiFetch('/api/admin/dinstar/fleet/models'),
        apiFetch('/api/admin/dinstar/capabilities'),
      ]);
      setFleet(await json(f));
      setFleetPorts(await json(p));
      setModels(await json(m));
      setCapabilities(await json(c));
    } catch (e: any) {
      message.error(e.message || 'تعذر الاتصال بأسطول البوابات');
    } finally {
      setLoading(false);
    }
  }, []);

  // استطلاع كل 15 ثانية يتوقف عند إخفاء التبويب — كل دورة تستعلم أجهزة حقيقية
  usePolling(load, 15000);

  const totals = fleetPorts?.totals;

  /** المنافذ المسجّلة لكنها بلا إشارة صالحة — العطل الصامت الذي كان مخفيًا. */
  const silentlyDead = useMemo(() => {
    if (!fleetPorts) return [];
    return fleetPorts.gateways.flatMap(({ gateway, ports }) =>
      ports
        .filter((p) => isRegistered(p.status) && !p.signalUsable)
        .map((p) => ({ host: gateway.host, index: p.index, raw: p.signalRaw })),
    );
  }, [fleetPorts]);

  const resetPort = (gatewayHost: string, port: number) =>
    Modal.confirm({
      title: `إعادة تشغيل وحدة المنفذ ${port + 1}`,
      content: `على البوابة ${gatewayHost}. يقطع أي مكالمة نشطة على هذا المنفذ فقط.`,
      okType: 'danger',
      onOk: async () => {
        try {
          await json(await apiFetch(`/api/admin/dinstar/ports/${port}/reset`, { method: 'POST' }));
          message.success('أُرسل أمر reset موثق للوحدة');
          setTimeout(load, 3000);
        } catch (e: any) { message.error(e.message); }
      },
    });

    const [ussdResult, setUssdResult] = useState<any>(null);
  const [ussdLoading, setUssdLoading] = useState(false);

  const sendUssd = async () => {
    if (ussdTarget == null || !ussd) return;
    setUssdLoading(true);
    setUssdResult(null);
    try {
      // نقطة النهاية الحقيقية: POST على منفذ بعينه. المسار السابق
      // (/ussd/balance?gatewayId=…) لا وجود له في DinstarController.
      // gatewayId يُرسل لأن فهرس المنفذ وحده غامض في أسطول متعدد البوابات.
      const res = await json(await apiFetch(`/api/admin/dinstar/ports/${ussdTarget.port}/ussd`, {
        method: 'POST',
        body: JSON.stringify({ code: ussd, gatewayId: ussdTarget.gatewayId }),
      }));
      setUssdResult(res.response_text || res.reply || res.error || JSON.stringify(res));
      message.success('تم جلب الرد بنجاح');
    } catch (e: any) { message.error(e.message); }
    finally { setUssdLoading(false); }
  };

  const openDial = (port: number, host: string) => {
    setDialPort(port); setDialHost(host); setDialNumber(''); setDialResult(null);
  };

  /**
   * مكالمة إدارية حرة: أي منفذ، أي بوابة، أي رقم متصل من الأسطول.
   * POST /api/admin/dinstar/calls — لا حد يومي، و callerNumber مقيّد
   * بأرقام الأسطول (anti-spoofing). يبقى /api/pstn/calls للمستخدم العادي
   * محبوسًا على شريحته 1:1.
   */
  const dialPortCall = async () => {
    if (dialPort == null) return;
    const number = dialNumber.replace(/\D/g, '');
    if (!/^[0-9]{6,15}$/.test(number)) {
      message.error('أدخل رقمًا صحيحًا (أرقام فقط)');
      return;
    }
    setDialBusy(true);
    try {
      const b = await json(await apiFetch('/api/admin/dinstar/calls', {
        method: 'POST',
        body: JSON.stringify({
          number,
          gatewayHost: dialHost || undefined,
          portIndex: dialPort,
          // callerNumber: اتركه فارغًا ليستخدم رقم المنفذ المختار تلقائيًا
        }),
      }));
      setDialResult(b);
      const portOut = (b.port ?? b.slot ?? dialPort) as number;
      message.success(`تم إطلاق المكالمة على المنفذ ${portOut + 1} — ${b.status || 'DIALING'}`);
    } catch (e: any) { message.error(e.message); setDialResult(null); }
    finally { setDialBusy(false); }
  };

  /**
   * سجل المكالمات — من قاعدة البيانات لا من ذاكرة الجهاز.
   *
   * `/api/admin/dinstar/cdr` يستعلم البوابة مباشرةً: ذاكرة متطايرة تُمحى عند
   * إعادة التشغيل، وتحمل بيانات جهاز واحد فقط، ولا تعرف المشغّل ولا التكلفة.
   * `/cdr/analysis` يقرأ `dinstar_cdr` الدائم مضمومًا إلى لقطات المنافذ
   * والبوابات، فيُعطي المشغّل وزمن الرنين وسبب الإنهاء والتكلفة لكل الأسطول.
   */
  const loadCdr = useCallback(async () => {
    try {
      const b = await json(await apiFetch('/api/admin/dinstar/cdr/analysis?limit=200'));
      setCdr(Array.isArray(b) ? b : (b.records || []));
    } catch (e: any) { message.error(e.message); }
  }, []);

  // السجل يُحمَّل تلقائيًا: قراءة قاعدة البيانات لا تلمس الأجهزة، فلا مبرّر
  // لإخفاء البيانات وراء زر. الزر بقي للتحديث اليدوي الفوري.
  usePolling(loadCdr, 30000);

  /**
   * أرقام الوجهة — تُفصل بفاصلة أو مسافة أو سطر جديد.
   * التكرار يُزال: إرسال الرسالة نفسها مرتين لنفس الرقم يُحتسب مرتين
   * على المشغّل ويصل المستلم مكرّرًا.
   */
  const smsRecipients = useMemo(
    () => Array.from(new Set(
      smsTo.split(/[\s,،;]+/).map((x) => x.trim()).filter(Boolean),
    )),
    [smsTo],
  );

  /**
   * القياس بالبايت لا بالحرف — الجهاز يحسب بالبايت.
   * TextEncoder يعطي طول UTF-8 الفعلي: الحرف العربي بايتان.
   */
  const smsBytes = useMemo(() => new TextEncoder().encode(smsText).length, [smsText]);

  /** أي حرف خارج ASCII يفرض unicode فينكمش المقطع من 160 إلى 70 حرفًا. */
  const smsIsUnicode = useMemo(() => /[^\x00-\x7F]/.test(smsText), [smsText]);

  /**
   * شرائح الإرسال المتاحة — مشتقّة من لقطة الأسطول الحقيقية لا من قائمة ثابتة.
   *
   * فهرس المنفذ مُقيَّد ببوابته: المنفذ 3 على جهاز يعني شريحة أخرى على جهاز
   * ثانٍ. لذلك لا تُعرض الشرائح إلا بعد اختيار البوابة، وإلا أرسل المسؤول من
   * منفذ يظنّه رقمًا معيّنًا وهو رقم آخر.
   *
   * غير المسجَّلة تظهر مُعطَّلة بدل حجبها: الغياب يبدو خطأً في الواجهة، أما
   * الظهور المُعطَّل فيقول «الشريحة موجودة لكنها ساقطة على الشبكة».
   */
  const smsPortOptions = useMemo(() => {
    if (!smsGateway || !fleetPorts) return [];
    const entry = fleetPorts.gateways.find((g) => g.gateway.host === smsGateway);
    if (!entry) return [];
    return entry.ports.map((p) => {
      const registered = isRegistered(p.status);
      const op = resolveOp(p.operator);
      return {
        value: p.index,
        disabled: !registered,
        label: `SIM ${p.index + 1} — ${p.numberMasked || 'رقم غير معروف'} · ${op.ar}`
          + (registered ? '' : ' · غير مسجّلة'),
      };
    });
  }, [smsGateway, fleetPorts]);

  const loadInbox = useCallback(async () => {
    try {
      const b = await json(await apiFetch('/api/admin/dinstar/sms/incoming'));
      setSmsInbox(b.sms || b.result || []);
    } catch (e: any) { message.error(e.message); }
  }, []);

  const sendSms = async () => {
    setSmsSending(true);
    try {
      const body = {
        text: smsText,
        // بوابة بعينها من الأسطول؛ عند تركها فارغة يستعمل الخادم
        // البوابة النشطة. بلا هذا الخيار كان كل SMS يخرج من جهاز واحد.
        ...(smsGateway ? { gatewayHost: smsGateway } : {}),
        // منفذ الإرسال: بدونه يختار الجهاز الأول دائمًا فتُستنزف شريحة واحدة
        // ويظهر للمستلم رقم غير المقصود. الباكإند يقبله للأدمن.
        ...(smsPort != null ? { port: [smsPort] } : {}),
        // الترميز يُحسم هنا لا في الجهاز: تركه للاستنتاج التلقائي كان
        // يسقط صامتًا إلى unicode ويضاعف عدد المقاطع.
        encoding: smsIsUnicode ? 'unicode' : 'gsm-7bit',
        param: smsRecipients.map((number, i) => ({ number, user_id: i + 1 })),
      };
      const b = await json(await apiFetch('/api/admin/dinstar/sms/send', {
        method: 'POST', body: JSON.stringify(body),
      }));
      // 202 = قُبلت للإرسال لاحقًا، وهي نجاح لا فشل.
      message.success(`أُرسلت إلى ${smsRecipients.length} مستلمًا (مهمة ${b.task_id ?? '—'})`);
      setSmsText(''); setSmsTo('');
    } catch (e: any) { message.error(e.message); } finally { setSmsSending(false); }
  };

  /**
   * فحص العنوان قبل التسجيل.
   *
   * يملأ الطراز وعدد المنافذ من الجهاز نفسه بدل تخمين المسؤول:
   * الطراز الخاطئ يعني الاستعلام عن منافذ غير موجودة.
   */
  const probeHost = async () => {
    const host = form.getFieldValue('host');
    if (!host) { message.warning('أدخل العنوان أولًا'); return; }
    setProbing(true); setProbeResult(null);
    try {
      const b: ProbeResult = await json(await apiFetch('/api/admin/dinstar/fleet/probe', {
        method: 'POST', body: JSON.stringify({ host }),
      }));
      setProbeResult(b);
      if (b.reachable && b.model) {
        // نملأ ما اكتشفه الجهاز فعلًا
        form.setFieldsValue({ model: b.model });
      }
    } catch (e: any) {
      message.error(e.message || 'تعذر فحص العنوان');
    } finally { setProbing(false); }
  };

  const addGateway = async () => {
    try {
      const values = await form.validateFields();
      await json(await apiFetch('/api/admin/dinstar/fleet', {
        method: 'POST', body: JSON.stringify(values),
      }));
      message.success('سُجّلت البوابة');
      setAddOpen(false); form.resetFields(); load();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.message || 'تعذر تسجيل البوابة');
    }
  };

  const toggleGateway = async (g: Gateway) => {
    try {
      await json(await apiFetch(`/api/admin/dinstar/fleet/${g.id}/enabled`, {
        method: 'POST', body: JSON.stringify({ enabled: !g.enabled }),
      }));
      message.success(g.enabled ? 'عُطّلت البوابة' : 'فُعّلت البوابة');
      load();
    } catch (e: any) { message.error(e.message); }
  };

  const removeGateway = (g: Gateway) =>
    Modal.confirm({
      title: `حذف ${g.name}؟`,
      content: 'يُزال الجهاز من التوجيه. لا يؤثر على إعدادات الجهاز نفسه.',
      okType: 'danger',
      onOk: async () => {
        try {
          await json(await apiFetch(`/api/admin/dinstar/fleet/${g.id}`, { method: 'DELETE' }));
          message.success('حُذفت البوابة'); load();
        } catch (e: any) { message.error(e.message); }
      },
    });

  const testRouting = async () => {
    try {
      const { number } = await routeForm.validateFields();
      const r = await apiFetch('/api/admin/dinstar/fleet/routing/select', {
        method: 'POST', body: JSON.stringify({ number }),
      });
      const body = await r.json().catch(() => ({}));
      setRouteResult(r.ok ? body : { error: body?.error || `HTTP ${r.status}`, ...body });
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.message);
    }
  };

  const renderPort = (gateway: Gateway, port: Port) => {
    const label = SIGNAL_LABEL[port.signalLabel || 'NO_SIGNAL'] || SIGNAL_LABEL.NO_SIGNAL;
    const measured = port.signalDbm != null;
    const op = resolveOp(port.operator);
    const registered = isRegistered(port.status);
    const ready = registered && !!port.signalUsable;
    // لون الحدود حسب الجاهزية — رسمي راقٍ بلا أخضر
    const borderColor = ready ? 'rgba(183,138,46,0.35)' : registered ? 'rgba(25,118,210,0.25)' : 'rgba(185,28,28,0.25)';
    const statusTag = ready ? { clr: '#B78A2E', txt: 'جاهز' } : registered ? { clr: '#1976D2', txt: 'مسجّل بلا إشارة' } : { clr: '#B91C1C', txt: 'غير مسجّل' };
    return (
      <Col xs={24} sm={12} lg={6} xl={6} key={`${gateway.id}-${port.index}`}>
        <Card
          size="small"
          hoverable
          style={{ borderColor, borderWidth: 1.5, overflow: 'hidden', height: '100%', display: 'flex', flexDirection: 'column' }}
          styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', padding: 12 } }}
          title={
            <Space size={6}>
              <span style={{ fontWeight: 800, letterSpacing: 0.3 }}>SIM {port.index + 1}</span>
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>@ {gateway.host}</Typography.Text>
            </Space>
          }
          extra={<Tag color={statusTag.clr} style={{ color: '#fff', border: 'none', fontWeight: 600 }}>{statusTag.txt}</Tag>}
        >
          {/* ── الشريحة والمشغل ── */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <Tag color={op.clr} style={{ color: '#fff', border: 'none', fontWeight: 600, fontSize: 12 }}>{op.ar}</Tag>
            <Space size={4}>
              <Tag style={{ fontSize: 11 }}>{port.radioType || '—'}</Tag>
              <Tag color={port.callState === 'Idle' ? 'default' : 'processing'} style={{ fontSize: 11 }}>{port.callState || '—'}</Tag>
            </Space>
          </div>

          {/* ── الإشارة — عرض أنيق ── */}
          <div style={{ textAlign: 'center', padding: '8px 0 6px', background: 'var(--yns-surface-light, #F0F2F5)', borderRadius: 10, marginBottom: 8 }}>
            <SignalFilled style={{ fontSize: 28, color: label.clr }} />
            {measured ? (
              <div style={{ padding: '4px 10px 0' }}>
                <Progress percent={Math.max(0, Math.min(100, port.signal ?? 0))} strokeColor={label.clr} size="small" showInfo={false} />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 2 }}>
                  <Typography.Text strong style={{ color: label.clr, fontSize: 12 }}>{port.signalDbm} dBm</Typography.Text>
                  <Tag color={label.clr} style={{ color: '#fff', border: 'none', fontSize: 11 }}>{label.ar}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>خام {port.signalRaw ?? '—'}</Typography.Text>
                </div>
              </div>
            ) : (
              <Tooltip title={`القراءة الخام ${port.signalRaw ?? '—'} تعني في 3GPP TS 27.007 «غير قابلة للكشف»`}>
                <div style={{ padding: 6 }}><Tag color="default">لا يوجد قياس إشارة</Tag></div>
              </Tooltip>
            )}
          </div>

          {/* ── كل المعلومات — شبكة 2×4 حديثة ── */}
          <Descriptions column={1} size="small" bordered={false} style={{ flex: 1 }}>
            <Descriptions.Item label={<span style={{ fontSize: 11, color: 'var(--yns-text-muted)' }}>الرقم</span>}>
              <span style={{ fontWeight: 600, fontSize: 12 }}>{port.numberMasked || 'غير معروف'}</span>
            </Descriptions.Item>
            <Descriptions.Item label={<span style={{ fontSize: 11 }}>IMSI</span>}>
              <Typography.Text copyable={!!port.imsiMasked} style={{ fontSize: 11 }}>{port.imsiMasked || '—'}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={<span style={{ fontSize: 11 }}>ICCID</span>}>
              <Typography.Text copyable={!!port.iccidMasked} style={{ fontSize: 11 }}>{port.iccidMasked || '—'}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label={<span style={{ fontSize: 11 }}>الشبكة</span>}>
              <Space size={4} wrap>
                <Tag style={{ fontSize: 11 }}>{port.gprs || '—'}</Tag>
                <Tag color={ready ? '#B78A2E' : undefined} style={ready ? { color: '#fff', border: 'none' } : {}}>{ready ? 'قابل للمكالمات' : 'غير جاهز'}</Tag>
              </Space>
            </Descriptions.Item>
          </Descriptions>

          {/* ── الأزرار — ترتيب عصري متساوٍ ── */}
          <Space.Compact block style={{ marginTop: 10 }}>
            <Button
              type="primary" size="small" icon={<PhoneOutlined />} block
              disabled={!ready}
              onClick={() => openDial(port.index, gateway.host)}
              style={{ fontWeight: 600 }}
            >
              اتصال
            </Button>
            <Button size="small" icon={<ApiOutlined />} onClick={() => { setUssdTarget({gatewayId: gateway.id, port: port.index}); setUssd(''); }}>
              USSD
            </Button>
            <Button size="small" icon={<ToolOutlined />} onClick={() => resetPort(gateway.host, port.index)}>
              تهيئة
            </Button>
          </Space.Compact>
          <Typography.Text type="secondary" style={{ fontSize: 10, textAlign: 'center', marginTop: 4, display: 'block' }}>
            {gateway.model} · منفذ {port.index} · {port.signalLabel || '—'}
          </Typography.Text>
        </Card>
      </Col>
    );
  };

  return (
    <div style={{ padding: 20 }}>
      <Row justify="space-between" align="middle">
        <div>
          <Typography.Title level={2} style={{ marginBottom: 0 }}>أسطول بوابات DINSTAR</Typography.Title>
          <Typography.Text type="secondary">
            UC2000-VE — جسر يونس الصوتي إلى شبكات GSM/LTE. التحكم الموثق فقط.
          </Typography.Text>
        </div>
        <Space>
          <Button type="primary" icon={<PhoneOutlined />} onClick={() => setDialerOpen(true)}>اتصال مباشر</Button>
          <Button icon={<RadarChartOutlined />} onClick={() => { setRouteResult(null); setRouteOpen(true); }}>
            اختبار التوجيه
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>إضافة بوابة</Button>
          <Button loading={loading} icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Row>

      {totals && (
        <Row gutter={[12, 12]} style={{ margin: '14px 0' }}>
          <Col xs={12} md={6}><Card size="small" styles={{ body: { padding: 12 } }}><Statistic title="البوابات" value={totals.gateways} suffix={`/ ${fleet.length}`} valueStyle={{ color: '#0F1B2D', fontWeight: 800 }} /></Card></Col>
          <Col xs={12} md={6}><Card size="small" styles={{ body: { padding: 12 } }}><Statistic title="متصلة" value={totals.online} valueStyle={{ color: '#B78A2E', fontWeight: 700 }} /></Card></Col>
          <Col xs={12} md={6}><Card size="small" styles={{ body: { padding: 12 } }}><Statistic title="شرائح مسجّلة" value={totals.registered} suffix={`/ ${totals.ports}`} valueStyle={{ color: '#1976D2', fontWeight: 600 }} /></Card></Col>
          <Col xs={12} md={6}>
            <Card size="small" styles={{ body: { padding: 12 } }}>
              <Tooltip title="الشريحة المسجّلة قد تكون بلا إشارة قابلة للقياس؛ الجاهزة فقط تصلح لحمل مكالمة.">
                <Statistic title="جاهزة للمكالمات" value={totals.usable} suffix={`/ ${totals.registered}`}
                  valueStyle={{ color: totals.usable < totals.registered ? '#C0842B' : '#B78A2E', fontWeight: 700 }} />
              </Tooltip>
            </Card>
          </Col>
        </Row>
      )}

      {silentlyDead.length > 0 && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 14 }}
          message={`${silentlyDead.length} شريحة مسجّلة على الشبكة لكنها بلا إشارة صالحة`}
          description={
            <>
              هذه الشرائح تبدو سليمة في حالة التسجيل، لكن قراءة الإشارة تعني «غير قابلة
              للكشف» أو أضعف من ‎-100 dBm. تُستبعد من توجيه المكالمات تلقائيًا.{' '}
              {silentlyDead.map((d) => `${d.host}#${d.index + 1}(raw=${d.raw ?? '—'})`).join('، ')}
            </>
          }
        />
      )}

      <Alert
        style={{ marginBottom: 14 }} type="info" showIcon
        message="مسار الصوت"
        description="المكالمات تخرج حصراً عبر Backend ← Asterisk ← PJSIP ← DINSTAR. لا يوجد endpoint اتصال مباشر في البوابة، ولا تستطيع أجهزة العملاء طلب البوابة دون إذن الخادم."
      />

      <Card title={<><SafetyCertificateOutlined /> حدود الأمان والقدرات</>} style={{ marginBottom: 16 }}>
        <Descriptions size="small" column={{ xs: 1, md: 3 }}>
          <Descriptions.Item label="الصوت">Asterisk/PJSIP فقط</Descriptions.Item>
          <Descriptions.Item label="SMS/USSD">{capabilities.ussd ? 'موثق' : 'غير متاح'}</Descriptions.Item>
          <Descriptions.Item label="حالة المنافذ">{capabilities.portInfo ? 'موثق' : 'غير متاح'}</Descriptions.Item>
          <Descriptions.Item label="مصادقة الواجهة">HTTP Digest</Descriptions.Item>
          <Descriptions.Item label="البرنامج الثابت">واجهة DINSTAR الأصلية فقط</Descriptions.Item>
          <Descriptions.Item label="إعادة ضبط المصنع"><Tag color="red">محظور من يونس</Tag></Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="الأجهزة المسجّلة" style={{ marginBottom: 16 }}>
        <Table
          rowKey="id" size="small" pagination={false} dataSource={fleet}
          locale={{ emptyText: <Empty description="لا توجد بوابات مسجّلة" /> }}
          columns={[
            { title: 'الاسم', dataIndex: 'name' },
            { title: 'الطراز', dataIndex: 'model', render: (m: string) => <Tag color="blue">{m}</Tag> },
            { title: 'العنوان', render: (_, g: Gateway) => `${g.scheme}://${g.host}:${g.apiPort}` },
            { title: 'المنافذ', dataIndex: 'portCount', align: 'center' as const },
            { title: 'الموقع', dataIndex: 'siteLabel', render: (v?: string) => v || '—' },
            { title: 'نظير PJSIP', dataIndex: 'pjsipEndpoint', render: (v?: string) => <code>{v || '—'}</code> },
            {
              title: 'الحالة',
              render: (_, g: Gateway) => {
                const h = HEALTH[g.healthState] || HEALTH.UNKNOWN;
                return <Space><Badge status={h.badge} text={h.ar} />{!g.enabled && <Tag>معطّلة</Tag>}</Space>;
              },
            },
            { title: 'الأولوية', dataIndex: 'routingPriority', align: 'center' as const },
            {
              title: 'إجراءات',
              render: (_, g: Gateway) => (
                <Space>
                  <Button size="small" icon={<DisconnectOutlined />} onClick={() => toggleGateway(g)}>
                    {g.enabled ? 'تعطيل' : 'تفعيل'}
                  </Button>
                  <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeGateway(g)} />
                </Space>
              ),
            },
          ]}
        />
      </Card>

      {fleetPorts?.gateways.map(({ gateway, ports, error }) => (
        <Card
          key={gateway.id}
          title={<Space><Tag color="blue">{gateway.model}</Tag>{gateway.host}
            {gateway.serialNumber && <Typography.Text type="secondary" style={{ fontSize: 12 }}>SN {gateway.serialNumber}</Typography.Text>}
            {/* بادئة OUI تؤكد المُصنّع — F8:A0:3D مسجّلة لـ Dinstar في IEEE */}
            {gateway.macAddress && (
              <Tooltip title="عنوان MAC — البادئة F8:A0:3D مسجّلة لـ Dinstar Technologies">
                <Typography.Text type="secondary" style={{ fontSize: 12 }} code>{gateway.macAddress}</Typography.Text>
              </Tooltip>
            )}
          </Space>}
          style={{ marginBottom: 16 }}
        >
          {error
            ? <Alert type="error" showIcon message={`تعذر الاستعلام: ${error}`} />
            : <Row gutter={[12, 12]}>{ports.map((p) => renderPort(gateway, p))}</Row>}
        </Card>
      ))}

      {!fleetPorts?.gateways.length && (
        <Card><Typography.Text type="secondary">
          لا توجد بوابات مفعّلة. أضف جهازًا وتأكد من وصول الخادم إلى شبكة الإدارة.
        </Typography.Text></Card>
      )}

      <Card
        title={<><HistoryOutlined /> سجل المكالمات ({cdr.length})</>}
        extra={<Button icon={<ReloadOutlined />} onClick={loadCdr}>تحديث</Button>}
      >
        <Table
          size="small" dataSource={cdr}
          rowKey={(r: any) => r.id || `${r.portIndex}-${r.startTime}`}
          locale={{ emptyText: <Empty description="لا توجد مكالمات مسجّلة" /> }}
          pagination={{ pageSize: 10, showSizeChanger: true, hideOnSinglePage: true }}
          scroll={{ x: 'max-content' }}
          columns={[
            {
              title: 'الشريحة', width: 110, fixed: 'left',
              render: (_, r: any) => (
                <Tooltip title={r.gatewayHost || '—'}>
                  <span>SIM {(r.portIndex ?? 0) + 1}</span>
                </Tooltip>
              ),
            },
            {
              title: 'المشغّل', dataIndex: 'operator', width: 110,
              render: (v: string) => {
                const op = resolveOp(v);
                return <Tag color={op.clr}>{op.ar}</Tag>;
              },
            },
            {
              title: 'الاتجاه', dataIndex: 'direction', width: 90,
              render: (v: string) => (
                <Tag color={v === 'INBOUND' ? 'blue' : 'geekblue'}>
                  {v === 'INBOUND' ? 'واردة' : 'صادرة'}
                </Tag>
              ),
            },
            { title: 'الرقم', dataIndex: 'number', width: 130 },
            {
              title: 'البدء', dataIndex: 'startTime', width: 160,
              render: (v: string) => (v ? new Date(v).toLocaleString('ar') : '—'),
            },
            {
              // زمن الرنين يفرّق بين «لم يُرَد» و«لم تصل الشبكة»: رنين طويل
              // بلا رد يعني وصولًا سليمًا، ورنين صفر يعني رفضًا فوريًا.
              title: 'الرنين', dataIndex: 'ringDuration', width: 80,
              render: (v: number | null) => (v == null ? '—' : `${v}s`),
            },
            {
              title: 'المدة', dataIndex: 'duration', width: 90,
              render: (v: number) => {
                if (!v) return '—';
                const m = Math.floor(v / 60);
                return m > 0 ? `${m}د ${v % 60}ث` : `${v}ث`;
              },
            },
            {
              title: 'الحالة', dataIndex: 'status', width: 110,
              render: (v: string) => {
                const map: Record<string, { ar: string; clr: string }> = {
                  ANSWERED: { ar: 'مُجابة', clr: 'gold' },
                  NO_ANSWER: { ar: 'بلا رد', clr: 'warning' },
                  BUSY: { ar: 'مشغول', clr: 'orange' },
                  FAILED: { ar: 'فاشلة', clr: 'error' },
                  CANCELLED: { ar: 'مُلغاة', clr: 'default' },
                };
                const hit = map[v] || { ar: v || '—', clr: 'default' };
                return <Tag color={hit.clr}>{hit.ar}</Tag>;
              },
            },
            {
              // سبب الإنهاء هو ما يميّز خلل الشبكة عن سلوك المستلم — بدونه
              // كل فشل يبدو واحدًا ولا يُشخَّص.
              title: 'سبب الإنهاء', dataIndex: 'hangupCause', width: 150,
              ellipsis: true, render: (v: string) => v || '—',
            },
            {
              title: 'التكلفة', dataIndex: 'cost', width: 110, align: 'right',
              render: (v: number) => (v ? `${v.toFixed(2)} ﷼` : '—'),
            },
            { title: 'الترميز', dataIndex: 'codec', width: 90, render: (v: string) => v || '—' },
          ]}
        />
      </Card>

      {/* ── الرسائل القصيرة ── */}
      <Card
        title={<><MessageOutlined /> الرسائل القصيرة (SMS)</>}
        extra={<Button icon={<ReloadOutlined />} onClick={loadInbox}>تحديث الوارد</Button>}
      >
        <Alert
          type="info" showIcon style={{ marginBottom: 16 }}
          message="مسار منفصل عن رسائل RED"
          description={
            'رسائل RED بين المستخدمين مشفّرة طرفيًا ولا تمرّ من هنا إطلاقًا. '
            + 'هذه الشاشة تخاطب شبكة GSM عبر شرائح البوابة، ونصّها يمرّ بالمشغّل كأي رسالة عادية. '
            + 'لا تُستخدم للتسجيل ولا لرموز التحقق.'
          }
        />
        <Row gutter={16}>
          <Col xs={24} lg={11}>
            <Divider titlePlacement="end" plain>إرسال</Divider>
            <Space.Compact style={{ width: '100%', marginBottom: 8 }}>
              <Input
                value={smsTo} onChange={(e) => setSmsTo(e.target.value)}
                placeholder="أرقام الوجهة مفصولة بفاصلة — 777123456,733445566"
              />
            </Space.Compact>
            <Select
              style={{ width: '100%', marginBottom: 8 }}
              value={smsGateway}
              onChange={(v) => { setSmsGateway(v); setSmsPort(undefined); }}
              allowClear
              placeholder="البوابة (اتركها فارغة للبوابة النشطة)"
              options={(fleet || [])
                .filter((g) => g.enabled)
                .map((g) => ({
                  value: g.host,
                  label: `${g.name} — ${g.host} (${g.model})`,
                }))}
            />
            <Select
              style={{ width: '100%', marginBottom: 8 }}
              value={smsPort}
              onChange={setSmsPort}
              allowClear
              disabled={!smsGateway}
              placeholder={smsGateway
                ? 'شريحة الإرسال (اتركها فارغة ليختار الجهاز)'
                : 'اختر البوابة أولًا لعرض الشرائح'}
              options={smsPortOptions}
              notFoundContent="لا توجد شرائح في هذه البوابة"
            />
            <Input.TextArea
              rows={4} value={smsText} onChange={(e) => setSmsText(e.target.value)}
              placeholder="نص الرسالة"
            />
            <Space style={{ marginTop: 8, width: '100%', justifyContent: 'space-between' }}>
              <Typography.Text type={smsBytes > SMS_MAX_TEXT_BYTES ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
                {smsBytes} / {SMS_MAX_TEXT_BYTES} بايت · {smsRecipients.length} / {SMS_MAX_RECIPIENTS} مستلمًا
                {' · '}{smsIsUnicode ? 'unicode (70 حرفًا/مقطع)' : 'gsm-7bit (160 حرفًا/مقطع)'}
              </Typography.Text>
              <Button
                type="primary" loading={smsSending} onClick={sendSms}
                disabled={
                  smsBytes === 0 || smsBytes > SMS_MAX_TEXT_BYTES
                  || smsRecipients.length === 0 || smsRecipients.length > SMS_MAX_RECIPIENTS
                }
              >
                إرسال
              </Button>
            </Space>
            <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 8 }}>
              الحدّ من وثيقة Dinstar HTTP API: {SMS_MAX_RECIPIENTS} مستلمًا و{SMS_MAX_TEXT_BYTES} بايت للطلب
              الواحد. القياس بالبايت لا بالحرف — الحرف العربي بايتان، فـ800 حرف عربي تتجاوز الحد.
            </Typography.Paragraph>
          </Col>
          <Col xs={24} lg={13}>
            <Divider titlePlacement="end" plain>الوارد</Divider>
            <Table
              size="small" dataSource={smsInbox} rowKey={(r: any) => `${r.index}-${r.timestamp}`}
              locale={{ emptyText: <Empty description="لا توجد رسائل واردة" /> }}
              pagination={{ pageSize: 5, hideOnSinglePage: true }}
              columns={[
                { title: 'المنفذ', dataIndex: 'port', width: 70, render: (v: number) => `SIM ${v + 1}` },
                { title: 'المرسل', dataIndex: 'number', width: 140 },
                { title: 'النص', dataIndex: 'text', ellipsis: true },
                {
                  title: 'الوقت', dataIndex: 'timestamp', width: 150,
                  render: (v: string) => (v ? new Date(v).toLocaleString('ar') : '—'),
                },
              ]}
            />
          </Col>
        </Row>
      </Card>

      {/* ── إضافة بوابة ── */}
      <Modal title="إضافة بوابة DINSTAR" open={addOpen} onCancel={() => setAddOpen(false)} onOk={addGateway} okText="تسجيل">
        <Form form={form} layout="vertical" initialValues={{ model: 'UC2000-VE-8G', scheme: 'https', apiPort: 443, routingPriority: 100 }}>
          <Form.Item name="host" label="عنوان الجهاز" rules={[
            { required: true, message: 'العنوان مطلوب' },
            {
              pattern: /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|127\.)/,
              message: 'يجب أن يكون عنوانًا خاصًا على شبكة الإدارة (RFC 1918)',
            },
          ]}>
            <Input
              placeholder="192.168.11.1"
              onChange={() => setProbeResult(null)}
              addonAfter={
                <Button type="link" size="small" loading={probing} onClick={probeHost} style={{ padding: 0 }}>
                  فحص
                </Button>
              }
            />
          </Form.Item>

          {probeResult && (
            <Alert
              style={{ marginBottom: 16 }}
              type={probeResult.reachable ? (probeResult.adoptable ? 'success' : 'warning') : 'error'}
              showIcon
              message={probeResult.reachable
                ? `استجاب جهاز — درجة الثقة ${probeResult.confidence}/100`
                : 'لا توجد استجابة get_port_info مصادَقة على هذا العنوان'}
              description={probeResult.reachable ? (
                <>
                  <Descriptions size="small" column={1} style={{ marginBottom: 8 }}>
                    <Descriptions.Item label="الطراز المكتشف">{probeResult.model || '—'}</Descriptions.Item>
                    <Descriptions.Item label="المنافذ">{probeResult.portCount ?? '—'}</Descriptions.Item>
                    <Descriptions.Item label="الرقم التسلسلي">{probeResult.serialNumber || '—'}</Descriptions.Item>
                    <Descriptions.Item label="عنوان MAC">{probeResult.macAddress || 'لم يُفصح عنه'}</Descriptions.Item>
                  </Descriptions>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>الإشارات المرصودة:</Typography.Text>
                  <ul style={{ margin: '4px 0 0', paddingInlineStart: 18, fontSize: 12 }}>
                    {probeResult.signals.map((sig) => <li key={sig}>{sig}</li>)}
                  </ul>
                  {!probeResult.adoptable && (
                    <Typography.Text type="warning" style={{ fontSize: 12 }}>
                      الثقة دون الحد الآمن للضم التلقائي — راجع الجهاز قبل التسجيل.
                    </Typography.Text>
                  )}
                </>
              ) : probeResult.message}
            />
          )}

          <Form.Item name="model" label="الطراز" rules={[{ required: true }]}>
            <Select options={models.map((m) => ({
              value: m.model,
              label: `${m.model} — ${m.portCount} قنوات${m.supportsVolte ? ' · VoLTE' : ''}`,
            }))} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}><Form.Item name="scheme" label="البروتوكول"><Select options={[{ value: 'https' }, { value: 'http' }]} /></Form.Item></Col>
            <Col span={12}><Form.Item name="apiPort" label="منفذ الواجهة"><InputNumber min={1} max={65535} style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
          <Form.Item name="pjsipEndpoint" label="نظير PJSIP في Asterisk"
            tooltip="اسم النظير الذي تخرج منه المكالمة. مع عدة أجهزة لكل جهاز نظيره.">
            <Input placeholder="dinstar-gw-1" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}><Form.Item name="siteLabel" label="الموقع"><Input placeholder="صنعاء — المقر" /></Form.Item></Col>
            <Col span={12}><Form.Item name="routingPriority" label="أولوية التوجيه" tooltip="الأقل رقمًا يُجرَّب أولًا"><InputNumber min={0} max={1000} style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>

      {/* ── اختبار التوجيه ── */}
      <Modal title="اختبار اختيار المنفذ" open={routeOpen} onCancel={() => setRouteOpen(false)}
        onOk={testRouting} okText="اختبار" width={640}>
        <Form form={routeForm} layout="vertical">
          <Form.Item name="number" label="رقم الوجهة" rules={[{ required: true, message: 'الرقم مطلوب' }]}
            tooltip="يُظهر أي بوابة ومنفذ سيحملان المكالمة ولماذا استُبعد الباقي.">
            <Input placeholder="771234567" />
          </Form.Item>
        </Form>
        {routeResult && (routeResult.error ? (
          <Alert type="error" showIcon message={routeResult.error === 'NO_USABLE_PORT'
            ? 'لا يوجد أي منفذ صالح لحمل المكالمة'
            : routeResult.error} />
        ) : (
          <>
            <Descriptions bordered size="small" column={1}>
              <Descriptions.Item label="مشغل الوجهة">{routeResult.targetOperator || 'غير معروف'}</Descriptions.Item>
              <Descriptions.Item label="البوابة">{routeResult.selected.gatewayHost}</Descriptions.Item>
              <Descriptions.Item label="المنفذ">{routeResult.selected.portIndex + 1}</Descriptions.Item>
              <Descriptions.Item label="شريحة المنفذ">{routeResult.selected.operator}</Descriptions.Item>
              <Descriptions.Item label="الإشارة">{routeResult.selected.signalDbm} dBm</Descriptions.Item>
              <Descriptions.Item label="داخل الشبكة">
                {routeResult.selected.onNet ? <Tag color="gold">نعم — تكلفة أقل</Tag> : <Tag>لا</Tag>}
              </Descriptions.Item>
            </Descriptions>
            {routeResult.rejected?.length > 0 && (
              <>
                <Divider titlePlacement="end" plain>المنافذ المستبعدة</Divider>
                <Space wrap>
                  {routeResult.rejected.map((r: any, i: number) => (
                    <Tag key={i} color={r.why === 'REJECTED_NO_SIGNAL' ? 'red' : 'default'}>
                      {r.gateway}#{r.port + 1} — {
                        r.why === 'REJECTED_NO_SIGNAL' ? `بلا إشارة (raw=${r.signalRaw ?? '—'})`
                          : r.why === 'REJECTED_BUSY' ? 'مشغول'
                            : 'غير مسجّل'
                      }
                    </Tag>
                  ))}
                </Space>
              </>
            )}
          </>
        ))}
      </Modal>

      {/* ── USSD ── */}
      <Modal open={ussdTarget != null} title={`USSD — SIM ${(ussdTarget?.port ?? 0) + 1}`}
        onCancel={() => { setUssdTarget(null); setUssdResult(null); }} onOk={sendUssd}
        okButtonProps={{ loading: ussdLoading }} okText="إرسال" cancelText="إلغاء">
        <Input placeholder="*163#" value={ussd} onChange={(e) => setUssd(e.target.value)} disabled={ussdLoading} />
        {ussdResult && (
          <Alert style={{ marginTop: 16 }} type="info" message="رد الشبكة" description={<pre style={{ whiteSpace: 'pre-wrap' }}>{ussdResult}</pre>} />
        )}
      </Modal>

      {/* ── اتصال من منفذ بعينه ── */}
      <Modal open={dialPort != null} title={`اتصال — SIM ${(dialPort ?? 0) + 1} @ ${dialHost || 'DINSTAR'}`}
        onCancel={() => { setDialPort(null); setDialResult(null); }}
        onOk={dialPortCall} confirmLoading={dialBusy}
        okText="اتصال" cancelText="إغلاق"
        okButtonProps={{ disabled: dialBusy || !/^[0-9]{6,15}$/.test(dialNumber.replace(/\D/g, '')) }}>
        <Input
          value={dialNumber} onChange={(e) => setDialNumber(e.target.value)}
          placeholder="رقم الهاتف — مثال 781834704" prefix={<PhoneOutlined />}
          onPressEnter={dialPortCall}
        />
        <Alert style={{ marginTop: 12 }} type="info"
          message="مكالمة مجسّرة: يرنّ متصفّحك أولًا، وعند الرد تُطلب الوجهة على GSM ويُوصل الصوت في الاتجاهين."
          description="المسار: متصفّحك ⇄ Asterisk ⇄ PJSIP ⇄ DINSTAR ⇄ شبكة GSM — من هذا المنفذ حصرًا." />
        {dialResult && (
          <Alert style={{ marginTop: 12 }} type="success" showIcon
            message={`أُطلقت: ${dialResult.status} · المنفذ ${(dialResult.slot ?? 0) + 1} · callId ${dialResult.callId}`}
            description={dialResult.bridged === false
              ? 'تنبيه: أُطلقت بلا جسر صوتي — إشغال منفذ فقط.'
              : 'ارفع السمّاعة في المتصفّح لسماع رنين الشبكة.'} />
        )}
      </Modal>
      <NumberLearningCard />
    </div>
  );
}








