import { useCallback, useState } from 'react';
import { Alert, Avatar, Card, List, Skeleton, Space, Statistic, Tag, Tooltip, Typography } from 'antd';
import { ReloadOutlined, WifiOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import { getPresenceOnlineWithFallback } from '../api';
import { usePolling } from '../hooks/usePolling';

const { Text } = Typography;

/**
 * بطاقة المتصلين اللحظيين — GET /api/admin/presence/online مع سقوط أنيق.
 *
 * - تُحدَّث دوريًا كل 15 ثانية (وتتوقف تلقائيًا عند إخفاء التبويب عبر usePolling).
 * - إن غاب المسار بعد (وكيل الباك-إند يبنيه) تعرض رسالة عربية واضحة وتسقط
 *   على /api/admin/users/online العامل مع شارة «بديل» بدل الشاشة الفارغة.
 * - لا أزرار صامتة: زر التحديث يبلغ عن الفشل برسالة، والقائمة الفارغة تشرح السبب.
 */
export default function OnlinePresenceCard({ compact = false }: { compact?: boolean }) {
  const [users, setUsers] = useState<any[]>([]);
  const [count, setCount] = useState(0);
  const [source, setSource] = useState<'presence' | 'fallback'>('presence');
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const result = await getPresenceOnlineWithFallback();
      setUsers(result.users);
      setCount(result.count);
      setSource(result.source);
      setNote(result.note);
      setError('');
    } catch (e: any) {
      setError(e?.message || 'تعذّر تحميل المتصلين اللحظيين');
    } finally {
      setLoading(false);
    }
  }, []);

  // تحديث دوري كل 15 ثانية — يتوقف تلقائيًا عند إخفاء التبويب أو انقطاع الشبكة.
  usePolling(load, 15000);

  const online = users.slice(0, compact ? 5 : 8);

  return (
    <Card
      title={
        <Space>
          <WifiOutlined style={{ color: '#00E676' }} />
          <span>المتصلون اللحظيون</span>
          {source === 'presence' ? (
            <Tag color="success">مباشر</Tag>
          ) : (
            <Tooltip title={note || 'المسار الجديد قيد البناء'}>
              <Tag color="warning">بديل /users/online</Tag>
            </Tooltip>
          )}
        </Space>
      }
      extra={
        <Tooltip title="تحديث قائمة المتصلين الآن">
          <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>
            تحديث
          </Button>
        </Tooltip>
      }
    >
      <Statistic
        title={<Text style={{ color: '#10B981', fontWeight: 'bold' }}>● أونلاين الآن</Text>}
        value={count}
        loading={loading && users.length === 0}
        prefix={<WifiOutlined style={{ color: '#00E676' }} />}
        valueStyle={{ color: '#00E676', fontWeight: 'bold' }}
      />
      {source === 'fallback' && note && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 12 }}
          message="مسار الحضور الجديد قيد البناء"
          description={note}
        />
      )}
      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginTop: 12 }}
          message="تعذّر تحميل المتصلين"
          description={error}
          action={
            <Button size="small" onClick={() => void load()}>
              إعادة المحاولة
            </Button>
          }
        />
      )}
      {loading && users.length === 0 && !error && (
        <Skeleton active avatar paragraph={{ rows: 3 }} style={{ marginTop: 12 }} />
      )}
      {!error && !(loading && users.length === 0) && (
        <List
          style={{ marginTop: 12 }}
          size="small"
          locale={{ emptyText: 'لا يوجد متصلون حاليًا' }}
          dataSource={online}
          renderItem={(u: any) => (
            <List.Item>
              <Space>
                <Avatar size="small" style={{ backgroundColor: '#1890FF' }}>
                  {String(u.displayName || u.username || '؟').trim().charAt(0).toUpperCase()}
                </Avatar>
                <Text strong>{u.displayName || u.username || '—'}</Text>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  @{u.username || '—'} · {u.redId || '—'}
                </Text>
              </Space>
            </List.Item>
          )}
          footer={
            count > online.length ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                +{count - online.length} آخرون متصلون — افتح «المستخدمون» للقائمة الكاملة
              </Text>
            ) : undefined
          }
        />
      )}
    </Card>
  );
}
