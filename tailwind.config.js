/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        gray: {
          50: '#f9fafb',
          100: '#f3f4f6',
          200: '#e5e7eb',
          300: '#d1d5db',
          400: '#9ca3af',
          500: '#6b7280',
          600: '#4b5563',
          700: '#374151',
          800: '#1f2937',
          900: '#111827',
        },
        green: {
          400: '#4ade80',
          500: '#22c55e',
          600: '#16a34a',
        },
        emerald: {
          400: '#34d399',
          500: '#10b981',
          600: '#059669',
        },
        blue: {
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
        },
        purple: {
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
        },
        red: {
          400: '#f87171',
          500: '#ef4444',
          600: '#dc2626',
        },
        orange: {
          400: '#fb923c',
          500: '#f97316',
          600: '#ea580c',
        },
        term: {
          bg: '#0B0E14',
          panel: '#12161F',
          raised: '#171C27',
          hover: '#1B2130',
          line: '#222938',
          line2: '#2E3647',
          text: '#E4E8EF',
          muted: '#8C95A8',
          dim: '#5D667A',
          up: '#3FA586',
          'up-soft': '#173229',
          down: '#C8464F',
          'down-soft': '#35191D',
          warn: '#C99A3E',
          'warn-soft': '#33280F',
          accent: '#5B8DEF',
          'accent-soft': '#16233D',
          gold: '#C6A15B',
        },
        indigo: {
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
        }
      },
      // Institutional terminal palette: matte charcoal surfaces, hairline borders, muted semantic colors
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      animation: {
        'spin': 'spin 1s linear infinite',
        'pulse': 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'shimmer': 'shimmer 1.4s ease-in-out infinite',
      },
      keyframes: {
        shimmer: {
          '0%': { backgroundPosition: '-400px 0' },
          '100%': { backgroundPosition: '400px 0' },
        },
      },
      backdropBlur: {
        xs: '2px',
      }
    },
  },
  plugins: [],
} 