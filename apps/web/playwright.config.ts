import { defineConfig, devices } from '@playwright/test'

const port = 3100

/** Runs against a production build in demo mode, so the suite needs no database. */
export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['list']] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
  webServer: {
    command: `next build && next start --port ${port}`,
    port,
    timeout: 240_000,
    reuseExistingServer: !process.env.CI,
    env: { DEMO_ONLY: '1' },
  },
})
