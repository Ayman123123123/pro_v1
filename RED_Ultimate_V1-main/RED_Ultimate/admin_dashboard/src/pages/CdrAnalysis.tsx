import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Empty, Row, Select,
  Space, Statistic, Table, Tag, Typography, message,
} from 'antd';
import {
  BarChartOutlined, ClockCircleOutlined, DollarOutlined, PhoneOutlined,
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
  startTime: string;
  duration: number;
  status: string;
  operator: string;
  cost?: number;
};

type DailyStats = {
  date: string;
  calls: number;
  duration: number;
  succeeded: number;
  failed: number;
};

const { RangePicker } = DatePicker;

const STATUS_COLORS: Record<string, string> = {
  COMPLETED: 'success',
  ANSWERED: 'success',
  NO_ANSWER: 'warning',
  BUSY: 'warning',
  FAILED: 'error',
  REJECTED: 'error',
};

const DIRECTION_LABELS: Record<string, string> = {
  INBOUND: 'واردة',
  OUTBOUND: 'صادرة',
};

export default function CdrAnalysis() {
  const [cdr, setCdr] = useState<CdrRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState<[any, any] | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const [gatewayFilter, setGatewayFilter] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/api/admin/dinstar/cdr/analysis');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setCdr(Array.isArray(data) ? data : (data.records || []));
    } catch (e: any) {
      message.error(e.message || 'تعذر تحميل سجل المكالمات');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // الإحصائيات
  const stats = useMemo(() => {
    const total = cdr.length;
    const succeeded = cdr.filter((r) => ['COMPLETED', 'ANSWERED'].includes(r.status.toUpperCase())).length;
    const failed = total - succeeded;
    const totalDuration = cdr.reduce((s, r) => s + (r.duration || 0), 0);
    const avgDuration = total > 0 ? Math.round(totalDuration / total) : 0;
    const totalCost = cdr.reduce((s, r) => s + (r.cost || 0), 0);
    const inbound = cdr.filter((r) => r.direction === 'INBOUND').length;
    const outbound = cdr.filter((r) => r.direction === 'OUTBOUND').length;

    return { total, succeeded, failed, avgDuration, totalCost, inbound, outbound };
  }, [cdr]);

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

  // اسم محلي بحرف كبير — JSX يتطلب أن تبدأ مكوّنات الحالة بحرف كبير
  // (الاسم الصغير `<echartsForReact>` كان يُعامل كعنصر HTML أصلي فيفشل tsc)
  const EChartsForReact = echartsForReact;

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
        itemStyle: { borderRadius: 8, borderColor: '#050A16', borderWidth: 2 },
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
        itemStyle: { color: '#00C896', borderRadius: [6, 6, 0, 0] },
      }],
    };
  }, [echartsMod, gatewayDist]);

  const columns = [
    {
      title: 'الاتجاه',
      dataIndex: 'direction',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'INBOUND' ? 'green' : 'blue'}>
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
        <Tag color={STATUS_COLORS[v.toUpperCase()] || 'default'}>
          {v}
        </Tag>
      ),
    },
  ];

  return (
    <div>
      {/* إحصائيات */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="إجمالي المكالمات"
              value={stats.total}
              prefix={<PhoneOutlined />}
              valueStyle={{ color: '#00C896' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="ناجحة"
              value={stats.succeeded}
              valueStyle={{ color: '#52C41A' }}
              suffix={`/ ${stats.total > 0 ? Math.round(stats.succeeded / stats.total * 100) : 0}%`}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="متوسط المدة"
              value={stats.avgDuration}
              prefix={<ClockCircleOutlined />}
              suffix="ث"
              valueStyle={{ color: '#35CBE0' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={6}>
          <Card size="small">
            <Statistic
              title="صادرة / واردة"
              value={`${stats.outbound} / ${stats.inbound}`}
              prefix={<SwapOutlined />}
              valueStyle={{ color: '#E8B84A' }}
            />
          </Card>
        </Col>
      </Row>

      {/* رسوم بيانية */}
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12}>
          <Card title="توزيع المشغلين" size="small">
            {echartsReady && operatorChartOption && EChartsForReact ? (
              <EChartsForReact option={operatorChartOption} style={{ height: 240 }} />
            ) : operatorDist.length > 0 ? (
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
            {echartsReady && gatewayChartOption && EChartsForReact ? (
              <EChartsForReact option={gatewayChartOption} style={{ height: 240 }} />
            ) : gatewayDist.length > 0 ? (
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
              options={[
                { value: 'COMPLETED', label: 'ناجحة' },
                { value: 'ANSWERED', label: 'مُجابة' },
                { value: 'NO_ANSWER', label: 'لا رد' },
                { value: 'BUSY', label: 'مشغول' },
                { value: 'FAILED', label: 'فشل' },
                { value: 'REJECTED', label: 'مرفوض' },
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
