#!/usr/bin/env node
// Applies supabase/migrations/*.sql in file-name order and records each file in public.schema_migrations.
// Runs before `next build`. It skips without a database URL (CI, demo-only deployments) and on Vercel
// preview builds, see migrate-config.ts.
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import pg from 'pg'
import { migrationPlan } from './migrate-config.ts'

const directory = fileURLToPath(new URL('../../../supabase/migrations/', import.meta.url))
const plan = migrationPlan(process.env)

if (!plan.run) {
  console.log(`migrate: ${plan.reason}, skipping migrations.`)
  process.exit(0)
}

const files = (await readdir(directory)).filter((name) => name.endsWith('.sql')).sort()
const ssl =
  plan.caFile && plan.ssl !== false ? { rejectUnauthorized: true, ca: await readFile(plan.caFile, 'utf8') } : plan.ssl
const client = new pg.Client({ connectionString: plan.connectionString, ssl })

await client.connect()
try {
  // One migrator at a time, even when two deployments build concurrently.
  await client.query("select pg_advisory_lock(hashtext('streamtrack_migrations'))")
  await client.query(`
    create table if not exists public.schema_migrations (
      name text primary key,
      applied_at timestamptz not null default now()
    );
    alter table public.schema_migrations enable row level security;
  `)
  await client.query(
    `do $$ begin
       if exists (select 1 from pg_roles where rolname = 'authenticated') then
         revoke all on public.schema_migrations from anon, authenticated;
       end if;
     end $$;`,
  )
  const { rows } = await client.query('select name from public.schema_migrations')
  const applied = new Set(rows.map((row) => row.name))
  const pending = files.filter((name) => !applied.has(name))

  for (const name of pending) {
    const sql = await readFile(join(directory, name), 'utf8')
    await client.query('begin')
    try {
      await client.query(sql)
      await client.query('insert into public.schema_migrations (name) values ($1)', [name])
      await client.query('commit')
      console.log(`migrate: applied ${name}`)
    } catch (error) {
      await client.query('rollback')
      throw new Error(`migrate: ${name} failed: ${error instanceof Error ? error.message : String(error)}`, {
        cause: error,
      })
    }
  }
  console.log(
    pending.length === 0 ? 'migrate: database is up to date.' : `migrate: ${pending.length} migration(s) applied.`,
  )
} finally {
  await client.end()
}
