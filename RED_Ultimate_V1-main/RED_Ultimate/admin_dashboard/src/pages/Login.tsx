import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography, Space, Tag } from 'antd';
import { LockOutlined, UserOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { adminLogin } from '../api';

interface LoginProps {
  onLogin?: (username: string, password: string) => Promise<void>;
  onSuccess?: () => void;
  isLoading?: boolean;
}
export default function Login({ onLogin, onSuccess, isLoading }: LoginProps) {
  const [error, setError] = useState('');
  const [internalLoading, setInternalLoading] = useState(false);
  const loading = isLoading ?? internalLoading;
  const submit = async (values: { username: string; password: string }) => {
    if (onLogin) {
      try { setError(''); await onLogin(values.username, values.password); }
      catch (e: any) { setError(e.message || 'تعذر تسجيل الدخول'); }
      return;
    }
    // Fallback legacy — يدعم onSuccess القديم
    setInternalLoading(true); setError('');
    try { await adminLogin(values.username, values.password); onSuccess?.(); }
    catch (e: any) { setError(e.message || 'تعذر تسجيل الدخول'); }
    finally { setInternalLoading(false); }
  };
  return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#030712', direction: 'rtl' }}>
    <Space direction="vertical" align="center" size="large">
      <div style={{ textAlign: 'center' }}>
        <Typography.Title level={1} style={{ color: '#00C896', margin: 0 }}>◆ YOUNES</Typography.Title>
        <Typography.Text style={{ color: '#64748B', fontSize: 14 }}>Sovereign Master Control</Typography.Text>
      </div>
      <Card style={{ width: 400, borderColor: '#1E293B', background: '#0F172A', boxShadow: '0 0 40px rgba(0,200,150,0.08)' }}>
        <Typography.Title level={4} style={{ color: '#94A3B8', textAlign: 'center', marginBottom: 24 }}>دخول المسؤول السيادي</Typography.Title>
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={submit}>
          <Form.Item name="username" rules={[{ required: true, message: 'أدخل اسم المستخدم' }]}>
            <Input size="large" prefix={<UserOutlined style={{color:'#64748B'}} />} placeholder="اسم المستخدم" autoComplete="username"
              style={{ background: '#1E293B', borderColor: '#334155', color: '#fff' }} />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: 'أدخل كلمة المرور' }]}>
            <Input.Password size="large" prefix={<LockOutlined style={{color:'#64748B'}} />} placeholder="كلمة المرور" autoComplete="current-password"
              style={{ background: '#1E293B', borderColor: '#334155', color: '#fff' }} />
          </Form.Item>
          <Button htmlType="submit" type="primary" size="large" block loading={loading}
            style={{ background: '#00C896', color: '#030712', fontWeight: 'bold', height: 48, borderRadius: 8, border: 'none' }}>
            دخول
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center' }}>
          <Space>
            <SafetyCertificateOutlined style={{ color: '#00C896' }} />
            <Typography.Text style={{ color: '#475569', fontSize: 12 }}>اتصال مشفّر · سلطة يونس المحلية</Typography.Text>
          </Space>
        </div>
      </Card>
      <Space>
        <Tag color="blue">LOCAL MODE</Tag>
        <Tag color="default">v1.0.0</Tag>
      </Space>
    </Space>
  </div>;
}
