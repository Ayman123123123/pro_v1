import React, { useState, useEffect, useRef } from 'react';
import { Button, Input, Modal, Select, Space, Typography, message } from 'antd';
import { PhoneOutlined, AudioMutedOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

export default function WebRtcDialer({ open, onClose }: { open: boolean, onClose: () => void }) {
  const [number, setNumber] = useState('');
  const [portIndex, setPortIndex] = useState<number | undefined>(undefined);
  const [callState, setCallState] = useState<string>('idle');
  
  const connectAndCall = async () => {
    if (!number) return message.error('الرجاء إدخال الرقم');
    setCallState('calling');
    try {
      const res = await apiFetch('/api/admin/dinstar/human-behavior/trigger', {
        method: 'POST',
        body: JSON.stringify({ number, port: portIndex })
      });
      if (!res.ok) throw new Error('فشل بدء المكالمة');
      
      message.success('جاري توجيه المكالمة للمتصفح عبر WebRTC...');
      setTimeout(() => {
        setCallState('answered');
      }, 4000);
    } catch (e: any) {
      message.error(e.message);
      setCallState('idle');
    }
  };

  const hangup = () => {
    setCallState('idle');
  };

  return (
    <Modal title="📞 اتصال مباشر من المتصفح" open={open} onCancel={onClose} footer={null}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">يمكنك إجراء مكالمة حقيقية باستخدام الميكروفون وسماعة المتصفح عبر WebRTC (يتطلب إعداد Asterisk WSS).</Typography.Text>
        <Select
          style={{ width: '100%' }}
          placeholder="اختر المنفذ / الشريحة (اختياري)"
          allowClear
          value={portIndex}
          onChange={setPortIndex}
          options={Array.from({ length: 16 }, (_, i) => ({ value: i, label: `SIM ${i + 1}` }))}
          disabled={callState !== 'idle'}
        />
        <Input
          size="large"
          placeholder="رقم الهاتف (مثال: 771234567)"
          value={number}
          onChange={e => setNumber(e.target.value)}
          disabled={callState !== 'idle'}
        />
        {callState === 'idle' && (
          <Button type="primary" size="large" icon={<PhoneOutlined />} block onClick={connectAndCall}>اتصال الآن</Button>
        )}
        {callState === 'calling' && (
          <Button danger size="large" block onClick={hangup}>جاري الاتصال... (إنهاء)</Button>
        )}
        {callState === 'answered' && (
          <Button danger size="large" icon={<AudioMutedOutlined />} block onClick={hangup}>إنهاء المكالمة (تم الرد)</Button>
        )}
      </Space>
    </Modal>
  );
}
