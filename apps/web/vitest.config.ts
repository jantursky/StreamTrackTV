import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  test: {
    environment: 'node',
    include: ['test/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/lib/**/*.ts', 'src/components/**/*.tsx'],
      exclude: [
        // Adapters over Supabase, request cookies and the Next.js runtime. They hold no logic of their own
        // beyond wiring; the SQL behind them was checked against Postgres and the pages run in the e2e suite.
        'src/lib/supabase/server.ts',
        'src/lib/supabase/stores.ts',
        'src/lib/data/supabase-source.ts',
        'src/lib/account/actions.ts',
        'src/lib/auth/actions.ts',
        'src/lib/auth/session.ts',
        'src/lib/workspace.ts',
        'src/lib/digest/run.ts',
        'src/lib/http/route.ts',
        'src/components/dashboard/Shell.tsx',
        'src/components/auth/AuthCard.tsx',
        // Interfaces only.
        'src/lib/ingest/ports.ts',
      ],
      reporter: ['text-summary', 'text', 'html', 'lcov'],
      thresholds: { statements: 85, branches: 75, functions: 85, lines: 85 },
    },
  },
})
