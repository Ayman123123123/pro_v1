import { useEffect, useState } from 'react';
import { Alert, Badge, Button, Empty, List, Space, Tag, Typography } from 'antd';
import { BellOutlined, CheckOutlined } from '@ant-design/icons';
import { getNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '../../api';

const typeColors: Record<string, string> = {
  NEW_MESSAGE: 'blue', GROUP_MESSAGE: 'green', MENTION: 'cyan',
  INCOMING_CALL: 'blue', MISSED_CALL: 'red', PSTN_CALL: 'gold',
  STORY_VIEW: 'cyan', STORY_REPLY: 'blue',
  GROUP_INVITE: 'green', GROUP_UPDATE: 'default', ROLE_CHANGE: 'orange',
  LIVE_STARTED: 'red', SPACE_STARTED: 'purple',
  SECURITY_ALERT: 'red', DEVICE_NEW: 'orange', UPDATE_AVAILABLE: 'cyan',
  DINSTAR_STATUS: 'gold', DINSTAR_ALERT: 'red',
  APPROVAL: 'gold', SECURITY: 'red', SYSTEM: 'blue',
};

const typeLabels: Record<string, string> = {
  NEW_MESSAGE: 'رسالة', GROUP_MESSAGE: 'رسالة مجموعة', MENTION: 'إشارة',
  INCOMING_CALL: 'مكالمة واردة', MISSED_CALL: 'مكالمة فائتة', PSTN_CALL: 'مكالمة خطية',
  STORY_VIEW: 'مشاهدة قصة', STORY_REPLY: 'رد قصة',
  GROUP_INVITE: 'دعوة مجموعة', GROUP_UPDATE: 'تحديث مجموعة', ROLE_CHANGE: 'تغيير دور',
  LIVE_STARTED: 'بث مباشر', SPACE_STARTED: 'غرفة صوتية',
  SECURITY_ALERT: 'تنبيه أمني', DEVICE_NEW: 'جهاز جديد', UPDATE_AVAILABLE: 'تحديث',
  DINSTAR_STATUS: 'Dinstar', DINSTAR_ALERT: 'تنبيه Dinstar',
  APPROVAL: 'موافقة', SECURITY: 'أمان', SYSTEM: 'نظام',
};

const filters = [
  { key: '', label: 'الكل' },
  { key: 'NEW_MESSAGE', label: 'الرسائل' },
  { key: 'INCOMING_CALL', label: 'المكالمات' },
  { key: 'GROUP_INVITE', label: 'المجموعات' },
  { key: 'SECURITY_ALERT', label: 'الأمان' },
];

function formatTimeAgo(timestamp: string): string {
  const diff = Date.now() - new Date(timestamp).getTime();
  if (!Number.isFinite(diff) || diff < 0) return '—';
  if (diff < 60000) return 'الآن';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}د`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}س`;
  return `${Math.floor(diff / 86400000)}ي`;
}

export default function NotificationsTab() {
  const [notifications, setNotifications] = useState<any[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const [data, unread] = await Promise.all([
        getNotifications(0, 50, filter || undefined),
        getUnreadCount().catch(() => ({ count: 0 })),
      ]);
      setNotifications(Array.isArray(data.notifications) ? data.notifications : []);
      setUnreadCount(Number(unread?.count ?? data.unreadCount ?? 0));
      setError('');
    } catch (e: any) {
      setError(e.message || 'تعذر تحميل الإشعارات');
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [filter]);

  const handleMarkRead = async (id: string) => {
    try {
      await markNotificationRead(id);
      void load();
    } catch (e: any) {
      setError(e?.message || 'تعذر تعليم الإشعار كمقروء');
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsRead();
      void load();
    } catch (e: any) {
      setError(e?.message || 'تعذر تعليم كل الإشعارات');
    }
  };

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <Space>
          <BellOutlined style={{ color: '#14D89B' }} />
          <Typography.Text strong>صندوق الإشعارات</Typography.Text>
          {unreadCount > 0 && <Badge count={unreadCount} color="#00C896" />}
        </Space>
        {unreadCount > 0 && (
          <Button size="small" icon={<CheckOutlined />} onClick={handleMarkAllRead}>
            قراءة الكل
          </Button>
        )}
      </div>

      <Space wrap>
        {filters.map((item) => (
          <Button
            key={item.key || 'all'}
            type={filter === item.key ? 'primary' : 'default'}
            size="small"
            onClick={() => setFilter(item.key)}
          >
            {item.label}
          </Button>
        ))}
        <Button size="small" onClick={load} loading={loading}>تحديث</Button>
      </Space>

      {error && <Alert type="error" showIcon message={error} />}

      {notifications.length === 0 ? (
        <Empty description="لا توجد إشعارات" />
      ) : (
        <List
          loading={loading}
          dataSource={notifications}
          renderItem={(notif) => (
            <List.Item
              style={{
                cursor: notif.isRead ? 'default' : 'pointer',
                background: notif.isRead ? 'transparent' : 'rgba(0,201,150,0.06)',
                borderRadius: 10,
                paddingInline: 12,
              }}
              onClick={() => { if (!notif.isRead) void handleMarkRead(notif.id); }}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Typography.Text strong>{notif.title}</Typography.Text>
                    {!notif.isRead && <Badge status="processing" />}
                    <Tag color={typeColors[notif.type] || 'default'}>{typeLabels[notif.type] || notif.type}</Tag>
                  </Space>
                }
                description={
                  <Space direction="vertical" size={0}>
                    <Typography.Text type="secondary">{notif.body}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                      {notif.createdAt ? formatTimeAgo(notif.createdAt) : '—'}
                    </Typography.Text>
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      )}
    </Space>
  );
}
