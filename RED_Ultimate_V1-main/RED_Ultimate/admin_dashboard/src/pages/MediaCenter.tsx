import React from 'react';
import { Card, Typography, Alert } from 'antd';
import { VideoCameraOutlined } from '@ant-design/icons';
import MediaTab from './tabs/MediaTab';

// 🔴 مدموج من MediaTab القديمة — صفحة مستقلة بالشكل الجديد — بيانات حقيقية
export default function MediaCenter() {
  return (
    <div>
      <Typography.Title level={2} style={{color:'#00E6A0', margin:0}}><VideoCameraOutlined /> مركز الوسائط — موحد</Typography.Title>
      <Typography.Text type="secondary">mediasoup SFU + WebRTC — مدموج من MediaTab القديمة — الآن صفحة مستقلة بالشكل الجديد — بيانات حية</Typography.Text>
      <Alert type="info" showIcon style={{margin:'16px 0'}} message="الفيديو يعمل عبر WebRTC/SFU — مسار DINSTAR للصوت فقط — البيانات من /api/master/v1/media/active-calls" />
      <Card style={{marginTop:16}}>
        <MediaTab />
      </Card>
    </div>
  );
}
