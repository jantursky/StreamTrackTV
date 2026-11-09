import { expect, test, type Page } from '@playwright/test'

/** Fails the test on console errors, which includes Content Security Policy violations. */
function watchConsole(page: Page): string[] {
  const errors: string[] = []
  // WebKit reports router prefetches cut off by a navigation as access-control errors.
  const record = (text: string) => {
    if (!text.includes('_rsc=')) errors.push(text)
  }
  page.on('console', (message) => {
    if (message.type() === 'error') record(message.text())
  })
  page.on('pageerror', (error) => {
    record(error.message)
  })
  return errors
}

const kpi = (page: Page, label: string) =>
  page.getByTestId('kpis').locator('div.card', { hasText: label }).locator('p').nth(1)

test.describe('demo dashboard', () => {
  test('landing page leads to the live demo', async ({ page }) => {
    const errors = watchConsole(page)
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Know how your TV is really used.')
    await page.getByRole('link', { name: 'Open the live demo' }).click()
    await expect(page).toHaveURL(/\/demo$/)
    expect(errors).toEqual([])
  })

  test('overview renders the seeded household', async ({ page }) => {
    const errors = watchConsole(page)
    await page.goto('/demo')
    await expect(page.getByTestId('demo-badge')).toHaveText('Demo data')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('The TVs were on for')
    await expect(page.getByTestId('range-label')).toHaveText('Nov 2 – Nov 8')
    await expect(kpi(page, 'Screen on')).toHaveText(/\d+ h/)
    await expect(page.getByTestId('top-apps').getByRole('listitem')).toHaveCount(6)
    await expect(page.getByTestId('alerts').getByRole('listitem').first()).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Dashboard sections' }).getByRole('link')).toHaveCount(10)
    expect(errors).toEqual([])
  })

  test('range filter changes the URL and the numbers', async ({ page }) => {
    await page.goto('/demo')
    const week = await kpi(page, 'Screen on').textContent()
    await page.getByRole('link', { name: 'Day', exact: true }).click()
    await expect(page).toHaveURL(/range=day/)
    await expect(page.getByTestId('range-label')).toHaveText('Sunday, November 8, 2026')
    await expect(kpi(page, 'Screen on')).not.toHaveText(week ?? '')
    await page.getByRole('link', { name: 'Earlier period' }).click()
    await expect(page).toHaveURL(/date=2026-11-07/)
    await expect(page.getByTestId('range-label')).toHaveText('Saturday, November 7, 2026')

    await page.getByRole('link', { name: 'Custom' }).click()
    await expect(page).toHaveURL(/range=custom/)
    await page.getByLabel('From', { exact: true }).fill('2026-10-01')
    await page.getByLabel('To', { exact: true }).fill('2026-10-31')
    await page.getByRole('button', { name: 'Apply' }).click()
    await expect(page).toHaveURL(/from=2026-10-01&to=2026-10-31/)
    await expect(page.getByTestId('range-label')).toHaveText('Oct 1 – Oct 31')
  })

  test('device filter narrows every number', async ({ page }) => {
    await page.goto('/demo/remote')
    const all = await page.getByRole('heading', { level: 1 }).textContent()
    await page.getByLabel('Device').selectOption({ label: 'Bedroom TV' })
    await expect(page).toHaveURL(/device=demo-bedroom/)
    await expect(page.getByRole('heading', { level: 1 })).not.toHaveText(all ?? '')
    // The filter follows to other sections.
    await page.getByRole('link', { name: 'Apps' }).click()
    await expect(page).toHaveURL(/\/demo\/apps\?.*device=demo-bedroom/)
    await expect(page.getByLabel('Device')).toHaveValue('demo-bedroom')
  })

  test('calendar shows a day and compares two days', async ({ page }) => {
    await page.goto('/demo/calendar')
    await page.getByRole('link', { name: /Saturday, November 7, 2026/ }).click()
    await expect(page).toHaveURL(/day=2026-11-07/)
    await expect(page.getByTestId('day-detail')).toBeVisible()
    await page.getByRole('link', { name: 'Compare with previous week' }).click()
    await expect(page).toHaveURL(/a=2026-11-07&b=2026-10-31/)
    const compare = page.getByTestId('compare')
    await expect(compare.getByText('Saturday, November 7, 2026', { exact: true }).first()).toBeVisible()
    await expect(compare.getByText('Saturday, October 31, 2026', { exact: true }).first()).toBeVisible()

    await page.getByLabel('First').fill('2026-10-26..2026-11-01')
    await page.getByLabel('Second').fill('2026-11-02..2026-11-08')
    await page.getByRole('button', { name: 'Compare', exact: true }).click()
    await expect(compare.getByText('Oct 26 – Nov 1', { exact: true }).first()).toBeVisible()
    await expect(compare.getByText('Nov 2 – Nov 8', { exact: true }).first()).toBeVisible()
  })

  test('every section renders read-only', async ({ page }) => {
    const errors = watchConsole(page)
    for (const [path, heading] of [
      ['/demo/apps', 'apps used in this period'],
      ['/demo/remote', 'remote presses'],
      ['/demo/heatmap', 'When the TVs are on'],
      ['/demo/devices', 'paired'],
      ['/demo/members', 'Who watches what'],
      ['/demo/goals', 'Limits that keep'],
      ['/demo/settings', 'Your data, your rules'],
    ] as const) {
      await page.goto(path)
      await expect(page.getByRole('heading', { level: 1 })).toContainText(heading)
    }
    await expect(page.getByRole('button', { name: 'Delete everything' })).toBeDisabled()
    await page.goto('/demo/apps?q=net&sort=name')
    await expect(page.getByTestId('apps-table').getByRole('row')).toHaveCount(2)
    expect(errors).toEqual([])
  })

  test('heatmap gives every hour a column of the same width', async ({ page }) => {
    for (const width of [1280, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await page.goto('/demo/heatmap')
      const table = page.getByRole('table', { name: 'Average screen time per hour and weekday' })
      const box = await table.evaluate((element) => {
        const container = element.parentElement?.getBoundingClientRect()
        const cells = [...element.querySelectorAll('tbody tr:first-child td')].map((td) => td.getBoundingClientRect())
        const scroll = element.parentElement?.scrollWidth ?? 0
        return {
          widths: cells.map((c) => c.width),
          lastRight: cells.at(-1)?.right ?? Infinity,
          containerRight: (container?.left ?? 0) + scroll,
        }
      })
      expect(box.widths, `${width}px`).toHaveLength(24)
      expect(Math.max(...box.widths) - Math.min(...box.widths), `${width}px`).toBeLessThanOrEqual(1)
      expect(Math.min(...box.widths), `${width}px`).toBeGreaterThan(10)
      expect(box.lastRight, `${width}px`).toBeLessThanOrEqual(box.containerRight + 0.5)
      await expect(table.getByRole('columnheader')).toHaveText([
        '00:00',
        '03:00',
        '06:00',
        '09:00',
        '12:00',
        '15:00',
        '18:00',
        '21:00',
      ])
    }
    await expect(page.getByText('Each cell is the average screen time')).toBeVisible()
  })

  test('accounts are off in a demo-only deployment', async ({ page }) => {
    await page.goto('/app')
    await expect(page).toHaveURL(/\/demo$/)
    await page.goto('/login')
    await expect(page.getByText('Accounts are disabled on this deployment')).toBeVisible()
  })
})

test.describe('themes', () => {
  test('follows the system scheme', async ({ browser }) => {
    for (const [scheme, canvas] of [
      ['light', 'rgb(250, 250, 249)'],
      ['dark', 'rgb(12, 10, 9)'],
    ] as const) {
      const context = await browser.newContext({ colorScheme: scheme })
      const page = await context.newPage()
      await page.goto('/demo')
      await expect(page.locator('body')).toHaveCSS('background-color', canvas)
      await context.close()
    }
  })

  test('the toggle overrides the system and survives a reload', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto('/demo')
    await page.getByRole('button', { name: 'Color theme: system' }).click()
    await page.getByRole('button', { name: 'Color theme: light' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(12, 10, 9)')
    await page.reload()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await expect(page.getByRole('button', { name: 'Color theme: dark' })).toBeVisible()
  })
})

test.describe('mobile', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('no section scrolls sideways', async ({ page }) => {
    for (const path of [
      '/',
      '/demo',
      '/demo/calendar?day=2026-11-07&a=2026-11-07&b=2026-10-31',
      '/demo/apps',
      '/demo/remote',
      '/demo/heatmap',
      '/demo/devices',
      '/demo/goals',
      '/demo/settings',
    ]) {
      await page.goto(path)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, path).toBeLessThanOrEqual(0)
    }
  })

  test('section tabs stay reachable', async ({ page }) => {
    await page.goto('/demo')
    const settings = page.getByRole('link', { name: 'Settings' })
    await settings.scrollIntoViewIfNeeded()
    await settings.click()
    await expect(page).toHaveURL(/\/demo\/settings/)
  })
})

test.describe('device API in demo mode', () => {
  test('health reports demo mode', async ({ request }) => {
    const response = await request.get('/api/v1/health')
    expect(response.ok()).toBe(true)
    expect(await response.json()).toMatchObject({ status: 'ok', schemaVersion: 1, mode: 'demo' })
    expect(response.headers()['content-security-policy']).toContain("default-src 'none'")
  })

  test('sync answers an ApiError instead of accepting data', async ({ request }) => {
    const response = await request.post('/api/v1/sync', { data: {} })
    expect(response.status()).toBe(404)
    expect(await response.json()).toMatchObject({ error: 'not_found' })
  })

  test('pages carry a nonce-based CSP and security headers', async ({ request }) => {
    const response = await request.get('/demo')
    const headers = response.headers()
    expect(headers['content-security-policy']).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/)
    expect(headers['x-frame-options']).toBe('DENY')
    expect(headers['x-content-type-options']).toBe('nosniff')
    expect(headers['x-powered-by']).toBeUndefined()
  })
})
