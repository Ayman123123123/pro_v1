import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Col, Empty, Row, Select,
  Space, Statistic, Table, Tag, Typography, message,
} from 'antd';
import {
  ClockCircleOutlined, PhoneOutlined,
  ReloadOutlined, SwapOutlined,
} from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * صفحة تحليل سجل المكالمات CDR — إحصائيات ورسوم بيانية.
 *
 * تعرض:
 * 1. ملخص إحصائي (إجمالي، نجحت، فشلت، متوسط المدة)
 * 2. رسم بياني للمكالمات لكل يوم (آخر 30 يوم)
 * 3. رسم بياني لتوزيع المشغلين
 * 4. رسم بياني لتوزيع البوابات
 * 5. جدول مكالمات تفصيلي مع تصفية
 */

type CdrRecord = {
  id: string;
  gatewayHost: string;
  portIndex: number;
  direction: string;
  number: string;
  callerNumber?: string | null;
  calleeNumber?: string | null;
  startTime: string;
  answerTime?: string | null;
  duration: number;
  ringDuration?: number;
  status: string;
  hangupCause?: string | null;
  codec?: string | null;
  gsmCode?: number | null;
  operator: string;
  cost?: number;
};

/**
 * ملخّص محسوب في القاعدة على كل الصفوف.
 *
 * الحساب في المتصفّح كان يجري على الصفحة المُحمَّلة فقط (500 صفًّا)، فيُعرَض
 * «إجمالي المكالمات» وهو في الحقيقة إجمالي المعروض. القاعدة ترى كل الصفوف.
 */
type CdrSummary = {
  total: number;
  answered: number;
  noAnswer: number;
  busy: number;
  failed: number;
  cancelled: number;
  inbound: number;
  outbound: number;
  totalSeconds: number;
  billableSeconds: number;
  avgAnsweredSeconds: number;
  answerRate: number;
  totalCostYer: number;
  firstCallAt?: string | null;
  lastCallAt?: string | null;
};

type DailyStats = {
  date: string;
  calls: number;
  duration: number;
  succeeded: number;
  failed: number;
};
const STATUS_COLORS: Record<string, string> = {
  ANSWERED: 'success',
  NO_ANSWER: 'warning',
  BUSY: 'warning',
  CANCELLED: 'default',
  FAILED: 'error',
};

/**
 * مفردات `dinstar_cdr.status` — نفس قيد CHECK في المخطَّط. الواجهة كانت تعرض
 * `COMPLETED`/`REJECTED` وهي مفردات نتيجة التوجيه لا حالة المكالمة.
 */
const STATUS_LABELS: Record<string, string> = {
  ANSWERED: 'مُجابة',
  NO_ANSWER: 'لا رد',
  BUSY: 'مشغول',
  CANCELLED: 'ملغاة',
  FAILED: 'فشل',
};

const DIRECTION_LABELS: Record<string, string> = {
  INBOUND: 'واردة',
  OUTBOUND: 'صادرة',
};

export default function CdrAnalysis() {
  const [cdr, setCdr] = useState<CdrRecord[]>([]);
  const [summary, setSummary] = useState<CdrSummary | null>(null);
  const [daily, setDaily] = useState<DailyStats[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [gatewayFilter, setGatewayFilter] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // الملخّص والتوزيع اليومي من القاعدة على كل الصفوف، والجدول عيّنة
      // أحدثها. طلبها معًا لا متتابعة: التتابع يُظهر أرقامًا من لحظات مختلفة.
      const [listRes, sumRes, dailyRes] = await Promise.all([
        apiFetch('/api/admin/dinstar/cdr/analysis'),
        apiFetch('/api/admin/dinstar/cdr/summary'),
        apiFetch('/api/admin/dinstar/cdr/daily?days=30'),
      ]);
      if (!listRes.ok) throw new Error(`HTTP ${listRes.status}`);
      const data = await listRes.json();
      setCdr(Array.isArray(data) ? data : (data.records || []));
      // فشل الملخّص لا يُفرِّغ الجدول: الجدول وحده مفيد.
      setSummary(sumRes.ok ? await sumRes.json() : null);
      setDaily(dailyRes.ok ? await dailyRes.json() : []);
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل سجل المكالمات');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  /**
   * الإحصاءات المعروضة. تُفضَّل أرقام القاعدة دائمًا؛ وعند تعذّر الملخّص
   * تُحسَب من الصفوف المُحمَّلة مع تعليم أنها جزئية بدل الإيهام بأنها الكل.
   */
  const stats = useMemo(() => {
    if (summary) {
      return {
        total: summary.total,
        succeeded: summary.answered,
        failed: summary.total - summary.answered,
        avgDuration: summary.avgAnsweredSeconds,
        totalCost: summary.totalCostYer,
        inbound: summary.inbound,
        outbound: summary.outbound,
        answerRate: summary.answerRate,
        partial: false,
      };
    }
    const total = cdr.length;
    const succeeded = cdr.filter((r) => r.status?.toUpperCase() === 'ANSWERED').length;
    const answeredSeconds = cdr
      .filter((r) => r.status?.toUpperCase() === 'ANSWERED')
      .reduce((s, r) => s + (r.duration || 0), 0);
    return {
      total,
      succeeded,
      failed: total - succeeded,
      // متوسط المُجابة فقط: إدخال غير المُجابة يسحب المتوسط إلى الصفر.
      avgDuration: succeeded > 0 ? Math.round(answeredSeconds / succeeded) : 0,
      totalCost: cdr.reduce((s, r) => s + (r.cost || 0), 0),
      inbound: cdr.filter((r) => r.direction === 'INBOUND').length,
      outbound: cdr.filter((r) => r.direction === 'OUTBOUND').length,
      answerRate: total > 0 ? Math.round((succeeded / total) * 100) : 0,
      partial: true,
    };
  }, [cdr, summary]);

  // توزيع المشغلين
  const operatorDist = useMemo(() => {
    const map = new Map<string, number>();
    cdr.forEach((r) => {
      const op = r.operator || 'غير معروف';
      map.set(op, (map.get(op) || 0) + 1);
    });
    return Array.from(map.entries())
      .map(([operator, count]) => ({ operator, count }))
      .sort((a, b) => b.count - a.count);
  }, [cdr]);

  // توزيع البوابات
  const gatewayDist = useMemo(() => {
    const map = new Map<string, number>();
    cdr.forEach((r) => {
      map.set(r.gatewayHost || 'غير معروف', (map.get(r.gatewayHost || 'غير معروف') || 0) + 1);
    });
    return Array.from(map.entries())
      .map(([gateway, count]) => ({ gateway, count }))
      .sort((a, b) => b.count - a.count);
  }, [cdr]);

  // البوابات الفريدة للتصفية
  const gateways = useMemo(() => [...new Set(cdr.map((r) => r.gatewayHost))], [cdr]);

  // تصفية
  const filtered = useMemo(() => {
    let result = cdr;
    if (statusFilter) {
      result = result.filter((r) => r.status.toUpperCase() === statusFilter);
    }
    if (gatewayFilter) {
      result = result.filter((r) => r.gatewayHost === gatewayFilter);
    }
    return result;
  }, [cdr, statusFilter, gatewayFilter]);

  // تهيئة ECharts
  const [echartsReady, setEchartsReady] = useState(false);
  const [echartsMod, setEchartsMod] = useState<any>(null);
  const [echartsForReact, setEchartsForReact] = useState<any>(null);

  useEffect(() => {
    Promise.all([import('echarts'), import('echarts-for-react')]).then(([ec, efr]) => {
      setEchartsMod(ec);
      setEchartsForReact(efr.default || efr);
      setEchartsReady(true);
    }).catch(() => {
      // ECharts غير متوفر — نعرض الإحصائيات النصية فقط
    });
  }, []);

  // رسم بياني — المكالمات اليومية (آخر 30 يومًا، مجمَّعة في القاعدة)
  const dailyChartOption = useMemo(() => {
    if (!echartsMod || daily.length === 0) return null;
    return {
      tooltip: { trigger: 'axis' },
      legend: { data: ['مُجابة', 'غير مُجابة'] },
      xAxis: { type: 'category', data: daily.map((d) => d.date) },
      yAxis: { type: 'value', name: 'عدد المكالمات' },
      series: [
        {
          name: 'مُجابة',
          type: 'bar',
          stack: 'calls',
          data: daily.map((d) => d.succeeded),
          itemStyle: { color: '#1976D2' },
        },
        {
          name: 'غير مُجابة',
          type: 'bar',
          stack: 'calls',
          data: daily.map((d) => d.failed),
          itemStyle: { color: '#E0A83C' },
        },
      ],
    };
  }, [echartsMod, daily]);

  // رسم بياني — توزيع المشغلين
  const operatorChartOption = useMemo(() => {
    if (!echartsMod || operatorDist.length === 0) return null;
    return {
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', right: 10, top: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 8, borderColor: '#06110D', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: operatorDist.map((d) => ({ name: d.operator, value: d.count })),
      }],
    };
  }, [echartsMod, operatorDist]);

  // رسم بياني — توزيع البوابات
  const gatewayChartOption = useMemo(() => {
    if (!echartsMod || gatewayDist.length === 0) return null;
    return {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: gatewayDist.map((d) => d.gateway),
        axisLabel: { rotate: 20, fontSize: 10 },
      },
      yAxis: { type: 'value', name: 'عدد المكالمات' },
      series: [{
        type: 'bar',
        data: gatewayDist.map((d) => d.count),
        itemStyle: { color: '#B78A2E', borderRadius: [6, 6, 0, 0] },
      }],
    };
  }, [echartsMod, gatewayDist]);

  const columns = [
    {
      title: 'الاتجاه',
      dataIndex: 'direction',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'INBOUND' ? 'gold' : 'blue'}>
          {DIRECTION_LABELS[v] || v}
        </Tag>
      ),
    },
    {
      title: 'الرقم',
      dataIndex: 'number',
      width: 120,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: 'البوابة',
      dataIndex: 'gatewayHost',
      width: 130,
    },
    {
      title: 'المنفذ',
      dataIndex: 'portIndex',
      width: 70,
      render: (v: number) => `SIM ${v + 1}`,
    },
    {
      title: 'المشغل',
      dataIndex: 'operator',
      width: 100,
      render: (v: string) => v || '—',
    },
    {
      title: 'البدء',
      dataIndex: 'startTime',
      width: 150,
      render: (v: string) => v ? new Date(v).toLocaleString('ar') : '—',
    },
    {
      title: 'المدة',
      dataIndex: 'duration',
      width: 80,
      render: (v: number) => {
        if (!v) return '—';
        const m = Math.floor(v / 60);
        const s = v % 60;
        return m > 0 ? `${m}:${s.toString().padStart(2, '0')}` : `${s}ث`;
      },
    },
    {
      title: 'الحالة',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => (
        <Tag color={STATUS_COLORS[v?.toUpperCase()] || 'default'}>
          {STATUS_LABELS[v?.toUpperCase()] || v}
        </Tag>
      ),
    },
  ];

  return (
    <div>
      {/* تعذّر الملخّص: الأرقام محسوبة من العيّنة المُحمَّلة لا من كل الصفوف */}
      {stats.partial && cdr.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="الإحصاءات محسوبة من المكالمات المعروضة فقط"
          description="تعذّر جلب الملخّص من الخادم، فالأرقام أدناه تخصّ العيّنة المُحمَّلة لا كل السجل."
        />
      )}

      {/* إحصائيات */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="إجمالي المكالمات"
              value={stats.total}
              prefix={<PhoneOutlined />}
              valueStyle={{ color: '#B78A2E' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="مُجابة"
              value={stats.succeeded}
              valueStyle={{ color: '#1976D2' }}
              suffix={`/ ${stats.answerRate}%`}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="متوسط مدة المُجابة"
              value={stats.avgDuration}
              prefix={<ClockCircleOutlined />}
              suffix="ث"
              valueStyle={{ color: '#4FC3F7' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="صادرة / واردة"
              value={`${stats.outbound} / ${stats.inbound}`}
              prefix={<SwapOutlined />}
              valueStyle={{ color: '#E0A83C' }}
            />
          </Card>
        </Col>
      </Row>

      {/* المكالمات اليومية */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24}>
          <Card title="المكالمات اليومية (آخر 30 يومًا)" size="small">
            {echartsReady && dailyChartOption
              ? React.createElement(echartsForReact, { option: dailyChartOption, style: { height: 240 } })
            : daily.length > 0 ? (
              <Table
                size="small"
                dataSource={daily}
                columns={[
                  { title: 'اليوم', dataIndex: 'date' },
                  { title: 'المكالمات', dataIndex: 'calls', width: 90 },
                  { title: 'مُجابة', dataIndex: 'succeeded', width: 80 },
                  { title: 'غير مُجابة', dataIndex: 'failed', width: 90 },
                ]}
                pagination={false}
                rowKey="date"
              />
            ) : <Empty description="لا توجد بيانات" />}
          </Card>
        </Col>
      </Row>

      {/* رسوم بيانية */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card title="توزيع المشغلين" size="small">
            {echartsReady && operatorChartOption
              ? React.createElement(echartsForReact, { option: operatorChartOption, style: { height: 240 } })
            : operatorDist.length > 0 ? (
              <Table
                size="small"
                dataSource={operatorDist}
                columns={[
                  { title: 'المشغل', dataIndex: 'operator' },
                  { title: 'المكالمات', dataIndex: 'count', width: 80 },
                ]}
                pagination={false}
                rowKey="operator"
              />
            ) : <Empty description="لا توجد بيانات" />}
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="توزيع البوابات" size="small">
            {echartsReady && gatewayChartOption
              ? React.createElement(echartsForReact, { option: gatewayChartOption, style: { height: 240 } })
            : gatewayDist.length > 0 ? (
              <Table
                size="small"
                dataSource={gatewayDist}
                columns={[
                  { title: 'البوابة', dataIndex: 'gateway' },
                  { title: 'المكالمات', dataIndex: 'count', width: 80 },
                ]}
                pagination={false}
                rowKey="gateway"
              />
            ) : <Empty description="لا توجد بيانات" />}
          </Card>
        </Col>
      </Row>

      {/* جدول المكالمات */}
      <Card
        title="سجل المكالمات التفصيلي"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="البوابة"
              value={gatewayFilter}
              onChange={setGatewayFilter}
              style={{ width: 160 }}
              options={gateways.map((g) => ({ value: g, label: g }))}
            />
            <Select
              allowClear
              placeholder="الحالة"
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 140 }}
              // نفس مفردات قيد CHECK على `dinstar_cdr.status`. الخيارات
              // السابقة (COMPLETED/REJECTED) من مفردات نتيجة التوجيه، فلم
              // تُطابق صفًّا واحدًا وكان كل تصفية تُفرِّغ الجدول.
              options={[
                { value: 'ANSWERED', label: 'مُجابة' },
                { value: 'NO_ANSWER', label: 'لا رد' },
                { value: 'BUSY', label: 'مشغول' },
                { value: 'CANCELLED', label: 'ملغاة' },
                { value: 'FAILED', label: 'فشل' },
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>تحديث</Button>
          </Space>
        }
      >
        <Table
          dataSource={filtered}
          columns={columns}
          rowKey="id"
          loading={loading}
          size="small"
          scroll={{ x: 900 }}
          pagination={{ pageSize: 15, showSizeChanger: true, showTotal: (t) => `إجمالي ${t} مكالمة` }}
          locale={{ emptyText: <Empty description="لا توجد مكالمات مسجّلة" /> }}
        />
      </Card>
    </div>
  );
}
