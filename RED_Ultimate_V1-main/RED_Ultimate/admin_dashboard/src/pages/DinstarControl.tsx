import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert, Badge, Button, Card, Col, Descriptions, Divider, Empty, Form, Input,
  InputNumber, Modal, Progress, Row, Select, Space, Statistic, Table, Tag,
  Tooltip, Typography, message,
} from 'antd';
import {
  ApiOutlined, AuditOutlined, DeleteOutlined, DisconnectOutlined, HistoryOutlined, MessageOutlined,
  PlusOutlined, RadarChartOutlined, ReloadOutlined, SafetyCertificateOutlined, SignalFilled,
  ToolOutlined, PhoneOutlined, WarningOutlined,
} from '@ant-design/icons';
import {
  apiFetch, bindSim, unbindSim, getPstnEligibleUsers, updateSimInventory, type SimInventoryUpdate,
} from '../api';
import { usePolling } from '../hooks/usePolling';

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
  number?: string; numberMasked?: string; imsi?: string; imsiMasked?: string; iccid?: string; iccidMasked?: string; operator?: string;
  boundRedId?: string; boundUsername?: string;
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

/** مشغلو اليمن: 71 سبأفون · 73 يو · 77/78 يمن موبايل · 70 واي */
const YEMEN_OP: Record<string, { ar: string; clr: string }> = {
  Sabafon: { ar: 'سبأفون', clr: '#E53935' },
  MTN: { ar: 'يو', clr: '#FFB300' },
  YOU: { ar: 'يو', clr: '#FFB300' },
  YemenMobile: { ar: 'يمن موبايل', clr: '#43A047' },
  'Yemen Mobile': { ar: 'يمن موبايل', clr: '#43A047' },
  YTelecom: { ar: 'واي', clr: '#1E88E5' },
  'Y Telecom': { ar: 'واي', clr: '#1E88E5' },
  HiTel: { ar: 'واي', clr: '#1E88E5' },
};
const resolveOp = (o?: string) => {
  if (!o || o === 'UNKNOWN') return { ar: 'غير معروف', clr: '#757575' };
  if (YEMEN_OP[o]) return YEMEN_OP[o];
  const hit = Object.keys(YEMEN_OP).find((k) => o.includes(k));
  return hit ? YEMEN_OP[hit] : { ar: o, clr: '#757575' };
};

/** ألوان تصنيف الإشارة — مشتقة من dBm لا من نسبة ملفّقة. */
const SIGNAL_LABEL: Record<string, { ar: string; clr: string }> = {
  EXCELLENT: { ar: 'ممتازة', clr: '#00C896' },
  GOOD: { ar: 'جيدة', clr: '#52C41A' },
  FAIR: { ar: 'مقبولة', clr: '#E8B84A' },
  WEAK: { ar: 'ضعيفة', clr: '#FA8C16' },
  UNUSABLE: { ar: 'غير كافية', clr: '#F5222D' },
  NO_SIGNAL: { ar: 'لا يوجد قياس', clr: '#8C8C8C' },
  OUT_OF_RANGE: { ar: 'قراءة شاذة', clr: '#8C8C8C' },
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
  const [ussdTarget, setUssdTarget] = useState<number | null>(null);
  const [ussd, setUssd] = useState('');
  const [dialPort, setDialPort] = useState<number | null>(null);
  const [dialHost, setDialHost] = useState('');
  const [dialNumber, setDialNumber] = useState('');
  const [dialBusy, setDialBusy] = useState(false);
  const [dialResult, setDialResult] = useState<any>(null);
  const [smsTo, setSmsTo] = useState('');
  const [smsGateway, setSmsGateway] = useState<string | undefined>(undefined);
  const [smsText, setSmsText] = useState('');
  const [smsSending, setSmsSending] = useState(false);
  const [smsInbox, setSmsInbox] = useState<any[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [routeOpen, setRouteOpen] = useState(false);
  const [routeResult, setRouteResult] = useState<any>(null);
  const [probing, setProbing] = useState(false);
  const [probeResult, setProbeResult] = useState<ProbeResult | null>(null);
  const [reconcileLoading, setReconcileLoading] = useState(false);
  const [reconcileResult, setReconcileResult] = useState<any | null>(null);
  const [reconcileOpen, setReconcileOpen] = useState(false);
  const [bindOpen, setBindOpen] = useState(false);
  const [bindPort, setBindPort] = useState<number | null>(null);
  const [bindGwId, setBindGwId] = useState<string | null>(null);
  const [bindGwHost, setBindGwGwHost] = useState('');
  const [eligibleUsers, setEligibleUsers] = useState<any[]>([]);
  const [binding, setBinding] = useState(false);
  const [invOpen, setInvOpen] = useState(false);
  const [invPort, setInvPort] = useState<number | null>(null);
  const [invGwId, setInvGwId] = useState<string | null>(null);
  const [invSaving, setInvSaving] = useState(false);
  const [form] = Form.useForm();
  const [routeForm] = Form.useForm();
  const [bindForm] = Form.useForm();
  const [invForm] = Form.useForm();

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

  /** منافذ تحتاج تعلّم الرقم: مسجّلة + إشارة صالحة لكن number فارغ (Sabafon — يحتاج Phone Number Learning) */
  const needsNumberLearningPorts = useMemo(() => {
    if (!fleetPorts) return [];
    return fleetPorts.gateways.flatMap(({ gateway, ports }) =>
      ports
        .filter((p) => isRegistered(p.status) && !!p.signalUsable && !p.number)
        .map((p) => ({ host: gateway.host, gatewayId: gateway.id, index: p.index, operator: p.operator, imsiMasked: p.imsiMasked })),
    );
  }, [fleetPorts]);

  const runReconcile = useCallback(async () => {
    setReconcileLoading(true);
    try {
      const b = await json(await apiFetch('/api/admin/dinstar/bindings/reconcile'));
      setReconcileResult(b);
      setReconcileOpen(true);
      const s = (b as any)?.summary;
      if (s) {
        if (s.mismatched > 0 || s.orphanBindings > 0) message.warning(`يوجد ${s.mismatched} تضارب و ${s.orphanBindings} ربط يتيم`);
        else if (s.ok > 0) message.success(`المطابقة سليمة — ${s.ok} منفذ مطابق`);
        else message.info('لا توجد مطابقات — تحقق من الربط');
      }
    } catch (e: any) {
      message.error(e.message || 'تعذر التحقق من الربط');
    } finally {
      setReconcileLoading(false);
    }
  }, []);

  const triggerLearning = async (port: number) => {
    try {
      await json(await apiFetch(`/api/admin/dinstar/ports/${port}/learning`, { method: 'POST' }));
      message.success(`أُرسل طلب تعلّم الرقم للمنفذ ${port + 1} (Call Mode)`);
      setTimeout(load, 2000);
    } catch (e: any) { message.error(e.message); }
  };

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

  const sendUssd = async () => {
    if (ussdTarget == null) return;
    try {
      await json(await apiFetch(`/api/admin/dinstar/ports/${ussdTarget}/ussd`, {
        method: 'POST', body: JSON.stringify({ code: ussd }),
      }));
      message.success('أُرسل طلب USSD');
      setUssdTarget(null); setUssd('');
    } catch (e: any) { message.error(e.message); }
  };

  const openDial = (port: number, host: string) => {
    setDialPort(port); setDialHost(host); setDialNumber(''); setDialResult(null);
  };

  /**
   * إجراء مكالمة من منفذ بعينه: Backend → Asterisk → PJSIP → DINSTAR.
   * slotIndex يثبّت المنفذ في موزّع الأحمال (forcedPort).
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
      const b = await json(await apiFetch('/api/pstn/calls', {
        method: 'POST', body: JSON.stringify({ number, slotIndex: dialPort }),
      }));
      setDialResult(b);
      message.success(`تم إطلاق المكالمة على المنفذ ${(b.slot ?? dialPort) + 1} — ${b.status || 'DIALING'}`);
    } catch (e: any) { message.error(e.message); setDialResult(null); }
    finally { setDialBusy(false); }
  };

  const loadCdr = async () => {
    try {
      const b = await json(await apiFetch('/api/admin/dinstar/cdr'));
      setCdr(b.cdr || b.query || []);
    } catch (e: any) { message.error(e.message); }
  };

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

  const openBind = async (gwId: string, host: string, port: number, currentUser?: string) => {
    setBindGwId(gwId);
    setBindGwGwHost(host);
    setBindPort(port);
    bindForm.setFieldsValue({ userId: currentUser || undefined });
    setBindOpen(true);
    try {
      const users = await getPstnEligibleUsers();
      setEligibleUsers(users);
    } catch (e: any) {
      message.error('تعذر تحميل قائمة المستخدمين المؤهلين');
    }
  };

  const doBind = async () => {
    if (bindGwId === null || bindPort === null) return;
    setBinding(true);
    try {
      const { userId, number } = await bindForm.validateFields();
      if (!userId) {
        // Unbind case
        const portKey = `${bindGwId}#${bindPort}`;
        const existing = fleetPorts?.gateways.find(g => g.gateway.id === bindGwId)?.ports.find(p => p.index === bindPort);
        if (existing?.boundRedId) {
          // We need the internal UUID to unbind. PstnUser type has it.
          // For now, let's find the user in eligibleUsers or just call bind with null.
          // Backend bind supports updating to another user or 409 if exists.
          // PstnBindingController DELETE /{userId} is the explicit unbind.
        }
        message.warning('اختر مستخدماً للربط أو استخدم فك الارتباط الصريح');
        return;
      }
      await bindSim(userId, bindGwId, bindPort, number);
      message.success('تم ربط الشريحة بالمستخدم بنجاح');
      setBindOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(e.message || 'فشل عملية الربط');
    } finally {
      setBinding(false);
    }
  };

  const doUnbind = async (userId: string, redId: string) => {
    Modal.confirm({
      title: `فك ارتباط ${redId}؟`,
      content: 'لن يتمكن هذا المستخدم من استخدام هذه الشريحة (CLIP) بعد الآن.',
      okType: 'danger',
      onOk: async () => {
        try {
          await unbindSim(userId);
          message.success('تم فك الارتباط');
          load();
        } catch (e: any) { message.error(e.message); }
      }
    });
  };

  const openInventory = (gwId: string, port: Port) => {
    setInvGwId(gwId);
    setInvPort(port.index);
    invForm.setFieldsValue({
      simLabel: '', // TODO: fetch existing if any
      operatorLabel: port.operator || '',
      verificationState: 'UNVERIFIED',
      notes: ''
    });
    setInvOpen(true);
  };

  const doSaveInventory = async () => {
    if (invGwId === null || invPort === null) return;
    setInvSaving(true);
    try {
      const values = await invForm.validateFields();
      await updateSimInventory(invGwId, invPort, values as SimInventoryUpdate);
      message.success('تم تحديث بيانات المخزون بنجاح');
      setInvOpen(false);
      load();
    } catch (e: any) {
      if (e.errorFields) return;
      message.error(e.message || 'فشل تحديث المخزون');
    } finally {
      setInvSaving(false);
    }
  };

  const renderPort = (gateway: Gateway, port: Port) => {
    const label = SIGNAL_LABEL[port.signalLabel || 'NO_SIGNAL'] || SIGNAL_LABEL.NO_SIGNAL;
    const measured = port.signalDbm != null;
    const needsLearning = isRegistered(port.status) && !!port.signalUsable && !port.number;
    return (
      <Col xs={24} sm={12} lg={6} key={`${gateway.id}-${port.index}`}>
        <Card
          size="small"
          title={`SIM ${port.index + 1}`}
          extra={
            <Space size={4}>
              {needsLearning && (
                <Tooltip title="الرقم فارغ رغم وجود الشريحة — فعّل Phone Number Learning من واجهة الجهاز (Human Behavior → Phone Number Learning). راجع docs/diagnostics/DINSTAR_NUMBER_LEARNING_GUIDE_AR.md — نمط Call موصى به لـ Sabafon.">
                  <Tag color="orange" icon={<WarningOutlined />}>يحتاج تعلّم الرقم</Tag>
                </Tooltip>
              )}
              <Tag color={isRegistered(port.status) ? (port.signalUsable ? 'green' : 'orange') : 'red'}>
                {isRegistered(port.status) ? (port.signalUsable ? 'جاهز' : 'مسجّل بلا إشارة') : 'غير مسجّل'}
              </Tag>
            </Space>
          }
        >
          <div style={{ textAlign: 'center' }}>
            <SignalFilled style={{ fontSize: 30, color: label.clr }} />
            {measured ? (
              <>
                <Progress percent={port.signal ?? 0} strokeColor={label.clr} size="small" />
                <Typography.Text strong style={{ color: label.clr }}>
                  {port.signalDbm} dBm · {label.ar}
                </Typography.Text>
              </>
            ) : (
              // الفرق الجوهري: لا نرسم شريطًا بنسبة ملفّقة حين لا يوجد قياس.
              <Tooltip title={`القراءة الخام ${port.signalRaw ?? '—'} تعني في 3GPP TS 27.007 «غير قابلة للكشف»`}>
                <div style={{ padding: '6px 0' }}>
                  <Tag color="default">لا يوجد قياس إشارة</Tag>
                </div>
              </Tooltip>
            )}
            <Space wrap style={{ marginTop: 6 }}>
              <Tag>{port.radioType || 'UNKNOWN'}</Tag>
              <Tag color="blue">{port.callState || 'UNKNOWN'}</Tag>
              <Tag>{port.gprs || 'UNKNOWN'}</Tag>
            </Space>
          </div>
          <Descriptions column={1} size="small" style={{ marginTop: 8 }}>
            <Descriptions.Item label="المشغل">
              <Tag color={resolveOp(port.operator).clr} style={{ fontWeight: 600, fontSize: 12, padding: '2px 10px' }}>{resolveOp(port.operator).ar}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="الرقم">
              {port.number ? (
                <Space>
                  <Typography.Text copyable style={{ fontFamily: 'monospace', fontWeight: 600, color: '#1677ff' }}>{port.number}</Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>({port.numberMasked})</Typography.Text>
                </Space>
              ) : port.numberMasked ? (
                <Typography.Text copyable style={{ fontFamily: 'monospace' }}>{port.numberMasked}</Typography.Text>
              ) : needsLearning ? (
                <Tooltip title="الرقم فارغ رغم التسجيل والإشارة — الحل: Human Behavior → Phone Number Learning (نمط Call موصى به لـ Sabafon). راجع docs/diagnostics/DINSTAR_NUMBER_LEARNING_GUIDE_AR.md">
                  <Tag color="warning" icon={<WarningOutlined />}>يحتاج تعلّم الرقم</Tag>
                </Tooltip>
              ) : (
                <Tag color="default">غير معروف</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="IMSI">
              {port.imsi ? (
                <Typography.Text copyable style={{ fontFamily: 'monospace', fontSize: 11 }}>{port.imsi}</Typography.Text>
              ) : port.imsiMasked ? (
                <Space>
                  <Typography.Text style={{ fontFamily: 'monospace', fontSize: 11 }}>{port.imsiMasked}</Typography.Text>
                  <Tooltip title="IMSI الكامل مخفي للخصوصية — يظهر للأدمن فقط عند الحاجة"><Tag>مخفي</Tag></Tooltip>
                </Space>
              ) : (
                '—'
              )}
            </Descriptions.Item>
            {port.iccid && (
              <Descriptions.Item label="ICCID">
                <Typography.Text copyable style={{ fontFamily: 'monospace', fontSize: 11 }}>{port.iccid}</Typography.Text>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="الحساب المالك">
              {port.boundUsername ? (
                <Space size={4} wrap>
                  <Tag color="purple" style={{ fontWeight: 600 }}>{port.boundUsername}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 11 }}>#{port.boundRedId}</Typography.Text>
                  <Button size="small" type="link" danger onClick={() => {
                    // We need the internal UUID which isn't in Port type currently.
                    // Let's assume boundRedId is enough for a lookup or update the API to include it.
                    // Actually, the reconcile logic shows we need the userId.
                    message.info('استخدم صفحة إدارة PSTN لفك الارتباط حالياً أو Reconcile');
                  }}>فك</Button>
                </Space>
              ) : (
                <Space>
                  <Tag color="default">غير مرتبط</Tag>
                  <Button size="small" type="link" onClick={() => openBind(gateway.id, gateway.host, port.index)}>ربط</Button>
                </Space>
              )}
            </Descriptions.Item>
          </Descriptions>
          <Space style={{ marginTop: 6 }} wrap>
            <Button
              type="primary" size="small" icon={<PhoneOutlined />}
              disabled={!isRegistered(port.status) || !port.signalUsable}
              onClick={() => openDial(port.index, gateway.host)}
            >
              اتصال
            </Button>
            <Button size="small" icon={<ApiOutlined />} onClick={() => { setUssdTarget(port.index); setUssd(''); }}>
              USSD
            </Button>
            <Button size="small" icon={<HistoryOutlined />} onClick={() => triggerLearning(port.index)}>
              تعلّم
            </Button>
            <Button size="small" icon={<SafetyCertificateOutlined />} onClick={() => openInventory(gateway.id, port)}>
              جرد
            </Button>
            <Button size="small" danger icon={<ToolOutlined />} onClick={() => resetPort(gateway.host, port.index)}>
              إعادة
            </Button>
          </Space>
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
          <Tooltip title="يقارن أرقام الشرائح الحية (get_port_info) مع ربط PSTN في القاعدة ويكشف التضارب واحتياج Number Learning">
            <Button icon={<AuditOutlined />} loading={reconcileLoading} onClick={runReconcile}>تحقق من الربط</Button>
          </Tooltip>
          <Button icon={<RadarChartOutlined />} onClick={() => { setRouteResult(null); setRouteOpen(true); }}>
            اختبار التوجيه
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setAddOpen(true)}>إضافة بوابة</Button>
          <Button loading={loading} icon={<ReloadOutlined />} onClick={load}>تحديث</Button>
        </Space>
      </Row>

      {totals && (
        <Row gutter={[12, 12]} style={{ margin: '14px 0' }}>
          <Col xs={12} md={6}><Card size="small"><Statistic title="البوابات" value={totals.gateways} suffix={`/ ${fleet.length}`} /></Card></Col>
          <Col xs={12} md={6}><Card size="small"><Statistic title="متصلة" value={totals.online} valueStyle={{ color: '#00C896' }} /></Card></Col>
          <Col xs={12} md={6}><Card size="small"><Statistic title="شرائح مسجّلة" value={totals.registered} suffix={`/ ${totals.ports}`} /></Card></Col>
          <Col xs={12} md={6}>
            <Card size="small">
              <Tooltip title="الشريحة المسجّلة قد تكون بلا إشارة قابلة للقياس؛ الجاهزة فقط تصلح لحمل مكالمة.">
                <Statistic title="جاهزة للمكالمات" value={totals.usable} suffix={`/ ${totals.registered}`}
                  valueStyle={{ color: totals.usable < totals.registered ? '#E8B84A' : '#00C896' }} />
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

      {needsNumberLearningPorts.length > 0 && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 14 }}
          message={`${needsNumberLearningPorts.length} شريحة مسجّلة بإشارة صالحة لكن رقمها فارغ — تحتاج تعلّم الرقم`}
          description={
            <>
              الحقل <code>number</code> فارغ في <code>get_port_info</code> رغم التسجيل والإشارة — حال Sabafon الافتراضي قبل تفعيل Phone Number Learning.{' '}
              افتح واجهة الجهاز: <Typography.Text strong>Human Behavior → Phone Number Learning</Typography.Text> واختر <Tag color="green">Call</Tag> لكل المنافذ (موصى به — يتعلم 8 أرقام داخليًا عبر CLIP بدون USSD).
              التفاصيل الكاملة مع لقطات توضيحية:{' '}
              <Typography.Text code>docs/diagnostics/DINSTAR_NUMBER_LEARNING_GUIDE_AR.md</Typography.Text>{' '}
              — بعد الحفظ اضغط <Button size="small" onClick={runReconcile} loading={reconcileLoading}>تحقق من الربط</Button> ليظهر <code>liveNumber</code> مملوءًا.
              <br />
              المنافذ: {needsNumberLearningPorts.map((d) => `${d.host}#${d.index + 1}(${resolveOp(d.operator).ar}${d.imsiMasked ? ` · ${d.imsiMasked}` : ''})`).join('، ')}
              <br />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                بدائل: USSD (<code>*121#</code> + Keyword) أو SMS — لكن USSD لمعرفة الرقم غير موثق في Sabafon. كذلك فعّل <code>Auto CLIP Routing</code> و <code>Auto Update SIM Number</code> كميزتين مرتبطتين.
              </Typography.Text>
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
            { title: 'العنوان', render: (_: any, g: Gateway) => `${g.scheme}://${g.host}:${g.apiPort}` },
            { title: 'المنافذ', dataIndex: 'portCount', align: 'center' as const },
            { title: 'الموقع', dataIndex: 'siteLabel', render: (v?: string) => v || '—' },
            { title: 'نظير PJSIP', dataIndex: 'pjsipEndpoint', render: (v?: string) => <code>{v || '—'}</code> },
            {
              title: 'الحالة',
              render: (_: any, g: Gateway) => {
                const h = HEALTH[g.healthState] || HEALTH.UNKNOWN;
                return <Space><Badge status={h.badge} text={h.ar} />{!g.enabled && <Tag>معطّلة</Tag>}</Space>;
              },
            },
            { title: 'الأولوية', dataIndex: 'routingPriority', align: 'center' as const },
            {
              title: 'إجراءات',
              render: (_: any, g: Gateway) => (
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
        title={<><HistoryOutlined /> سجل المكالمات من الجهاز (CDR)</>}
        extra={<Button onClick={loadCdr}>تحميل</Button>}
      >
        <Table
          size="small" dataSource={cdr}
          rowKey={(r: any) => `${r.id || r.port}-${r.startTime || r.start_date}`}
          columns={[
            { title: 'المنفذ', dataIndex: 'port' },
            { title: 'الاتجاه', dataIndex: 'direction' },
            { title: 'الرقم', render: (_: any, r: any) => r.callee || r.destination_number || '—' },
            { title: 'البدء', render: (_: any, r: any) => r.startTime || r.start_date || '—' },
            { title: 'المدة', dataIndex: 'duration' },
            { title: 'الحالة', dataIndex: 'status' },
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
              onChange={setSmsGateway}
              allowClear
              placeholder="البوابة (اتركها فارغة للبوابة النشطة)"
              options={(fleet || [])
                .filter((g) => g.enabled)
                .map((g) => ({
                  value: g.host,
                  label: `${g.name} — ${g.host} (${g.model})`,
                }))}
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
                {routeResult.selected.onNet ? <Tag color="green">نعم — تكلفة أقل</Tag> : <Tag>لا</Tag>}
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

      {/* ── Reconcile ── */}
      <Modal title="تحقق من الربط — المطابقة الحية vs القاعدة" open={reconcileOpen} onCancel={() => setReconcileOpen(false)} footer={[<Button key="close" onClick={() => setReconcileOpen(false)}>إغلاق</Button>, <Button key="reload" type="primary" loading={reconcileLoading} onClick={runReconcile}>تحديث</Button>]} width={900}>
        {!reconcileResult ? (
          <Empty description="لا توجد نتيجة بعد — اضغط تحديث" />
        ) : (
          <>
            {(() => {
              const s = (reconcileResult as any)?.summary;
              return s ? (
                <Row gutter={[8, 8]} style={{ marginBottom: 12 }}>
                  <Col span={4}><Card size="small"><Statistic title="الإجمالي" value={s.totalPorts} /></Card></Col>
                  <Col span={4}><Card size="small"><Statistic title="مربوط" value={s.boundPorts} /></Card></Col>
                  <Col span={4}><Card size="small"><Statistic title="مطابق" value={s.ok} valueStyle={{ color: '#00C896' }} /></Card></Col>
                  <Col span={4}><Card size="small"><Statistic title="تضارب" value={s.mismatched} valueStyle={{ color: s.mismatched ? '#F5222D' : undefined }} /></Card></Col>
                  <Col span={4}><Card size="small"><Statistic title="يتيم" value={s.orphanBindings} valueStyle={{ color: s.orphanBindings ? '#FA8C16' : undefined }} /></Card></Col>
                  <Col span={4}><Card size="small"><Statistic title="بلا ربط وبه SIM" value={s.unboundWithSim} valueStyle={{ color: s.unboundWithSim ? '#FA8C16' : undefined }} /></Card></Col>
                </Row>
              ) : null;
            })()}
            {(reconcileResult as any)?.learnInstruction && (
              <Alert type="info" showIcon style={{ marginBottom: 12 }} message="تعليمات تعلّم الرقم" description={<><Typography.Text code>{(reconcileResult as any).learnInstruction}</Typography.Text><br /><Typography.Text type="secondary" style={{ fontSize: 12 }}>المسار: Human Behavior → Phone Number Learning — التفاصيل في docs/diagnostics/DINSTAR_NUMBER_LEARNING_GUIDE_AR.md (نمط Call موصى به لـ Sabafon).</Typography.Text></>} />
            )}
            <Table
              size="small" pagination={{ pageSize: 8, hideOnSinglePage: true }}
              dataSource={((reconcileResult as any)?.ports || []) as any[]}
              rowKey={(r: any) => `${r.gatewayId}#${r.index}`}
              columns={[
                { title: '#', dataIndex: 'index', width: 50, render: (v: number) => v + 1 },
                { title: 'المضيف', dataIndex: 'host', width: 130 },
                { title: 'الرقم الحي', dataIndex: 'liveNumber', render: (v: string | null) => v ? <Tag color="blue" style={{ fontFamily: 'monospace' }}>{v}</Tag> : <Tag color="orange" icon={<WarningOutlined />}>فارغ</Tag> },
                { title: 'IMSI/مقنّع', dataIndex: 'imsiLast4', render: (v: string | null, r: any) => v ? `••••${v}` : (r.liveNumberMasked || '—') },
                { title: 'المشغل', dataIndex: 'operator', render: (v: string) => <Tag color={resolveOp(v).clr}>{resolveOp(v).ar}</Tag> },
                { title: 'المربوط', dataIndex: 'boundRedId', render: (v: string | null, r: any) => v ? <><Tag color="purple">{v}</Tag><br /><span style={{ fontFamily: 'monospace', fontSize: 11 }}>{r.boundNumber || '—'}</span></> : <Tag>غير مربوط</Tag> },
                { title: 'الحالة', dataIndex: 'suggestedAction', width: 160, render: (v: string, r: any) => {
                  const needs = r.needsNumberLearning;
                  if (v === 'MISMATCH') return <Tag color="red">تضارب</Tag>;
                  if (v === 'ORPHAN_BINDING_NEEDS_CLEAR') return <Tag color="red">يتيم — يحتاج فك</Tag>;
                  if (v === 'UNBOUND_HAS_SIM') return <Tag color="orange">بلا ربط وبه SIM</Tag>;
                  if (needs) return <Tag color="orange" icon={<WarningOutlined />}>يحتاج تعلّم</Tag>;
                  return <Tag color="green">مطابق</Tag>;
                } },
                { title: 'السبب', dataIndex: 'reason', ellipsis: true, render: (v: string) => <Tooltip title={v}><span style={{ fontSize: 11 }}>{v}</span></Tooltip> },
              ]}
            />
            <Typography.Paragraph type="secondary" style={{ fontSize: 11, marginTop: 8 }}>
              المصدر: <code>GET /api/admin/dinstar/bindings/reconcile</code> — يقارن <code>liveNumber</code> من <code>get_port_info</code> مع <code>pstn_number</code> في القاعدة. <code>GET /api/admin/dinstar/bindings/discover</code> يعيد نفس البيانات + <code>learnable/learnInstruction</code>.
            </Typography.Paragraph>
          </>
        )}
      </Modal>

      {/* ── USSD ── */}
      <Modal open={ussdTarget != null} title={`USSD — SIM ${(ussdTarget ?? 0) + 1}`}
        onCancel={() => setUssdTarget(null)} onOk={sendUssd}
        okButtonProps={{ disabled: !/^[*#0-9]{2,30}$/.test(ussd) }}>
        <Input value={ussd} onChange={(e) => setUssd(e.target.value)} placeholder="مثال *101#" />
        <Alert style={{ marginTop: 12 }} type="warning"
          message="قد يعرض USSD الرصيد أو معلومات حساسة؛ النتيجة لا تُسجَّل نصًا في سجل التدقيق." />
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
          message="المكالمة تخرج عبر Backend ← Asterisk ← PJSIP ← DINSTAR من هذا المنفذ حصرًا (slotIndex مثبّت في موزّع الأحمال)." />
        {dialResult && (
          <Alert style={{ marginTop: 12 }} type="success" showIcon
            message={`أُطلقت: ${dialResult.status} · المنفذ ${(dialResult.slot ?? 0) + 1} · callId ${dialResult.callId}`} />
        )}
      </Modal>

      {/* ── ربط شريحة بمستخدم ── */}
      <Modal
        title={`ربط شريحة بمستخدم — المنفذ ${(bindPort ?? 0) + 1} @ ${bindGwHost}`}
        open={bindOpen}
        onCancel={() => setBindOpen(false)}
        onOk={doBind}
        confirmLoading={binding}
        okText="حفظ الربط"
        cancelText="إلغاء"
      >
        <Form form={bindForm} layout="vertical">
          <Form.Item name="userId" label="المستخدم المستهدف" rules={[{ required: true, message: 'اختر مستخدماً' }]}>
            <Select
              showSearch
              placeholder="ابحث عن مستخدم (الاسم أو معرّف RED)"
              optionFilterProp="children"
              options={eligibleUsers.map(u => ({
                value: u.userId,
                label: `${u.displayName} (@${u.username}) — ${u.redId}`
              }))}
            />
          </Form.Item>
          <Form.Item
            name="number"
            label="رقم الهاتف (CLIP)"
            tooltip="الرقم الفعلي للشريحة. يُستخدم للتحقق ولإظهاره كـ Caller ID للمستلم."
          >
            <Input placeholder="مثال: 712064924" />
          </Form.Item>
          <Alert
            type="info"
            showIcon
            message="الربط الدائم"
            description="عند ربط مستخدم بمنفذ، ستخرج كافة مكالماته من هذا المنفذ حصرًا، وستوجه المكالمات الواردة لهذا المنفذ إليه مباشرة."
          />
        </Form>
      </Modal>

      {/* ── جرد الشريحة ── */}
      <Modal
        title={`جرد الشريحة — المنفذ ${(invPort ?? 0) + 1}`}
        open={invOpen}
        onCancel={() => setInvOpen(false)}
        onOk={doSaveInventory}
        confirmLoading={invSaving}
        okText="حفظ البيانات"
        cancelText="إلغاء"
      >
        <Form form={invForm} layout="vertical">
          <Form.Item name="simLabel" label="تسمية الشريحة" tooltip="اسم وصفي (مثلاً: رقم الطوارئ 1)">
            <Input placeholder="أدخل اسماً اختيارياً" />
          </Form.Item>
          <Form.Item name="operatorLabel" label="تسمية المشغل" tooltip="تجاوز كشف المشغل التلقائي">
            <Input placeholder="مثلاً: Sabafon North" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="verificationState" label="حالة التحقق" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'UNVERIFIED', label: 'غير محقق' },
                  { value: 'VERIFIED', label: 'محقق' },
                  { value: 'LEARNED', label: 'تم تعلمه آلياً' },
                  { value: 'FAILED', label: 'فشل التحقق' },
                ]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="verificationMethod" label="طريقة التحقق">
                <Select allowClear options={[
                  { value: 'MANUAL', label: 'يدوي' },
                  { value: 'USSD', label: 'USSD' },
                  { value: 'SMS_KEYWORD', label: 'SMS Keyword' },
                  { value: 'CALL_LOOP', label: 'Call Loop' },
                ]} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="ملاحظات فنية">
            <Input.TextArea rows={2} placeholder="أي معلومات إضافية عن هذه الشريحة" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
