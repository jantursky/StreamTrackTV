import { describe, expect, it, vi } from 'vitest'
import {
  categoryForm,
  deleteAccountForm,
  deviceForm,
  formFields,
  goalForm,
  settingsForm,
  shareForm,
} from '../src/lib/account/forms'
import { durationTicks, heatLevel, linearTicks, niceStep } from '../src/components/charts/scale'
import { buildDigest, digestWeeks, escapeHtml } from '../src/lib/digest/digest'
import { sendMail } from '../src/lib/digest/mail'
import { appDirectory } from '../src/lib/data/catalog'
import { cronSecret, isDemoOnly, mailConfig, siteUrl, supabaseConfig, supabaseSecretKey } from '../src/lib/env'
import { apiError, describeIssues, readJsonBody } from '../src/lib/http/api'
import { clientAddress } from '../src/lib/http/route'
import {
  formatDay,
  formatDuration,
  formatDurationShort,
  formatHour,
  formatMonth,
  formatNumber,
  formatPercent,
  formatRelative,
  formatSpanLabel,
} from '../src/lib/i18n/format'
import { createTranslator } from '../src/lib/i18n/translate'
import { fetchAllRows, parseRow, parseRows, unwrap } from '../src/lib/supabase/query'
import { nextTheme, parseTheme } from '../src/lib/theme'
import { withParams } from '../src/lib/url'
import { z } from 'zod'

const t = createTranslator()
const MIN = 60_000

describe('i18n', () => {
  it('fills placeholders and keeps unknown ones', () => {
    expect(t('kpi.vsPrevious', { change: '+5%' })).toBe('+5% vs previous period')
    expect(t('overview.headline', { period: 'today' })).toBe('The TVs were on for {highlight} today.')
    expect(createTranslator('xx')('nav.overview')).toBe('Overview')
  })

  it('throws on a key that is not a string', () => {
    expect(() => t('nav' as never)).toThrow(/Missing message/)
  })

  it('formats durations, numbers, percents and dates', () => {
    expect(formatDuration(0, t)).toBe('0 min')
    expect(formatDuration(45 * MIN, t)).toBe('45 min')
    expect(formatDuration(120 * MIN, t)).toBe('2 h')
    expect(formatDuration(200 * MIN, t)).toBe('3 h 20 min')
    expect(formatDurationShort(30 * MIN, t)).toBe('30 m')
    expect(formatDurationShort(90 * MIN, t)).toBe('1.5 h')
    expect(formatDurationShort(12.4 * 60 * MIN, t)).toBe('12 h')
    expect(formatNumber(5117, 'en')).toBe('5,117')
    expect(formatPercent(0.14, 'en', true)).toBe('+14%')
    expect(formatPercent(0.5, 'en')).toBe('50%')
    expect(formatDay('2026-11-08', 'en')).toBe('Nov 8')
    expect(formatDay('2026-11-08', 'en', 'weekday')).toBe('Sun')
    expect(formatDay('2026-11-08', 'en', 'long')).toBe('Sunday, November 8, 2026')
    expect(formatMonth('2026-11', 'en')).toBe('November 2026')
    expect(formatSpanLabel({ from: '2026-11-02', to: '2026-11-08' }, 'en')).toBe('Nov 2 – Nov 8')
    expect(formatSpanLabel({ from: '2026-11-08', to: '2026-11-08' }, 'en')).toBe('Sunday, November 8, 2026')
    expect(formatHour(7)).toBe('07:00')
  })

  it('formats relative times with the largest fitting unit', () => {
    const now = new Date('2026-11-08T20:00:00Z')
    expect(formatRelative('2026-11-08T17:00:00Z', now, 'en')).toBe('3 hours ago')
    expect(formatRelative('2026-11-07T20:00:00Z', now, 'en')).toBe('yesterday')
    expect(formatRelative('2026-11-08T19:58:00Z', now, 'en')).toBe('2 minutes ago')
    expect(formatRelative('2026-11-08T20:00:10Z', now, 'en')).toBe('this minute')
  })
})

describe('chart scales', () => {
  it('picks round steps', () => {
    expect(niceStep(0)).toBe(1)
    expect(niceStep(3)).toBe(5)
    expect(niceStep(0.13)).toBe(0.2)
    expect(niceStep(700)).toBe(1000)
    expect(linearTicks(1000)).toEqual([0, 500, 1000])
    expect(linearTicks(1)).toEqual([0, 0.5, 1])
    const fractional = linearTicks(0.5)
    expect(fractional).toHaveLength(4)
    expect(fractional.at(-1)).toBeCloseTo(0.6)
  })

  it('lands duration ticks on hours or minutes', () => {
    expect(durationTicks(13.6 * 60 * MIN)).toEqual([0, 5, 10, 15].map((h) => h * 60 * MIN))
    expect(durationTicks(50 * MIN)).toEqual([0, 20, 40, 60].map((m) => m * MIN))
  })

  it('maps values onto five heat levels', () => {
    expect(heatLevel(0, 10)).toBe(0)
    expect(heatLevel(1, 0)).toBe(0)
    expect(heatLevel(0.1, 10)).toBe(1)
    expect(heatLevel(10, 10)).toBe(5)
    expect(heatLevel(20, 10)).toBe(5)
  })
})

describe('forms', () => {
  const form = (fields: Record<string, string>) => {
    const data = new FormData()
    for (const [k, v] of Object.entries(fields)) data.set(k, v)
    data.set('file', new Blob(['x']))
    return formFields(data)
  }
  const uuid = '6f1c1b4e-3c51-4a52-9a4b-1d1f7d8f0b11'

  it('reads only string fields', () => {
    expect(form({ a: '1' })).toEqual({ a: '1' })
  })

  it('validates devices and categories', () => {
    expect(deviceForm.parse({ id: uuid, name: ' Den ', memberId: '' })).toEqual({
      id: uuid,
      name: 'Den',
      memberId: null,
    })
    expect(deviceForm.safeParse({ id: 'x', name: 'Den', memberId: '' }).success).toBe(false)
    expect(categoryForm.safeParse({ packageName: 'a.b', category: 'nope' }).success).toBe(false)
  })

  it('splits the goal target and converts hours to milliseconds', () => {
    expect(goalForm.parse({ target: `device:${uuid}`, metric: 'screen_on', period: 'day', limit: '1.5' })).toEqual({
      scope: 'device',
      target: uuid,
      metric: 'screen_on',
      period: 'day',
      limit: 90 * MIN,
    })
    expect(goalForm.parse({ target: 'category:streaming', metric: 'app_time', period: 'week', limit: '3' }).limit).toBe(
      180 * MIN,
    )
    expect(goalForm.parse({ target: `member:${uuid}`, metric: 'keypresses', period: 'day', limit: '99.6' }).limit).toBe(
      100,
    )
    expect(goalForm.safeParse({ target: 'app:a.b', metric: 'keypresses', period: 'day', limit: '1' }).success).toBe(
      false,
    )
    expect(goalForm.safeParse({ target: 'planet:x', metric: 'app_time', period: 'day', limit: '1' }).success).toBe(
      false,
    )
    expect(goalForm.safeParse({ target: 'app:a.b', metric: 'app_time', period: 'day', limit: '-1' }).success).toBe(
      false,
    )
  })

  it('validates settings, share links and account deletion', () => {
    expect(
      settingsForm.parse({ timezone: 'Europe/Bratislava', locale: 'en', retentionDays: '90', digestEnabled: 'on' }),
    ).toEqual({
      timezone: 'Europe/Bratislava',
      locale: 'en',
      retentionDays: 90,
      digestEnabled: true,
    })
    expect(settingsForm.parse({ timezone: 'UTC', locale: 'en', retentionDays: '90' }).digestEnabled).toBe(false)
    expect(settingsForm.safeParse({ timezone: 'Nowhere/City', locale: 'en', retentionDays: '90' }).success).toBe(false)
    expect(settingsForm.safeParse({ timezone: 'UTC', locale: 'de', retentionDays: '90' }).success).toBe(false)
    expect(shareForm.parse({ deviceId: '', days: '7' })).toEqual({ deviceId: null, days: 7 })
    expect(shareForm.safeParse({ deviceId: '', days: '9' }).success).toBe(false)
    expect(deleteAccountForm.safeParse({ confirm: 'delete' }).success).toBe(false)
  })
})

describe('digest', () => {
  const resolve = appDirectory([{ packageName: 'evil.app', label: '<script>x</script>', category: 'other' }])
  const current = {
    usage: [{ deviceId: 'a', day: '2026-11-03', hour: 20, packageName: 'evil.app', foregroundMs: 30 * MIN }],
    keys: [],
    screen: [{ deviceId: 'a', day: '2026-11-03', hour: 20, screenOnMs: 60 * MIN }],
  }
  const previous = { ...current, usage: [], screen: [{ ...current.screen[0]!, screenOnMs: 30 * MIN }] }

  it('covers the last full week and the one before', () => {
    expect(digestWeeks('2026-11-08')).toEqual({
      current: { from: '2026-10-26', to: '2026-11-01' },
      previous: { from: '2026-10-19', to: '2026-10-25' },
    })
    expect(digestWeeks('2026-11-09').current).toEqual({ from: '2026-11-02', to: '2026-11-08' })
  })

  it('builds text and escaped HTML', () => {
    const digest = buildDigest({ from: '2026-11-02', to: '2026-11-08' }, current, previous, resolve, t, 'en')
    expect(digest.subject).toBe('Your TV week: 1 h of screen time')
    expect(digest.text).toContain('Change vs the week before: +100%')
    expect(digest.text).toContain('- <script>x</script>: 30 min')
    expect(digest.html).toContain('&lt;script&gt;x&lt;/script&gt;')
    expect(digest.html).not.toContain('<script>')
    const quiet = buildDigest(
      { from: '2026-11-02', to: '2026-11-08' },
      current,
      { usage: [], keys: [], screen: [] },
      resolve,
      t,
      'en',
    )
    expect(quiet.text).not.toContain('Change')
    const empty = buildDigest(
      { from: '2026-11-02', to: '2026-11-08' },
      { usage: [], keys: [], screen: [] },
      current,
      resolve,
      t,
      'en',
    )
    expect(empty.text).not.toContain('Top apps')
    expect(escapeHtml(`"'&`)).toBe('&quot;&#39;&amp;')
  })

  it('posts to the mail provider and fails on errors', async () => {
    const fetchImpl = vi.fn(() => Promise.resolve(new Response('{}', { status: 200 })))
    const digest = { subject: 's', text: 't', html: 'h' }
    await sendMail({ apiKey: 'key', from: 'A <a@x>' }, 'me@x', digest, fetchImpl)
    expect(fetchImpl).toHaveBeenCalledWith('https://api.resend.com/emails', expect.objectContaining({ method: 'POST' }))
    const init = (fetchImpl.mock.calls[0] as unknown as [string, RequestInit])[1]
    expect(new Headers(init.headers).get('authorization')).toBe('Bearer key')
    expect(JSON.parse(init.body as string)).toEqual({
      from: 'A <a@x>',
      to: ['me@x'],
      subject: 's',
      text: 't',
      html: 'h',
    })
    await expect(
      sendMail({ apiKey: 'k', from: 'f' }, 'x', digest, () => Promise.resolve(new Response('', { status: 422 }))),
    ).rejects.toThrow('422')
  })
})

describe('env', () => {
  const full = {
    NEXT_PUBLIC_SUPABASE_URL: 'https://p.supabase.co',
    NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY: 'pk',
    SUPABASE_SECRET_KEY: 'sk',
  }

  it('reads the integration names and the legacy fallbacks', () => {
    expect(supabaseConfig(full)).toEqual({ url: 'https://p.supabase.co', publishableKey: 'pk' })
    expect(
      supabaseConfig({ NEXT_PUBLIC_SUPABASE_URL: 'u', NEXT_PUBLIC_SUPABASE_ANON_KEY: 'anon' })?.publishableKey,
    ).toBe('anon')
    expect(supabaseConfig({ NEXT_PUBLIC_SUPABASE_URL: 'u', NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY: ' ' })).toBeNull()
    expect(supabaseSecretKey({ SUPABASE_SERVICE_ROLE_KEY: 'legacy' })).toBe('legacy')
    expect(supabaseSecretKey({})).toBeNull()
  })

  it('runs demo-only when asked or when Supabase is missing', () => {
    expect(isDemoOnly(full)).toBe(false)
    expect(isDemoOnly({ ...full, DEMO_ONLY: 'true' })).toBe(true)
    expect(isDemoOnly({ ...full, DEMO_ONLY: '0' })).toBe(false)
    expect(isDemoOnly({ ...full, SUPABASE_SECRET_KEY: '' })).toBe(true)
    expect(isDemoOnly({})).toBe(true)
  })

  it('derives the site URL', () => {
    expect(siteUrl({ NEXT_PUBLIC_SITE_URL: 'https://tv.example/' })).toBe('https://tv.example')
    expect(siteUrl({ VERCEL_PROJECT_PRODUCTION_URL: 'tv.vercel.app' })).toBe('https://tv.vercel.app')
    expect(siteUrl({})).toBe('http://localhost:3000')
  })

  it('reads cron and mail settings', () => {
    expect(cronSecret({ CRON_SECRET: 'c' })).toBe('c')
    expect(cronSecret({})).toBeNull()
    expect(mailConfig({})).toBeNull()
    expect(mailConfig({ RESEND_API_KEY: 'r' })?.from).toContain('resend.dev')
    expect(mailConfig({ RESEND_API_KEY: 'r', DIGEST_FROM: 'Me <me@x>' })).toEqual({ apiKey: 'r', from: 'Me <me@x>' })
  })
})

describe('http helpers', () => {
  it('reads JSON bodies with a byte limit', async () => {
    const post = (body: string, headers: Record<string, string> = {}) =>
      new Request('https://x', { method: 'POST', body, headers })
    expect(await readJsonBody(post('{"a":1}'), 100)).toEqual({ ok: true, value: { a: 1 } })
    const declared = await readJsonBody(post('{}', { 'content-length': '1000' }), 100)
    expect(declared.ok || declared.response.status).toBe(413)
    const empty = await readJsonBody(new Request('https://x', { method: 'POST' }), 100)
    expect(empty.ok || empty.response.status).toBe(400)
  })

  it('describes the first validation issue', () => {
    const result = z.object({ a: z.array(z.number()) }).safeParse({ a: ['x'] })
    expect(result.success || describeIssues(result.error)).toMatch(/^a\.0: /)
    expect(describeIssues(new z.ZodError([]))).toBe('Invalid payload')
    const root = z.number().safeParse('x')
    expect(root.success || describeIssues(root.error)).toBe('Invalid input: expected number, received string')
  })

  it('answers errors with the right status', async () => {
    const response = apiError('not_found', 'gone')
    expect(response.status).toBe(404)
    expect(await response.json()).toEqual({ error: 'not_found', message: 'gone' })
  })

  it('keys rate limits by the first forwarded address', () => {
    const at = (headers: Record<string, string>) => clientAddress(new Request('https://x', { headers }))
    expect(at({ 'x-forwarded-for': '203.0.113.7, 10.0.0.1' })).toBe('203.0.113.7')
    expect(at({ 'x-real-ip': '198.51.100.2' })).toBe('198.51.100.2')
    expect(at({})).toBe('unknown')
  })
})

describe('url and theme', () => {
  it('keeps shared parameters and drops the old range on a new range kind', () => {
    const current = { range: 'custom', from: '2026-11-01', to: '2026-11-05', device: 'd1', q: 'net' }
    expect(withParams('/demo/apps', current)).toBe('/demo/apps?range=custom&from=2026-11-01&to=2026-11-05&device=d1')
    expect(withParams('/demo', current, { range: 'day', date: '2026-11-05' })).toBe(
      '/demo?range=day&device=d1&date=2026-11-05',
    )
    expect(withParams('/demo', { device: ['d1', 'd2'] }, { device: null })).toBe('/demo')
    expect(withParams('/demo/calendar', { month: '2026-10', a: 'x' }, { day: '2026-10-01' }, ['month'])).toBe(
      '/demo/calendar?month=2026-10&day=2026-10-01',
    )
  })

  it('cycles themes', () => {
    expect(parseTheme('dark')).toBe('dark')
    expect(parseTheme('neon')).toBe('system')
    expect(nextTheme('system')).toBe('light')
    expect(nextTheme('light')).toBe('dark')
    expect(nextTheme('dark')).toBe('system')
  })
})

describe('supabase query helpers', () => {
  const ok = (data: unknown) => ({ data, error: null })

  it('unwraps data and throws on errors', () => {
    expect(unwrap(ok(1))).toBe(1)
    expect(() => unwrap({ data: null, error: { message: 'boom' } })).toThrow('boom')
    expect(parseRow(z.object({ a: z.number() }), ok(null))).toBeNull()
    expect(parseRows(z.number(), ok(null))).toEqual([])
    expect(() => parseRows(z.number(), ok(['x']))).toThrow()
  })

  it('pages until a short page arrives', async () => {
    const all = Array.from({ length: 2300 }, (_, i) => i)
    const pages: [number, number][] = []
    const rows = await fetchAllRows(z.number(), (from, to) => {
      pages.push([from, to])
      return Promise.resolve(ok(all.slice(from, to + 1)))
    })
    expect(rows).toEqual(all)
    expect(pages).toEqual([
      [0, 999],
      [1000, 1999],
      [2000, 2999],
    ])
  })
})
