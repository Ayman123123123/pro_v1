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
  return <div className="admin-login-shell" dir="rtl">
    <Card className="admin-login-card" styles={{ body: { padding: 32, background: 'rgba(15, 23, 42, .94)', borderRadius: 25 } }}>
      <div className="admin-login-brand">
        <div className="admin-login-emblem">◆</div>
        <Typography.Title level={2} style={{ color: '#00D39A', margin: 0, letterSpacing: '.5px' }}>YOUNES MASTER</Typography.Title>
        <Typography.Paragraph style={{ color: '#A8BBC7', textAlign: 'center', margin: 0 }}>دخول المسؤول المحلي الآمن</Typography.Paragraph>
      </div>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={submit}>
        <Form.Item name="username" rules={[{ required: true, message: 'أدخل اسم المستخدم' }]}><Input size="large" prefix={<UserOutlined />} placeholder="اسم المستخدم" autoComplete="username" /></Form.Item>
        <Form.Item name="password" rules={[{ required: true, message: 'أدخل كلمة المرور' }]}><Input.Password size="large" prefix={<LockOutlined />} placeholder="كلمة المرور" autoComplete="current-password" /></Form.Item>
        <Button htmlType="submit" type="primary" size="large" block loading={loading} style={{ background: '#00C896', color: '#030712' }}>دخول</Button>
      </Form>
    </Card>
  </div>;
}
