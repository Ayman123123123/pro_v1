import React, { useCallback, useState } from 'react';
import { Alert, Card, Row, Col, Statistic, Tag, Descriptions } from 'antd';
import { MessageOutlined, SendOutlined, ClockCircleOutlined } from '@ant-design/icons';

import { apiFetch } from '../../api';
import { usePolling } from '../../hooks/usePolling';
const MessagingTab: React.FC = () => {
    const [messageStats, setMessageStats] = useState<any>(null);
    const [error, setError] = useState('');

    const load = useCallback(async () => {
        try {
            const response = await apiFetch('/api/master/v1/stats/realtime');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            setMessageStats(await response.json());
            setError('');
        } catch (cause: any) { setError(`تعذر قراءة مقاييس الرسائل: ${cause?.message || 'خطأ غير معروف'}`); }
    }, []);

    // استطلاع كل 5 ثوانٍ يتوقف عند إخفاء التبويب
    usePolling(load, 5000);

    return (
        <div>
            <Row gutter={[16, 16]}>
                <Col span={6}>
                    <Card>
                        <Statistic title="رسائل اليوم" value={messageStats?.messages_24h ?? '—'}
                            prefix={<MessageOutlined />} valueStyle={{ color: '#1890ff' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="معدل التوصيل" value={messageStats?.delivery_rate_percent ?? '—'} suffix="%"
                            prefix={<SendOutlined />} valueStyle={{ color: '#52c41a' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="بانتظار التوصيل" value={messageStats?.pending_messages_24h ?? '—'}
                            prefix={<ClockCircleOutlined />} valueStyle={{ color: '#722ed1' }} />
                    </Card>
                </Col>
                <Col span={6}>
                    <Card>
                        <Statistic title="Active Conversations" value={messageStats?.active_conversations ?? '—'}
                            prefix={<MessageOutlined />} valueStyle={{ color: '#fa8c16' }} />
                    </Card>
                </Col>
            </Row>

            {error && <Alert type="warning" showIcon message={error} style={{ marginTop: 16 }} />}
            <Card title="تدفق الرسائل المشفر — آخر 24 ساعة" style={{ marginTop: 16 }}>
                <Descriptions column={{ xs: 1, sm: 2, lg: 4 }} bordered size="small">
                    <Descriptions.Item label="مرسلة"><Tag color="blue">{messageStats?.messages_24h ?? '—'}</Tag></Descriptions.Item>
                    <Descriptions.Item label="تم توصيلها"><Tag color="cyan">{messageStats?.delivered_messages_24h ?? '—'}</Tag></Descriptions.Item>
                    <Descriptions.Item label="تمت قراءتها"><Tag color="green">{messageStats?.read_messages_24h ?? '—'}</Tag></Descriptions.Item>
                    <Descriptions.Item label="معلّقة"><Tag color="orange">{messageStats?.pending_messages_24h ?? '—'}</Tag></Descriptions.Item>
                </Descriptions>
                <Alert style={{ marginTop: 16 }} type="info" showIcon message="تُعرض بيانات تشغيلية مجمّعة فقط؛ لا تعرض اللوحة محتوى الرسائل أو هوية المرسل أو معرّف المحادثة." />
            </Card>
        </div>
    );
};

export default MessagingTab;
