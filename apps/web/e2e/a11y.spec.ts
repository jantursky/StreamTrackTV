import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const PATHS = [
  '/demo',
  '/demo/calendar',
  '/demo/apps',
  '/demo/remote',
  '/demo/heatmap',
  '/demo/devices',
  '/demo/members',
  '/demo/goals',
  '/demo/security',
  '/demo/settings',
]
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']

/** Rules axe still reports, keyed by `scheme path`. Each is a `test.fixme` below, never a disabled rule. */
const KNOWN: Record<string, string> = {}

test.describe('accessibility', () => {
  test.skip(({ browserName }) => browserName !== 'chromium', 'axe rules are engine independent')

  for (const colorScheme of ['light', 'dark'] as const) {
    for (const path of PATHS) {
      const title = `axe: ${path} (${colorScheme})`
      const known = KNOWN[`${colorScheme} ${path}`]
      if (known) {
        test.fixme(`${title} [${known}]`, () => {})
        continue
      }
      test(title, async ({ page }) => {
        await page.emulateMedia({ colorScheme })
        await page.goto(path)
        await expect(page.getByRole('main')).toBeVisible()
        const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze()
        expect(violations.map((v) => ({ rule: v.id, nodes: v.nodes.map((n) => n.target.join(' ')) }))).toEqual([])
      })
    }
  }

  test('keyboard: skip link and section tabs', async ({ page }) => {
    await page.goto('/demo')
    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to content' })
    await expect(skip).toBeFocused()
    await expect(skip).toBeVisible()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/#main$/)
    await expect(page.locator('#main')).toBeInViewport()

    const nav = page.getByRole('navigation', { name: 'Dashboard sections' })
    const apps = nav.getByRole('link', { name: 'Apps', exact: true })
    await apps.focus()
    await page.keyboard.press('Shift+Tab')
    await page.keyboard.press('Tab')
    await expect(apps).toBeFocused()
    const outline = await apps.evaluate((el) => {
      const s = getComputedStyle(el)
      return { style: s.outlineStyle, width: parseFloat(s.outlineWidth) }
    })
    expect(outline.style).not.toBe('none')
    expect(outline.width).toBeGreaterThan(0)
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/demo\/apps/)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'Apps', exact: true })).toHaveAttribute('aria-current', 'page')
  })

  test('reduced motion removes button transitions', async ({ page }) => {
    await page.goto('/demo')
    const button = page.locator('.btn-ghost, .btn-primary').first()
    await expect(button).toBeVisible()
    const duration = () => button.evaluate((el) => getComputedStyle(el).transitionDuration)
    await page.emulateMedia({ reducedMotion: 'no-preference' })
    expect(await duration()).not.toBe('0s')
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const reduced = (await duration()).split(',').map((d) => parseFloat(d))
    expect(Math.max(...reduced)).toBeLessThanOrEqual(0.001)
  })
})
