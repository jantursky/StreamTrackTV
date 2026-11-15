import { describe, expect, it } from 'vitest'
import { DEFAULT_LOCALE, htmlLang } from '@/lib/i18n/translate'

describe('htmlLang', () => {
  it('returns a locale that has a dictionary', () => {
    expect(htmlLang('en')).toBe('en')
  })

  it('falls back to the default locale for an unknown one', () => {
    expect(htmlLang('sk')).toBe(DEFAULT_LOCALE)
    expect(htmlLang('toString')).toBe(DEFAULT_LOCALE)
  })
})
