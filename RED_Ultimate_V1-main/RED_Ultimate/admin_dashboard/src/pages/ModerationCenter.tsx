import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import ModerationTab from './tabs/ModerationTab';

// 🔴 مدموج من ModerationTab القديمة — صفحة مستقلة بالشكل الجديد — بيانات حقيقية
// ملاحظة: Reports.tsx هو المركز المتقدم للبلاغات — هذه الصفحة للتوافق والبلاغات المفتوحة السريعة
export default function ModerationCenter() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#D4B16A', margin:0}}><SafetyCertificateOutlined /> الإشراف السريع — موحد</Typography.Title>
      <Typography.Text type="secondary">بلاغات مفتوحة — مدموج من ModerationTab القديمة — الآن صفحة مستقلة بالشكل الجديد — بيانات حية من /api/admin/moderation/reports?status=OPEN</Typography.Text>
      <Alert type="info" showIcon style={{margin:'16px 0'}} message="البلاغات الحقيقية فقط — لا عقوبة تلقائية دون مراجعة — الصفحة المتقدمة: مراقبة المحتوى (Reports)" />
      <Card style={{marginTop:16}}>
        <ModerationTab />
      </Card>
    </div>
  );
}
