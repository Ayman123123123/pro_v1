import type { ThemeConfig } from 'antd';
import { theme as antdTheme } from 'antd';

/**
 * ═══ الثيم المركزي للوحة يونس السيادية (فرونت فقط) ═══
 *
 * كل ألوان Ant Design تُعرَّف هنا مرة واحدة، و`App.tsx` يستهلكها عبر
 * `buildYounesTheme(resolved)` — لا كائنات theme مبعثرة في الملفات.
 * القيم مطابقة لمتغيرات `src/styles.css` (`--yns-*`) حتى لا يخرج antd
 * عن الهوية الداكنة-الذهبية، ومطابقة لحارس التباين WCAG في
 * `scripts/check-contrast.mjs` (لا تُخفَّض أي قيمة دون حدّها).
 *
 * ملاحظة مالك (مقصودة لا سهوًا): في الوضع الداكن `colorSuccess`
 * ذهبي (#B78A2E) مطابق لـ `--yns-success` — لأن الأخضر الحقيقي على
 * الخلفية الداكنة كان راسبًا (2.16:1) فاعتُمد الذهبي عمدًا. إن أراد
 * المالك أخضر نجاح حقيقيًا في الداكن، القرار له والقيمة المقترحة
 * `#3DD68C` مع تحديث `--yns-success` والحارس معًا.
 */

export type YounesThemeMode = 'light' | 'dark' | 'system';
export type YounesResolvedTheme = 'light' | 'dark';

/** مفتاح التخزين المحلي — تفضيل عرض فقط، لا أسرار ولا توكنات. */
export const YNS_THEME_STORAGE_KEY = 'yns-theme';

const FONT_STACK = "'IBM Plex Sans Arabic', 'Segoe UI', Tahoma, Arial, sans-serif";

/** لوحة الداكن السيادية: زمرد للعمل وذهب للتمييز — مطابقة لـ :root في styles.css
 *  متوازنة 2026: أسطح مرفوعة قليلاً عن السواد التام لتمييز البطاقات دون إزعاج */
const darkToken: ThemeConfig['token'] = {
  colorPrimary: '#14C79A',
  colorSuccess: '#14C79A',
  colorWarning: '#E0B551',
  colorError: '#FF5A5F',
  colorInfo: '#4FC3F7',
  colorBgBase: '#0E1621',
  colorBgContainer: '#182635',
  colorBgLayout: '#0E1621',
  colorBorder: '#33465C',
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
        siderBg: '#0E1621',
        headerBg: dark ? 'rgba(8, 21, 37, 0.85)' : 'rgba(255,255,255,0.85)',
        headerHeight: 64,
      },
      Card: {
        borderRadiusLG: 20,
      },
    },
  };
}
