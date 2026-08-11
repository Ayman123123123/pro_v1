import React from 'react';
import { Button, Result, Typography } from 'antd';

interface ErrorBoundaryProps {
  /** المفتاح الحالي للصفحة — أي تغيّر فيه يعيد ضبط الحد ويسمح بمحاولة جديدة. */
  resetKey?: string;
  children: React.ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * حدّ أخطاء لكل صفحة إدارية.
 *
 * قبل هذا المكوّن كان أي استثناء أثناء التصيير (مثل حقل ناقص في رد الخادم)
 * يُفرغ شجرة React بالكامل ويترك المسؤول أمام شاشة سوداء بلا أي مسار للتعافي،
 * وهو سلوك خطِر في لوحة تشغيل سيادية. الآن يبقى الإطار والقائمة الجانبية
 * صالحين، ويُعرض خطأ محصور داخل منطقة المحتوى فقط.
 *
 * ملاحظة أمنية: لا نرسل تقارير الأعطال إلى أي خدمة خارجية — يبقى كل شيء محليًا،
 * والتفاصيل التقنية تُطبع في console للمشغّل فقط.
 */
export default class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidUpdate(previous: ErrorBoundaryProps) {
    // الانتقال إلى صفحة أخرى يمسح الخطأ السابق تلقائيًا.
    if (previous.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[younes-admin] فشل تصيير الصفحة:', error, info.componentStack);
  }

  private retry = () => this.setState({ error: null });

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <Result
        status="error"
        title="تعذّر عرض هذه الصفحة"
        subTitle="حدث خطأ غير متوقع أثناء التصيير. باقي اللوحة ما زال يعمل، ويمكنك إعادة المحاولة أو الانتقال إلى قسم آخر."
        extra={[
          <Button type="primary" key="retry" onClick={this.retry}>
            إعادة المحاولة
          </Button>,
          <Button key="reload" onClick={() => window.location.reload()}>
            إعادة تحميل اللوحة
          </Button>,
        ]}
      >
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            التفاصيل التقنية: {error.message}
          </Typography.Text>
        </Typography.Paragraph>
      </Result>
    );
  }
}
