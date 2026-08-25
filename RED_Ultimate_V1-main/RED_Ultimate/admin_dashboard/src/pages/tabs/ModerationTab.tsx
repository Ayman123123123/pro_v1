import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Select, Space, Table, Tag, Typography, message } from 'antd';
import { apiFetch } from '../../api';

type ReportStatus = 'OPEN' | 'REVIEWING' | 'RESOLVED' | 'DISMISSED';

type Report = {
  id: string;
  reporterRedId: string;
  reportedRedId?: string;
  category: string;
  details?: string;
  status: string;
  createdAt: string;
};

export default function ModerationTab() {
  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState<ReportStatus>('OPEN');

  const load = async (nextStatus: ReportStatus = status) => {
    setLoading(true);
    setError('');
    try {
      const r = await apiFetch(`/api/admin/moderation/reports?status=${nextStatus}`);
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const body = await r.json();
      setReports(Array.isArray(body) ? body : []);
    } catch (e: any) {
      setError(e.message || 'تعذر تحميل البلاغات');
      setReports([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(status); }, [status]);

  const updateStatus = async (id: string, nextStatus: Exclude<ReportStatus, 'OPEN'>) => {
    const r = await apiFetch(`/api/admin/moderation/reports/${id}?status=${nextStatus}`, { method: 'PATCH' });
    if (!r.ok) {
      message.error(`HTTP ${r.status}`);
      return;
    }
    const success = nextStatus === 'REVIEWING' ? 'نُقل البلاغ إلى المراجعة' : nextStatus === 'RESOLVED' ? 'تمت معالجة البلاغ' : 'تم رفض البلاغ';
    message.success(success);
    void load();
  };

  return (
    <Card
      title="الثقة والسلامة"
      extra={
        <Space>
          <Select<ReportStatus>
            value={status}
            onChange={setStatus}
            style={{ minWidth: 150 }}
            options={[
              { value: 'OPEN', label: 'مفتوحة' },
              { value: 'REVIEWING', label: 'تحت المراجعة' },
              { value: 'RESOLVED', label: 'تمت المعالجة' },
              { value: 'DISMISSED', label: 'مرفوضة' },
            ]}
          />
          <Button onClick={() => void load()} loading={loading}>تحديث</Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary">
        بلاغات المستخدمين الحقيقية؛ لا تُنفذ عقوبة تلقائية دون مراجعة مسؤول.
      </Typography.Paragraph>
      {error && <Alert type="error" message={error} style={{ marginBottom: 12 }} />}
      <Table
        rowKey="id"
        loading={loading}
        dataSource={reports}
        locale={{ emptyText: 'لا توجد بلاغات مفتوحة' }}
        columns={[
          { title: 'الفئة', dataIndex: 'category', render: (v: string) => <Tag color="red">{v}</Tag> },
          { title: 'المُبلّغ', dataIndex: 'reporterRedId', render: (v?: string) => v || '—' },
          { title: 'الحساب المُبلّغ عنه', dataIndex: 'reportedRedId', render: (v?: string) => v || '—' },
          { title: 'التفاصيل', dataIndex: 'details', render: (v?: string) => v || '—' },
          { title: 'الوقت', dataIndex: 'createdAt', render: (v: string) => (v ? new Date(v).toLocaleString('ar') : '—') },
          {
            title: 'الإجراء',
            render: (_: unknown, r: Report) => (
              <Space>
                {r.status === 'OPEN' && <Button onClick={() => updateStatus(r.id, 'REVIEWING')}>بدء المراجعة</Button>}
                {(r.status === 'OPEN' || r.status === 'REVIEWING') && <Button type="primary" onClick={() => updateStatus(r.id, 'RESOLVED')}>تمت المعالجة</Button>}
                {(r.status === 'OPEN' || r.status === 'REVIEWING') && <Button danger onClick={() => updateStatus(r.id, 'DISMISSED')}>رفض البلاغ</Button>}
              </Space>
            ),
          },
        ]}
      />
    </Card>
  );
}
