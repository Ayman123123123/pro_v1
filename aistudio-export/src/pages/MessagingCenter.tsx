import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import MessagingTab from './tabs/MessagingTab';

// 🔴 مدموج من MessagingTab القديمة — صفحة مستقلة بالشكل الجديد — بيانات حقيقية
export default function MessagingCenter() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#00E6A0', margin:0}}><MessageOutlined /> مركز الرسائل — موحد</Typography.Title>
      <Typography.Text type="secondary">إحصائيات الرسائل المشفرة — مدموج من MessagingTab القديمة — الآن صفحة مستقلة بالشكل الجديد — بيانات حية من /api/master/v1/stats/realtime</Typography.Text>
      <Alert type="success" showIcon style={{margin:'16px 0'}} message="البيانات مجمعة فقط — لا تعرض محتوى الرسائل أو هوية المرسل — الخصوصية محفوظة" />
      <Card style={{marginTop:16}}>
        <MessagingTab />
      </Card>
    </div>
  );
}
