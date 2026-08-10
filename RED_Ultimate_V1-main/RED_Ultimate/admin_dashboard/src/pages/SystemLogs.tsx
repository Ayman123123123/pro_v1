import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { FileSearchOutlined } from '@ant-design/icons';
import LogStreamerTab from './tabs/LogStreamerTab';

// 🔴 مدموج من LogStreamerTab القديمة — صفحة مستقلة — WebSocket حقيقي
export default function SystemLogs() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#00E6A0', margin:0}}><FileSearchOutlined /> سجل النظام المباشر — موحد</Typography.Title>
      <Typography.Text type="secondary">بث حي عبر WebSocket /ws/admin/logs — مدموج من LogStreamerTab القديمة (داخل MasterLayout) — بيانات حقيقية</Typography.Text>
      <Alert type="warning" showIcon style={{margin:'16px 0'}} message="السجل لا يعرض محتوى الرسائل المشفرة أو كلمات المرور — فقط routing metadata وأحداث النظام" />
      <Card style={{marginTop:16}}>
        <LogStreamerTab />
      </Card>
    </div>
  );
}
