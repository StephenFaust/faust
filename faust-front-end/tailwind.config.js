/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      padding: '2rem',
      screens: { '2xl': '1440px' }
    },
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        hairline: 'hsl(var(--hairline))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
          soft: 'hsl(var(--primary-soft))'
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))'
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))'
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))'
        },
        tertiary: {
          DEFAULT: 'hsl(var(--tertiary))',
          foreground: 'hsl(var(--tertiary-foreground))'
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))'
        },
        success: 'hsl(var(--success))',
        warning: 'hsl(var(--warning))',
        info: 'hsl(var(--info))',
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))'
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))'
        },
        sidebar: {
          DEFAULT: 'hsl(var(--sidebar))',
          foreground: 'hsl(var(--sidebar-foreground))',
          muted: 'hsl(var(--sidebar-muted))',
          accent: 'hsl(var(--sidebar-accent))',
          hover: 'hsl(var(--sidebar-hover))',
          border: 'hsl(var(--sidebar-border))'
        }
      },
      borderRadius: {
        '2xl': 'calc(var(--radius) + 6px)',
        xl: 'calc(var(--radius) + 2px)',
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 3px)',
        sm: 'calc(var(--radius) - 5px)'
      },
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          '"SF Pro Text"',
          '"PingFang SC"',
          '"Hiragino Sans GB"',
          '"Microsoft YaHei"',
          'sans-serif'
        ],
        display: [
          '"Space Grotesk"',
          '"SF Pro Display"',
          '-apple-system',
          'BlinkMacSystemFont',
          '"PingFang SC"',
          'sans-serif'
        ],
        mono: [
          '"JetBrains Mono"',
          'SFMono-Regular',
          'ui-monospace',
          'Menlo',
          'Consolas',
          'monospace'
        ]
      },
      fontSize: {
        display: ['clamp(26px, 2.6vw, 34px)', { lineHeight: '1.15', letterSpacing: '-0.032em' }],
        headline: ['21px', { lineHeight: '1.25', letterSpacing: '-0.022em' }],
        title: ['15px', { lineHeight: '1.4', letterSpacing: '-0.012em' }],
        label: ['11.5px', { lineHeight: '1.45', letterSpacing: '0.02em' }]
      },
      boxShadow: {
        /*
         * 注意：键名不能与 colors 里的 `card` / `popover` 重名。
         * 否则 Tailwind 会同时生成 `shadow-{color}` 规则并覆盖掉真实阴影，
         * 导致卡片在深色主题下完全没有投影。这里统一改用 panel / float。
         */
        panel:
          '0 1px 1.5px hsl(240 30% 12% / 0.035), 0 3px 10px -3px hsl(240 30% 12% / 0.055)',
        'panel-hover':
          '0 2px 4px hsl(240 30% 12% / 0.045), 0 14px 34px -10px hsl(240 30% 12% / 0.16)',
        float:
          '0 4px 10px -3px hsl(240 30% 12% / 0.10), 0 22px 48px -14px hsl(240 30% 12% / 0.20)',
        glow: '0 0 0 1px hsl(var(--primary) / 0.16), 0 10px 28px -10px hsl(var(--primary) / 0.5)',
        'glow-lg':
          '0 0 0 1px hsl(var(--primary) / 0.2), 0 18px 52px -14px hsl(var(--primary) / 0.55)',
        inset: 'inset 0 1px 0 0 hsl(0 0% 100% / 0.06)'
      },
      transitionTimingFunction: {
        expo: 'cubic-bezier(0.16, 1, 0.3, 1)',
        spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
        standard: 'cubic-bezier(0.2, 0, 0, 1)'
      },
      keyframes: {
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' }
        },
        rise: {
          from: { opacity: '0', transform: 'translate3d(0, 12px, 0) scale(0.985)' },
          to: { opacity: '1', transform: 'none' }
        },
        'slide-in': {
          from: { opacity: '0', transform: 'translate3d(-8px, 0, 0)' },
          to: { opacity: '1', transform: 'none' }
        },
        'aurora-drift': {
          '0%': { transform: 'translate3d(-3%, -2%, 0) scale(1)' },
          '50%': { transform: 'translate3d(3%, 2%, 0) scale(1.08)' },
          '100%': { transform: 'translate3d(-2%, 3%, 0) scale(1.02)' }
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' }
        },
        'pulse-ring': {
          '0%': { opacity: '0.55', transform: 'scale(0.9)' },
          '70%': { opacity: '0', transform: 'scale(1.9)' },
          '100%': { opacity: '0', transform: 'scale(1.9)' }
        },
        'caret-blink': {
          '0%, 70%, 100%': { opacity: '1' },
          '20%, 50%': { opacity: '0' }
        }
      },
      animation: {
        'fade-in': 'fade-in 0.24s cubic-bezier(0.16, 1, 0.3, 1)',
        rise: 'rise 0.52s cubic-bezier(0.16, 1, 0.3, 1) backwards',
        'slide-in': 'slide-in 0.32s cubic-bezier(0.16, 1, 0.3, 1)',
        aurora: 'aurora-drift 26s ease-in-out infinite alternate',
        shimmer: 'shimmer 1.8s infinite',
        'pulse-ring': 'pulse-ring 2.4s cubic-bezier(0.16, 1, 0.3, 1) infinite',
        caret: 'caret-blink 1.1s steps(1) infinite'
      }
    }
  },
  plugins: []
};
