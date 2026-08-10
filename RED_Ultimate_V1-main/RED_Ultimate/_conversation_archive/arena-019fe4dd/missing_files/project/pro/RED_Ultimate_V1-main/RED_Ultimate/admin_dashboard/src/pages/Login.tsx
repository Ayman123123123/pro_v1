import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { adminLogin } from '../api';

export default function Login({ onSuccess }: { onSuccess: () => void }) {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const submit = async (values: { username: string; password: string }) => {
    setLoading(true); setError('');
    try { await adminLogin(values.username, values.password); onSuccess(); }
    catch (e: any) { setError(e.message || 'تعذر تسجيل الدخول'); }
    finally { setLoading(false); }
  };
  return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#030712', direction: 'rtl' }}>
    <Card style={{ width: 390, borderColor: '#00C896', background: '#0F172A' }}>
      <Typography.Title level={2} style={{ color: '#00C896', textAlign: 'center' }}>YOUNES MASTER</Typography.Title>
      <Typography.Paragraph style={{ color: '#94A3B8', textAlign: 'center' }}>دخول المسؤول المحلي</Typography.Paragraph>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={submit}>
        <Form.Item name="username" rules={[{ required: true, message: 'أدخل اسم المستخدم' }]}><Input size="large" prefix={<UserOutlined />} placeholder="اسم المستخدم" autoComplete="username" /></Form.Item>
        <Form.Item name="password" rules={[{ required: true, message: 'أدخل كلمة المرور' }]}><Input.Password size="large" prefix={<LockOutlined />} placeholder="كلمة المرور" autoComplete="current-password" /></Form.Item>
        <Button htmlType="submit" type="primary" size="large" block loading={loading} style={{ background: '#00C896', color: '#030712' }}>دخول</Button>
      </Form>
    </Card>
  </div>;
}
