// Decides whether a build may migrate and how to reach the database. Kept free of I/O so it can be tested;
// scripts/migrate.mjs does the work. Written in erasable TypeScript, which Node runs directly.

export interface MigrationEnv {
  DATABASE_URL?: string | undefined
  POSTGRES_URL_NON_POOLING?: string | undefined
  VERCEL_ENV?: string | undefined
  MIGRATE_ON_PREVIEW?: string | undefined
  /** PEM of the CA that signed the database certificate; turns on certificate verification. */
  DATABASE_CA_CERT?: string | undefined
}

export type Tls = false | { rejectUnauthorized: boolean; ca?: string }

export type MigrationPlan =
  { run: false; reason: string } | { run: true; connectionString: string; ssl: Tls; caFile: string | null }

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]'])
const SSL_PARAMS = ['sslmode', 'sslrootcert', 'sslcert', 'sslkey', 'ssl', 'uselibpqcompat']

const present = (value: string | undefined) => (value === undefined || value.trim() === '' ? undefined : value)

export function migrationPlan(env: MigrationEnv): MigrationPlan {
  const databaseUrl = present(env.DATABASE_URL) ?? present(env.POSTGRES_URL_NON_POOLING)
  if (databaseUrl === undefined) return { run: false, reason: 'no DATABASE_URL or POSTGRES_URL_NON_POOLING set' }

  // Preview builds share the production database through the integration's variables; a branch must not
  // change its schema before the code that needs it is promoted.
  const target = present(env.VERCEL_ENV)
  if (target !== undefined && target !== 'production' && env.MIGRATE_ON_PREVIEW !== '1') {
    return { run: false, reason: `VERCEL_ENV is ${target}; set MIGRATE_ON_PREVIEW=1 to migrate from this build` }
  }

  // pg lets SSL parameters in the URL override the `ssl` option, so they are read here and removed.
  const url = new URL(databaseUrl)
  const mode = url.searchParams.get('sslmode')
  const caFile = url.searchParams.get('sslrootcert')
  for (const name of SSL_PARAMS) url.searchParams.delete(name)
  return { run: true, connectionString: url.toString(), ssl: tls(mode, url.hostname, env), caFile }
}

/**
 * Follows libpq: `disable` turns TLS off, `verify-ca` and `verify-full` check the certificate, and a
 * known CA makes every mode verify. Without a CA, a remote database is encrypted without verification:
 * Supabase signs its certificates with its own root CA, which is not in Node's trust store, so verifying
 * against the system store would always fail. Its CA can be downloaded from the dashboard
 * (Database settings, SSL configuration) and passed as DATABASE_CA_CERT or `sslrootcert`.
 */
function tls(mode: string | null, host: string, env: MigrationEnv): Tls {
  if (mode === 'disable') return false
  const ca = present(env.DATABASE_CA_CERT)
  if (ca !== undefined) return { rejectUnauthorized: true, ca }
  if (mode === 'verify-ca' || mode === 'verify-full') return { rejectUnauthorized: true }
  if (mode === null && LOCAL_HOSTS.has(host)) return false
  return { rejectUnauthorized: false }
}
