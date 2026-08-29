import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import NotificationsTab from './tabs/NotificationsTab';

// 🔴 مدموج من NotificationsTab القديمة — صفحة مستقلة موحدة — بيانات حقيقية 100%
export default function NotificationsCenter() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#D4B16A', margin:0}}><BellOutlined /> مركز الإشعارات — موحد</Typography.Title>
      <Typography.Text type="secondary">كل إشعارات المنصة — مدموج من NotificationsTab القديمة — الآن صفحة مستقلة ببيانات حقيقية</Typography.Text>
      <Alert type="info" showIcon style={{margin:'16px 0'}} message="البيانات حية من /api/notifications — لا بيانات وهمية" description="الفلتر: الكل/رسائل/مكالمات/مجموعات/أمان — النقر يعلّم كمقروء — العداد يتحدث تلقائياً" />
      <Card style={{marginTop:16}}>
        <NotificationsTab />
      </Card>
    </div>
  );
}
