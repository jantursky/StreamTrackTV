'use client'

import { useState } from 'react'
import { nextTheme, type Theme } from '@/lib/theme'

interface ThemeToggleProps {
  initial: Theme
  /** Accessible name for each state, e.g. "Color theme: dark". */
  labels: Record<Theme, string>
}

const ICONS: Record<Theme, string> = {
  system: 'M4 5h16v11H4zM9 20h6M12 16v4',
  light:
    'M12 4v2M12 18v2M4 12h2M18 12h2M6.3 6.3l1.4 1.4M16.3 16.3l1.4 1.4M6.3 17.7l1.4-1.4M16.3 7.7l1.4-1.4M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8z',
  dark: 'M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z',
}

/** Cycles system, light and dark. The choice lives in a cookie so the server renders the right theme. */
export function ThemeToggle({ initial, labels }: ThemeToggleProps) {
  const [theme, setTheme] = useState<Theme>(initial)

  function cycle() {
    const next = nextTheme(theme)
    const root = document.documentElement
    if (next === 'system') {
      delete root.dataset.theme
      document.cookie = 'theme=; path=/; max-age=0; samesite=lax'
    } else {
      root.dataset.theme = next
      document.cookie = `theme=${next}; path=/; max-age=31536000; samesite=lax`
    }
    setTheme(next)
  }

  return (
    <button
      type="button"
      onClick={cycle}
      aria-label={labels[theme]}
      title={labels[theme]}
      data-theme-state={theme}
      className="inline-flex size-9 items-center justify-center rounded-pill border border-line text-ink hover:border-line-strong"
    >
      <svg
        viewBox="0 0 24 24"
        className="size-4"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.75}
        aria-hidden="true"
      >
        <path d={ICONS[theme]} strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
  )
}
