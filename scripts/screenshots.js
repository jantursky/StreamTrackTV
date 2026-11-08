// Builds the web app in demo mode, serves it and captures the README and docs screenshots:
// each view in light and dark at 1280×800 (device scale 2), saved as compressed PNGs.
// The docs site shows copies from apps/docs/public/screens. Run `bun run screenshots` after the look changes.
// The callbacks passed to evaluate() run in the page, hence the browser globals.
/* global document */
import { execSync, spawn } from 'node:child_process'
import { copyFileSync, mkdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

import { chromium } from 'playwright'
import sharp from 'sharp'

const root = join(import.meta.dirname, '..')
const web = join(root, 'apps/web')
const out = join(root, '.github/assets')
const docsOut = join(root, 'apps/docs/public/screens')
const port = 3199
const base = `http://localhost:${port}`

const views = [
  { name: 'home', path: '/' },
  { name: 'overview', path: '/demo' },
  { name: 'calendar', path: '/demo/calendar' },
  { name: 'heatmap', path: '/demo/heatmap' },
]

const env = { ...process.env, DEMO_ONLY: '1' }

const waitForServer = async () => {
  for (let i = 0; i < 120; i++) {
    try {
      if ((await fetch(`${base}/api/v1/health`)).ok) return
    } catch {
      // not up yet
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error('The demo server did not start')
}

const save = async (buffer, name) => {
  const file = join(out, `${name}.png`)
  await sharp(buffer).png({ palette: true, quality: 90, effort: 10, compressionLevel: 9 }).toFile(file)
  copyFileSync(file, join(docsOut, `${name}.png`))
  console.log(`${relative(root, file)}  ${Math.round(statSync(file).size / 1024)} KB`)
}

// Fonts loaded, no requests in flight and charts drawn.
const settle = async (page) => {
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready)
  await page.waitForTimeout(500)
}

execSync('bun run build:schema', { cwd: root, stdio: 'inherit' })
execSync('bunx next build', { cwd: web, stdio: 'inherit', env })
mkdirSync(out, { recursive: true })
mkdirSync(docsOut, { recursive: true })

const server = spawn('bunx', ['next', 'start', '--port', String(port)], { cwd: web, env, stdio: 'ignore' })
const browser = await chromium.launch()

try {
  await waitForServer()
  for (const theme of ['light', 'dark']) {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 800 },
      deviceScaleFactor: 2,
      colorScheme: theme,
      reducedMotion: 'reduce',
    })
    const page = await context.newPage()
    for (const view of views) {
      await page.goto(base + view.path)
      await settle(page)
      await save(await page.screenshot(), `${view.name}-${theme}`)
    }
    await context.close()
  }
} finally {
  await browser.close()
  server.kill()
}
