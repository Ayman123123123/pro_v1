import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

/**
 * ═══ الثيم المركزي للوحة يونس السيادية (فرونت فقط) ═══
 *
 * المصدر الوحيد لثيم Ant Design: `App.tsx` يستهلكه عبر
 * `theme={buildYounesTheme(resolvedTheme)}` — لا كائنات theme مبعثرة
 * (حُذف الثيم الـinline الذهبي #B78A2E من App.tsx لصالح هذا الملف).
 *
 * اللون الواحد للعلامة: #14C79A (داكن) — وفي الفاتح درجته المتاحة
 * #007A5E من نفس التدرج الزمردي (5.8:1 على #F7F8FA)، لأن #14C79A
 * مع نص أبيض راسب (2.16:1) فلا يُستعمل كأساسي في الفاتح.
 *
 * الحياديات مطابقة لـ `src/styles.css` (`--yns-*`) ولـ `App.tsx`:
 * الخلفية #0A0F14 والسطح #141C24 والحدود #2C3A4A والتحذير #E0A83C.
 * حارس التباين `scripts/check-contrast.mjs` يقرأ `--yns-*` من styles.css
 * (لا تُخفَّض أي قيمة دون حدّها).
 */

export type YounesThemeMode = 'light' | 'dark' | 'system';
export type YounesResolvedTheme = 'light' | 'dark';

/** مفتاح التخزين المحلي — تفضيل عرض فقط، لا أسرار ولا توكنات. */
export const YNS_THEME_STORAGE_KEY = 'yns-theme';

const FONT_STACK = "'IBM Plex Sans Arabic', 'Segoe UI', Tahoma, Arial, sans-serif";

/** لوحة الداكن السيادية: زمرد #14C79A للعمل — مطابقة لـ :root في styles.css
 *  (--yns-dark/surface/border) ولكائن App.tsx الـinline سابقًا */
const darkToken: ThemeConfig['token'] = {
  colorPrimary: '#14C79A',
  colorSuccess: '#14C79A',
  colorWarning: '#E0A83C',
  colorError: '#FF5A5F',
  colorInfo: '#4FC3F7',
  colorBgBase: '#0A0F14',
  colorBgContainer: '#141C24',
  colorBgLayout: '#0A0F14',
  colorBorder: '#2C3A4A',
  colorText: '#F2F6F8',
  colorTextSecondary: '#9AAEBB',
  colorTextTertiary: '#7C90A0',
  borderRadius: 14,
  fontFamily: FONT_STACK,
};

/** لوحة الفاتح الرسمية: لؤلؤي + كحلي + ذهبي مطفي — مطابقة لـ [data-theme="light"] */
const lightToken: ThemeConfig['token'] = {
  colorPrimary: '#007A5E',
  colorSuccess: '#007A5E',
  colorWarning: '#B78A2E',
  colorError: '#D32F2F',
  colorInfo: '#1976D2',
  colorBgBase: '#F7F8FA',
  colorBgContainer: '#FFFFFF',
  colorBgLayout: '#F7F8FA',
  colorBorder: '#E6E8EB',
  colorText: '#0F1B2D',
  colorTextSecondary: '#5A6B7D',
  colorTextTertiary: '#6B7D8F',
  borderRadius: 14,
  fontFamily: FONT_STACK,
};

export function buildYounesTheme(resolved: YounesResolvedTheme): ThemeConfig {
  const dark = resolved === 'dark';
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: dark ? darkToken : lightToken,
    components: {
      Menu: {
        darkItemBg: 'transparent',
        darkSubMenuItemBg: 'transparent',
        darkItemColor: '#9AAEBB',
        darkItemHoverBg: 'rgba(20,199,154,0.10)',
        darkItemHoverColor: '#F2F6F8',
        darkItemSelectedBg:
          'linear-gradient(135deg, rgba(20,199,154,0.22) 0%, rgba(77,159,232,0.12) 100%)' as unknown as string,
        darkItemSelectedColor: '#3DE8BC',
        itemBorderRadius: 10,
        itemMarginInline: 10,
      },
      Layout: {
        siderBg: '#0A1014',
        headerBg: dark ? 'rgba(8, 21, 37, 0.85)' : 'rgba(255,255,255,0.85)',
        headerHeight: 64,
      },
      Card: {
        borderRadiusLG: 20,
      },
    },
  };
}
