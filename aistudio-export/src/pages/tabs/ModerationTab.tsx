import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import { apiFetch } from '../../api';

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

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const r = await apiFetch('/api/admin/moderation/reports?status=OPEN');
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

  useEffect(() => { void load(); }, []);

  const resolve = async (id: string, status: 'RESOLVED' | 'DISMISSED') => {
    const r = await apiFetch(`/api/admin/moderation/reports/${id}?status=${status}`, { method: 'PATCH' });
    if (!r.ok) {
      message.error(`HTTP ${r.status}`);
      return;
    }
    message.success(status === 'RESOLVED' ? 'تمت معالجة البلاغ' : 'تم رفض البلاغ');
    void load();
  };

  return (
    <Card title="الثقة والسلامة" extra={<Button onClick={load} loading={loading}>تحديث</Button>}>
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
                <Button type="primary" onClick={() => resolve(r.id, 'RESOLVED')}>تمت المعالجة</Button>
                <Button onClick={() => resolve(r.id, 'DISMISSED')}>رفض البلاغ</Button>
              </Space>
            ),
          },
        ]}
      />
    </Card>
  );
}
