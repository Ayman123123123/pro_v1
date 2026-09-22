import React, { useState, useEffect, useRef } from 'react';
import { Button, Input, Modal, Space, Typography, message } from 'antd';
import { PhoneOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

/**
 * طالب PSTN عبر الخادم — العقد المتحقق حيًا (red-backend المبني مسبقًا):
 *   POST /api/pstn/calls {number}            → {callId, status, slot, ...}
 *   POST /api/pstn/calls/{callId}/hangup {}  → {}
 * مسارا /api/admin/dinstar/calls* غير موجودين في نسخة الخادم (404 مقنّع 401).
 * الخادم يختار الشريحة/المنفذ تلقائيًا (حقل slot في الرد) — لا اختيار يدوي.
 */
export default function WebRtcDialer({ open, onClose }: { open: boolean, onClose: () => void }) {
  const [number, setNumber] = useState('');
  const [slot, setSlot] = useState<number | null>(null);
  const [callState, setCallState] = useState<string>('idle');
  const [callId, setCallId] = useState<string | null>(null);
  
  const connectAndCall = async () => {
    if (!number) return message.error('يرجى إدخال الرقم');
    setCallState('calling');
    try {
      const res = await apiFetch('/api/pstn/calls', {
        method: 'POST',
        body: JSON.stringify({ number })
      });
      if (!res.ok) throw new Error('فشل بدء المكالمة');
      const data = await res.json();
      if (!data?.callId) throw new Error('لم يعُد الخادم بمعرّف للمكالمة');
      setCallId(data.callId);
      if (typeof data?.slot === 'number') setSlot(data.slot);
      
      message.success('تم قبول طلب الاتصال. انتظر رنين الطرف الآخر عبر مسار WebRTC المعتمد.');
    } catch (e: any) {
      message.error(e.message);
      setCallState('idle');
    }
  };

  const hangup = async () => {
    if (!callId) return setCallState('idle');
    try {
      const res = await apiFetch(`/api/pstn/calls/${encodeURIComponent(callId)}/hangup`, {
        method: 'POST',
        body: JSON.stringify({}),
      });
      if (!res.ok) throw new Error('تعذّر إنهاء المكالمة من الخادم');
      message.success('تم إنهاء المكالمة وتحرير المنفذ.');
      setCallId(null);
      setSlot(null);
      setCallState('idle');
    } catch (error: any) {
      message.error(error?.message || 'تعذّر إنهاء المكالمة');
    }
  };

  return (
    <Modal title="📞 اتصال مباشر من المتصفح" open={open} onCancel={onClose} footer={null}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text type="secondary">يمكنك إجراء مكالمة حقيقية باستخدام الميكروفون وسماعة المتصفح عبر WebRTC (يتطلب إعداد Asterisk WSS).</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>الخادم يختار الشريحة/المنفذ تلقائيًا{slot !== null ? ` — المنفذ الحالي: SIM ${slot + 1}` : ''}.</Typography.Text>
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
          <Button danger size="large" block onClick={() => void hangup()}>جاري الاتصال... (إنهاء)</Button>
        )}
      </Space>
    </Modal>
  );
}
