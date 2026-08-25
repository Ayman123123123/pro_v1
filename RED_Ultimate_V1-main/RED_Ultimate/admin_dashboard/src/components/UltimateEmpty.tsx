import React from 'react';
import { Empty, Button, Typography } from 'antd';
import { InboxOutlined, SearchOutlined, TeamOutlined, MessageOutlined, PhoneOutlined, SafetyOutlined } from '@ant-design/icons';

const { Text } = Typography;

type Variant = 'default' | 'search' | 'contacts' | 'messages' | 'calls' | 'security';

const CONFIG: Record<Variant, { icon: React.ReactNode; title: string; desc: string }> = {
  default: { icon: <InboxOutlined style={{ fontSize: 48, color: '#334155' }} />, title: 'لا توجد بيانات', desc: 'سيظهر المحتوى هنا عند توفره' },
  search: { icon: <SearchOutlined style={{ fontSize: 48, color: '#475569' }} />, title: 'لا نتائج', desc: 'جرب كلمات مختلفة أو امسح الفلاتر' },
  contacts: { icon: <TeamOutlined style={{ fontSize: 48, color: '#0ea5e9' }} />, title: 'لا جهات اتصال', desc: 'أضف جهات أو استورد من vCard' },
  messages: { icon: <MessageOutlined style={{ fontSize: 48, color: '#10b981' }} />, title: 'لا رسائل', desc: 'ابدأ محادثة جديدة' },
  calls: { icon: <PhoneOutlined style={{ fontSize: 48, color: '#8b5cf6' }} />, title: 'لا مكالمات', desc: 'سجل مكالماتك سيظهر هنا' },
  security: { icon: <SafetyOutlined style={{ fontSize: 48, color: '#f59e0b' }} />, title: 'آمن', desc: 'لا تهديدات مكتشفة' },
};

export default function UltimateEmpty({ variant = 'default', actionText, onAction, description }: { variant?: Variant; actionText?: string; onAction?: () => void; description?: string }) {
  const c = CONFIG[variant];
  return (
    <Empty
      image={c.icon}
      description={
        <div>
          <div style={{ fontWeight: 700, color: '#e2e8f0' }}>{c.title}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>{description || c.desc}</Text>
        </div>
      }
      style={{ padding: 32 }}
    >
      {actionText && onAction && <Button type="primary" onClick={onAction}>{actionText}</Button>}
    </Empty>
  );
}
