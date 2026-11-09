// Packs the library, installs the tarball into an empty project and imports it.
import { execFileSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const pkgDir = resolve(import.meta.dirname, '..')
const work = mkdtempSync(join(tmpdir(), 'streamtrack-tv-pack-'))
const run = (cmd, args, cwd) => execFileSync(cmd, args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })

const out = JSON.parse(run('npm', ['pack', '--json', '--pack-destination', work], pkgDir))
const [{ filename, files }] = out
const names = files.map((f) => f.path)
for (const required of ['dist/index.js', 'dist/index.d.ts', 'package.json', 'README.md', 'LICENSE']) {
  if (!names.includes(required)) throw new Error(`Packed tarball is missing ${required}`)
}
if (names.some((n) => n.startsWith('src/') || n.startsWith('test/')))
  throw new Error('Tarball contains sources or tests')

const app = join(work, 'app')
mkdirSync(app)
writeFileSync(join(app, 'package.json'), JSON.stringify({ name: 'pack-test', private: true, type: 'module' }))
run('npm', ['install', '--no-audit', '--no-fund', join(work, filename)], app)
writeFileSync(
  join(app, 'check.mjs'),
  `import { readFileSync } from 'node:fs'
import { syncPayloadSchema, SCHEMA_VERSION, pinResetStartResponseSchema, securityEventSchema } from 'streamtrack-tv'
import * as pkg from 'streamtrack-tv'
const ok = syncPayloadSchema.safeParse({ schemaVersion: SCHEMA_VERSION, sentAt: '2026-11-08T20:00:00Z', timezone: 'UTC', usage: [], keys: [], screen: [] })
if (!ok.success) throw new Error('empty payload should be valid')
if (typeof pinResetStartResponseSchema.safeParse !== 'function' || typeof securityEventSchema.safeParse !== 'function')
  throw new Error('1.1.0 schemas are missing from the tarball')
const readme = readFileSync(new URL('./node_modules/streamtrack-tv/README.md', import.meta.url), 'utf8')
const undocumented = Object.keys(pkg).filter((name) => !readme.includes('\\\`' + name))
if (undocumented.length > 0) throw new Error('README does not name: ' + undocumented.join(', '))
console.log('pack test ok')
`,
)
console.log(run('node', ['check.mjs'], app).trim())
