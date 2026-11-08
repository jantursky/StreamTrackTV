import { describe, expect, it } from 'vitest'
import { migrationPlan } from '../scripts/migrate-config'

const REMOTE = 'postgres://postgres:pw@db.abcd.supabase.co:5432/postgres'

describe('migrationPlan', () => {
  it('skips without a database URL', () => {
    expect(migrationPlan({})).toEqual({ run: false, reason: 'no DATABASE_URL or POSTGRES_URL_NON_POOLING set' })
    expect(migrationPlan({ DATABASE_URL: ' ' }).run).toBe(false)
  })

  it('migrates from production and local builds only, unless previews opt in', () => {
    expect(migrationPlan({ DATABASE_URL: REMOTE }).run).toBe(true)
    expect(migrationPlan({ DATABASE_URL: REMOTE, VERCEL_ENV: 'production' }).run).toBe(true)
    const preview = migrationPlan({ POSTGRES_URL_NON_POOLING: REMOTE, VERCEL_ENV: 'preview' })
    expect(preview).toMatchObject({ run: false, reason: expect.stringContaining('MIGRATE_ON_PREVIEW=1') as string })
    expect(migrationPlan({ DATABASE_URL: REMOTE, VERCEL_ENV: 'development' }).run).toBe(false)
    expect(migrationPlan({ DATABASE_URL: REMOTE, VERCEL_ENV: 'preview', MIGRATE_ON_PREVIEW: '1' }).run).toBe(true)
  })

  it('prefers DATABASE_URL and strips TLS parameters pg would let override the options', () => {
    const plan = migrationPlan({
      DATABASE_URL: `${REMOTE}?sslmode=require&application_name=migrate`,
      POSTGRES_URL_NON_POOLING: 'postgres://other/db',
    })
    expect(plan).toMatchObject({
      run: true,
      connectionString: `${REMOTE}?application_name=migrate`,
      caFile: null,
    })
  })

  it('encrypts without verification only where no CA is known and none was asked for', () => {
    const ssl = (url: string, ca?: string) => {
      const plan = migrationPlan({ DATABASE_URL: url, DATABASE_CA_CERT: ca })
      return plan.run ? plan.ssl : null
    }
    expect(ssl(REMOTE)).toEqual({ rejectUnauthorized: false })
    expect(ssl(`${REMOTE}?sslmode=require`)).toEqual({ rejectUnauthorized: false })
    expect(ssl(`${REMOTE}?sslmode=verify-full`)).toEqual({ rejectUnauthorized: true })
    expect(ssl(`${REMOTE}?sslmode=verify-ca`)).toEqual({ rejectUnauthorized: true })
    expect(ssl(REMOTE, 'PEM')).toEqual({ rejectUnauthorized: true, ca: 'PEM' })
    expect(ssl(`${REMOTE}?sslmode=disable`, 'PEM')).toBe(false)
    expect(ssl('postgres://postgres@localhost:5432/postgres')).toBe(false)
    expect(ssl('postgres://postgres@127.0.0.1:5432/postgres?sslmode=require')).toEqual({ rejectUnauthorized: false })
  })

  it('passes a root certificate file on for verification', () => {
    const plan = migrationPlan({ DATABASE_URL: `${REMOTE}?sslmode=verify-full&sslrootcert=/etc/ca.pem` })
    expect(plan).toMatchObject({ run: true, caFile: '/etc/ca.pem', ssl: { rejectUnauthorized: true } })
  })
})
