import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        yn: {
          green: {
            DEFAULT: '#B78A2E',
            dark: '#9A7524',
            light: '#D4B16A',
          },
          gold: {
            DEFAULT: '#E0A83C',
            dark: '#C08F2E',
          },
          blue: {
            DEFAULT: '#4FC3F7',
            dark: '#3BA8D9',
          },
          navy: '#0A1014',
          dark: '#0A0F14',
          surface: '#141C24',
          'surface-light': '#1E2936',
          border: '#2C3A4A',
          text: '#F2F6F8',
          'text-secondary': '#9AAEBB',
          'text-muted': '#7C90A0',
          error: '#FF5A5F',
          warning: '#E0A83C',
          success: '#B78A2E',
          info: '#4FC3F7',
        },
      },
      fontFamily: {
        primary: ['IBM Plex Sans Arabic', 'Segoe UI', 'Tahoma', 'Arial', 'sans-serif'],
        heading: ['IBM Plex Sans Arabic', 'Segoe UI', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      fontSize: {
        'display-xl': ['2.25rem', { letterSpacing: '-0.02em', lineHeight: '1.3' }],
        'display-lg': ['1.75rem', { letterSpacing: '-0.01em', lineHeight: '1.3' }],
        'display-md': ['1.375rem', { lineHeight: '1.3' }],
        'display-sm': ['1.125rem', { lineHeight: '1.3' }],
        'base': ['1rem', { lineHeight: '1.6' }],
        'sm': ['0.875rem', { lineHeight: '1.5' }],
        'xs': ['0.75rem', { lineHeight: '1.5' }],
      },
      spacing: {
        '1': '4px',
        '2': '8px',
        '3': '12px',
        '4': '16px',
        '5': '20px',
        '6': '24px',
        '8': '32px',
        '10': '40px',
        '12': '48px',
        '16': '64px',
      },
      borderRadius: {
        sm: '8px',
        md: '14px',
        lg: '20px',
        xl: '28px',
        '2xl': '36px',
        full: '9999px',
      },
      boxShadow: {
        sm: '0 2px 8px rgba(0, 0, 0, 0.3)',
        md: '0 4px 16px rgba(0, 0, 0, 0.4)',
        lg: '0 8px 32px rgba(0, 0, 0, 0.5)',
        'glow-green': '0 0 30px rgba(183, 138, 46, 0.3)',
        'glow-gold': '0 0 30px rgba(224, 168, 60, 0.3)',
        'glow-blue': '0 0 30px rgba(79, 195, 247, 0.3)',
      },
      transitionDuration: {
        fast: '150ms',
        normal: '250ms',
        slow: '400ms',
      },
      transitionTimingFunction: {
        DEFAULT: 'ease',
      },
      zIndex: {
        base: '0',
        dropdown: '100',
        sticky: '200',
        overlay: '300',
        modal: '400',
        toast: '500',
      },
      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, var(--yns-green) 0%, var(--yns-blue) 100%)',
        'gradient-gold': 'linear-gradient(135deg, var(--yns-gold) 0%, var(--yns-green) 100%)',
        'gradient-surface': 'linear-gradient(180deg, var(--yns-surface) 0%, var(--yns-navy) 100%)',
        'gradient-header': 'linear-gradient(180deg, rgba(26, 36, 44, 0.6) 0%, transparent 100%)',
        'geometric-pattern': 'url("data:image/svg+xml,%3Csvg width=\'60\' height=\'60\' viewBox=\'0 0 60 60\' xmlns=\'http://www.w3.org/2000/svg\'%3E%3Cg fill=\'none\' fill-rule=\'evenodd\'%3E%3Cg fill=\'%2300B37E\' fill-opacity=\'0.05\'%3E%3Cpath d=\'M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z\'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")',
      },
    },
  },
  plugins: [],
};

export default config;