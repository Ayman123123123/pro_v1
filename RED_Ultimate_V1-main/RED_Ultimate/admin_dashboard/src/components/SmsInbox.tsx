import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Empty, Input, List, Row, Select, Space, Typography, message, Spin, Badge, Modal } from 'antd';
import { SendOutlined, ReloadOutlined, UserOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { apiFetch } from '../api';

type MessageDto = {
  id: string;
  direction: 'INBOUND' | 'OUTBOUND';
  status: string;
  text: string;
  timestamp: string;
  read: boolean;
  port?: number;
};

type ConversationDto = {
  number: string;
  lastMessage: string;
  lastTimestamp: string;
  unreadCount: number;
};

type BindingDto = {
  userId: string;
  redId: string;
  gatewayHost: string;
  portIndex: number;
  number: string | null;
};

export default function SmsInbox() {
  const [conversations, setConversations] = useState<ConversationDto[]>([]);
  const [bindings, setBindings] = useState<BindingDto[]>([]);
  const [selectedNumber, setSelectedNumber] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMsgs, setLoadingMsgs] = useState(false);
  const [replyText, setReplyText] = useState('');
  
  // selectedSender holds stringified JSON of { gatewayHost, portIndex }
  const [selectedSender, setSelectedSender] = useState<string | undefined>(undefined);
  const [sending, setSending] = useState(false);

  // New Chat Modal state
  const [isNewChatModalVisible, setIsNewChatModalVisible] = useState(false);
  const [newChatNumber, setNewChatNumber] = useState('');

  const loadConversations = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiFetch('/api/sms/conversations');
      if (!res.ok) throw new Error('Failed to load SMS');
      setConversations(await res.json());
    } catch (e: any) {
      message.error(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadBindings = useCallback(async () => {
    try {
      const res = await apiFetch('/api/admin/dinstar/bindings');
      if (res.ok) {
        setBindings(await res.json());
      }
    } catch (e: any) {
      console.warn('Failed to load bindings', e);
    }
  }, []);

  const loadMessages = useCallback(async (number: string) => {
    setLoadingMsgs(true);
    try {
      const res = await apiFetch(`/api/sms/conversation/${encodeURIComponent(number)}`);
      if (!res.ok) throw new Error('Failed to load conversation');
      setMessages(await res.json());
      
      await apiFetch('/api/sms/read', {
        method: 'POST',
        body: JSON.stringify({ number })
      });
      loadConversations();
    } catch (e: any) {
      message.error(e.message);
    } finally {
      setLoadingMsgs(false);
    }
  }, [loadConversations]);

  useEffect(() => {
    loadConversations();
    loadBindings();
  }, [loadConversations, loadBindings]);

  useEffect(() => {
    if (selectedNumber) loadMessages(selectedNumber);
  }, [selectedNumber, loadMessages]);

  const handleStartNewChat = () => {
    if (!newChatNumber.trim()) return;
    setSelectedNumber(newChatNumber.trim());
    setIsNewChatModalVisible(false);
    setNewChatNumber('');
  };

  const sendSms = async () => {
    if (!selectedNumber || !replyText.trim()) return;
    setSending(true);
    try {
      let res;
      if (selectedSender) {
        const senderInfo = JSON.parse(selectedSender);
        const body = {
          text: replyText,
          param: [{ number: selectedNumber }],
          port: [senderInfo.portIndex],
          gatewayHost: senderInfo.gatewayHost
        };
        res = await apiFetch('/api/admin/dinstar/sms/send', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        });
      } else {
        const body = {
          number: selectedNumber,
          text: replyText,
          port: null,
        };
        res = await apiFetch('/api/sms/send', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        });
      }
      
      if (!res.ok) throw new Error('Failed to send SMS');
      setReplyText('');
      loadMessages(selectedNumber);
    } catch (e: any) {
      message.error(e.message);
    } finally {
      setSending(false);
    }
  };

  const deleteMessage = async (id: string) => {
    try {
      await apiFetch(`/api/sms/${encodeURIComponent(id)}`, { method: 'DELETE' });
      if (selectedNumber) loadMessages(selectedNumber);
    } catch (e: any) {
      message.error(e.message);
    }
  };

  return (
    <Card 
      title="📩 إدارة رسائل SMS الموحدة" 
      extra={
        <Space>
          <Button icon={<PlusOutlined />} type="primary" onClick={() => setIsNewChatModalVisible(true)}>
            محادثة جديدة
          </Button>
          <Button icon={<ReloadOutlined />} onClick={loadConversations} loading={loading}>
            تحديث
          </Button>
        </Space>
      }
    >
      <Row gutter={16}>
        <Col span={8} style={{ borderRight: '1px solid #f0f0f0', maxHeight: '600px', overflowY: 'auto' }}>
          <List
            loading={loading}
            dataSource={conversations}
            locale={{ emptyText: 'لا توجد محادثات' }}
            renderItem={item => (
              <List.Item
                style={{ cursor: 'pointer', backgroundColor: selectedNumber === item.number ? '#e6f7ff' : 'transparent', padding: '12px' }}
                onClick={() => setSelectedNumber(item.number)}
              >
                <List.Item.Meta
                  avatar={<UserOutlined style={{ fontSize: 24, color: '#1890ff' }} />}
                  title={<Space>{item.number} {item.unreadCount > 0 && <Badge count={item.unreadCount} />}</Space>}
                  description={<Typography.Text ellipsis style={{ width: '100%' }}>{item.lastMessage}</Typography.Text>}
                />
              </List.Item>
            )}
          />
        </Col>
        <Col span={16}>
          {selectedNumber ? (
            <div style={{ display: 'flex', flexDirection: 'column', height: '600px' }}>
              <div style={{ padding: '0 0 16px 0', borderBottom: '1px solid #f0f0f0' }}>
                <Typography.Title level={5}>{selectedNumber}</Typography.Title>
              </div>
              <div style={{ flex: 1, overflowY: 'auto', padding: '16px 0', display: 'flex', flexDirection: 'column-reverse' }}>
                {loadingMsgs ? <Spin /> : messages.length === 0 ? <Empty /> : messages.map(msg => (
                  <div key={msg.id} style={{
                    marginBottom: 16,
                    alignSelf: msg.direction === 'OUTBOUND' ? 'flex-start' : 'flex-end',
                    maxWidth: '70%',
                  }}>
                    <div style={{
                      backgroundColor: msg.direction === 'OUTBOUND' ? '#f0f0f0' : '#1890ff',
                      color: msg.direction === 'OUTBOUND' ? '#000' : '#fff',
                      padding: '8px 12px',
                      borderRadius: '8px',
                      position: 'relative'
                    }}>
                      <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{msg.text}</div>
                      <div style={{ fontSize: '10px', marginTop: '4px', textAlign: 'right', opacity: 0.8 }}>
                        {new Date(msg.timestamp).toLocaleString('ar')}
                        {msg.port !== undefined && msg.port !== null && ` • منفذ ${msg.port + 1}`}
                        {msg.direction === 'OUTBOUND' && ` • ${msg.status}`}
                        <Button type="text" size="small" icon={<DeleteOutlined />} onClick={() => deleteMessage(msg.id)} style={{ padding: 0, marginLeft: 8, height: 'auto', color: 'inherit' }} />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: '16px' }}>
                <Space.Compact style={{ width: '100%' }}>
                  <Select
                    placeholder="رقم الإرسال..."
                    style={{ width: 150 }}
                    allowClear
                    value={selectedSender}
                    onChange={setSelectedSender}
                    options={bindings.map(b => ({
                      value: JSON.stringify({ gatewayHost: b.gatewayHost, portIndex: b.portIndex }),
                      label: b.number ? `${b.number} (${b.redId})` : `منفذ ${b.portIndex + 1} (${b.redId})`
                    }))}
                  />
                  <Input
                    placeholder="اكتب رسالة..."
                    value={replyText}
                    onChange={e => setReplyText(e.target.value)}
                    onPressEnter={sendSms}
                  />
                  <Button type="primary" icon={<SendOutlined />} onClick={sendSms} loading={sending}>إرسال</Button>
                </Space.Compact>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
              <Empty description="اختر محادثة لعرض الرسائل" />
            </div>
          )}
        </Col>
      </Row>

      <Modal
        title="محادثة جديدة"
        open={isNewChatModalVisible}
        onOk={handleStartNewChat}
        onCancel={() => setIsNewChatModalVisible(false)}
        okText="بدء"
        cancelText="إلغاء"
      >
        <Input 
          placeholder="أدخل رقم الهاتف..." 
          value={newChatNumber} 
          onChange={(e) => setNewChatNumber(e.target.value)} 
          onPressEnter={handleStartNewChat}
        />
      </Modal>
    </Card>
  );
}
