import { useState, useEffect } from 'react';
import { getNotifications, markNotificationRead, markAllNotificationsRead, getUnreadCount } from '../../api';

/**
 * 🔔 YOUNES Sovereign Notifications Tab — لوحة الإشعارات
 */
export default function NotificationsTab() {
  const [notifications, setNotifications] = useState<any[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [filter, setFilter] = useState<string>('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    loadNotifications();
    loadUnreadCount();
  }, [page, filter]);

  const loadNotifications = async () => {
    try {
      const data = await getNotifications(page, 50, filter || undefined);
      setNotifications(data.notifications || []);
    } catch {}
  };

  const loadUnreadCount = async () => {
    try {
      const data = await getUnreadCount();
      setUnreadCount(data.count || 0);
    } catch {}
  };

  const handleMarkRead = async (id: string) => {
    await markNotificationRead(id);
    loadNotifications();
    loadUnreadCount();
  };

  const handleMarkAllRead = async () => {
    await markAllNotificationsRead();
    loadNotifications();
    loadUnreadCount();
  };

  const typeColors: Record<string, string> = {
    NEW_MESSAGE: '#1E88E5', GROUP_MESSAGE: '#4CAF50', MENTION: '#38BDF8',
    INCOMING_CALL: '#1E88E5', MISSED_CALL: '#EF4444', PSTN_CALL: '#F4B400',
    STORY_VIEW: '#38BDF8', STORY_REPLY: '#1E88E5',
    GROUP_INVITE: '#4CAF50', GROUP_UPDATE: '#9E9E9E', ROLE_CHANGE: '#F59E0B',
    LIVE_STARTED: '#E53935', SPACE_STARTED: '#8E24AA',
    SECURITY_ALERT: '#EF4444', DEVICE_NEW: '#F59E0B', UPDATE_AVAILABLE: '#38BDF8',
    DINSTAR_STATUS: '#F4B400', DINSTAR_ALERT: '#EF4444'
  };

  const typeLabels: Record<string, string> = {
    NEW_MESSAGE: 'رسالة', GROUP_MESSAGE: 'رسالة مجموعة', MENTION: 'إشارة',
    INCOMING_CALL: 'مكالمة واردة', MISSED_CALL: 'مكالمة فائتة', PSTN_CALL: 'مكالمة خطية',
    STORY_VIEW: 'مشاهدة قصة', STORY_REPLY: 'رد قصة',
    GROUP_INVITE: 'دعوة مجموعة', GROUP_UPDATE: 'تحديث مجموعة', ROLE_CHANGE: 'تغيير دور',
    LIVE_STARTED: 'بث مباشر', SPACE_STARTED: 'غرفة صوتية',
    SECURITY_ALERT: 'تنبيه أمني', DEVICE_NEW: 'جهاز جديد', UPDATE_AVAILABLE: 'تحديث',
    DINSTAR_STATUS: 'Dinstar', DINSTAR_ALERT: 'تنبيه Dinstar'
  };

  const filters = [
    { key: '', label: 'الكل' },
    { key: 'NEW_MESSAGE', label: 'الرسائل' },
    { key: 'INCOMING_CALL', label: 'المكالمات' },
    { key: 'GROUP_INVITE', label: 'المجموعات' },
    { key: 'SECURITY_ALERT', label: 'الأمان' }
  ];

  return (
    <div className="space-y-4" dir="rtl">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white">الإشعارات</h2>
          {unreadCount > 0 && (
            <p className="text-sm text-cyan-400">{unreadCount} غير مقروء</p>
          )}
        </div>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAllRead}
            className="text-sm text-cyan-400 hover:text-cyan-300"
          >
            قراءة الكل
          </button>
        )}
      </div>

      {/* Filters */}
      <div className="flex gap-2 flex-wrap">
        {filters.map(f => (
          <button
            key={f.key}
            onClick={() => { setFilter(f.key); setPage(0); }}
            className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
              filter === f.key
                ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40'
                : 'bg-slate-800 text-gray-400 border border-slate-700 hover:border-slate-600'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Notifications List */}
      {notifications.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <div className="text-4xl mb-4">🔔</div>
          <p>لا توجد إشعارات</p>
        </div>
      ) : (
        <div className="space-y-2">
          {notifications.map((notif: any) => {
            const color = typeColors[notif.type] || '#9E9E9E';
            const label = typeLabels[notif.type] || notif.type;
            return (
              <div
                key={notif.id}
                onClick={() => !notif.isRead && handleMarkRead(notif.id)}
                className={`rounded-xl p-3 border transition-all cursor-pointer ${
                  notif.isRead
                    ? 'bg-slate-900/30 border-slate-800/50'
                    : 'border-opacity-60'
                }`}
                style={!notif.isRead ? {
                  backgroundColor: `${color}08`,
                  borderColor: `${color}30`
                } : {}}
              >
                <div className="flex items-start gap-3">
                  {/* Type badge */}
                  <div
                    className="w-10 h-10 rounded-full flex items-center justify-center text-sm flex-shrink-0"
                    style={{ backgroundColor: `${color}18` }}
                  >
                    <span style={{ color }}>{getEmoji(notif.type)}</span>
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-white text-sm truncate">{notif.title}</span>
                      {!notif.isRead && (
                        <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: color }} />
                      )}
                    </div>
                    <p className="text-gray-400 text-xs mt-1 line-clamp-2">{notif.body}</p>
                    <div className="flex items-center gap-2 mt-1">
                      <span
                        className="text-[10px] px-1.5 py-0.5 rounded font-medium"
                        style={{ backgroundColor: `${color}18`, color }}
                      >
                        {label}
                      </span>
                      <span className="text-gray-600 text-[10px]">
                        {formatTimeAgo(notif.createdAt)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function getEmoji(type: string): string {
  const map: Record<string, string> = {
    NEW_MESSAGE: '💬', GROUP_MESSAGE: '👥', MENTION: '📢',
    INCOMING_CALL: '📞', MISSED_CALL: '📵', PSTN_CALL: '📱',
    STORY_VIEW: '👁️', STORY_REPLY: '💬',
    GROUP_INVITE: '👥', GROUP_UPDATE: 'ℹ️', ROLE_CHANGE: '🛡️',
    LIVE_STARTED: '🔴', SPACE_STARTED: '🎙️',
    SECURITY_ALERT: '🔒', DEVICE_NEW: '📱', UPDATE_AVAILABLE: '🔄',
    DINSTAR_STATUS: '📡', DINSTAR_ALERT: '⚠️'
  };
  return map[type] || '🔔';
}

function formatTimeAgo(timestamp: string): string {
  const diff = Date.now() - new Date(timestamp).getTime();
  if (diff < 60000) return 'الآن';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}د`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}س`;
  return `${Math.floor(diff / 86400000)}ي`;
}
