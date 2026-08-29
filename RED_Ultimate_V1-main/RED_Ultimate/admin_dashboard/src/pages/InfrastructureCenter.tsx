import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { CloudServerOutlined } from '@ant-design/icons';
import InfrastructureTab from './tabs/InfrastructureTab';

// 🔴 مدموج من InfrastructureTab القديمة — صفحة مستقلة بالشكل الجديد — بيانات حقيقية
export default function InfrastructureCenter() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#D4B16A', margin:0}}><CloudServerOutlined /> البنية التحتية — موحدة</Typography.Title>
      <Typography.Text type="secondary">Local-first + Health — مدموج من InfrastructureTab القديمة — الآن صفحة مستقلة بالشكل الجديد — بيانات حية من /health</Typography.Text>
      <Alert type="warning" showIcon style={{margin:'16px 0'}} message="النمط المحلي بدون دومين أثناء التطوير — WireGuard ثم TLS عند الإطلاق" />
      <Card style={{marginTop:16}}>
        <InfrastructureTab />
      </Card>
    </div>
  );
}
