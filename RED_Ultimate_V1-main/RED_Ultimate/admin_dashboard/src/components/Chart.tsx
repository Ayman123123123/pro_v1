import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsOption } from 'echarts';

/** نوع خيارات المخطط — يُصدَّر كي تُعلّق به الصفحات كائناتها فيتحقق TypeScript منها. */
export type ChartOption = EChartsOption;

/**
 * غلاف مخططات بحمولة انتقائية.
 *
 * الاستيراد الكامل `echarts-for-react` كان يسحب مكتبة ECharts بأكملها
 * (‏~1.14 ميغابايت غير مضغوطة) من أجل مخططَي خط وأعمدة فقط في صفحة الرئيسية.
 * هنا نسجّل الوحدات المستخدمة فعليًا عبر نقطة الدخول `echarts/core`،
 * فينكمش الحِزم بشكل كبير — وهو فارق حقيقي على شبكة محلية أو وصلة WireGuard بطيئة.
 *
 * عند الحاجة لنوع مخطط جديد (مثل PieChart) يُسجَّل هنا مرة واحدة فقط.
 */
echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
]);

interface ChartProps {
  option: EChartsOption;
  style?: React.CSSProperties;
  className?: string;
}

export default function Chart({ option, style, className }: ChartProps) {
  return (
    <ReactEChartsCore
      echarts={echarts}
      option={option}
      style={style}
      className={className}
      notMerge
      lazyUpdate
    />
  );
}
