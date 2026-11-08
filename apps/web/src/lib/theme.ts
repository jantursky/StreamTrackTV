export const THEMES = ['system', 'light', 'dark'] as const
export type Theme = (typeof THEMES)[number]

export function parseTheme(value: string | undefined): Theme {
  return value === 'light' || value === 'dark' ? value : 'system'
}

export function nextTheme(theme: Theme): Theme {
  return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length] ?? 'system'
}
