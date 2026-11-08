// Type-checks the TypeScript code samples in the READMEs against the built `streamtrack-tv` package,
// so a sample can't drift from the API. Run `bun run build:schema` first. Usage: node scripts/samples.js [--check]
import { execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(import.meta.dirname, '..')
const dir = join(root, 'packages/schema/.samples')
const sources = ['README.md', 'packages/schema/README.md']

const samples = sources.flatMap((file) => {
  const text = readFileSync(join(root, file), 'utf8')
  return [...text.matchAll(/```ts\n([\s\S]*?)```/g)].map((m, i) => ({ name: `${file}#${i + 1}`, code: m[1] }))
})

rmSync(dir, { recursive: true, force: true })
mkdirSync(dir)
samples.forEach((s, i) => {
  // `export {}` keeps each sample its own module so top-level names don't collide.
  writeFileSync(join(dir, `sample-${i}.ts`), `${s.code}\nexport {}\n`)
})
writeFileSync(
  join(dir, 'tsconfig.json'),
  JSON.stringify({
    extends: '../../../tsconfig.base.json',
    compilerOptions: { noEmit: true, types: [] },
    include: ['*.ts'],
  }),
)

try {
  execFileSync('bunx', ['tsc', '-p', join(dir, 'tsconfig.json')], { cwd: root, stdio: 'inherit' })
  console.log(`${samples.length} README samples type-check`)
} catch {
  console.error('A README sample does not type-check:', samples.map((s) => s.name).join(', '))
  process.exitCode = 1
} finally {
  rmSync(dir, { recursive: true, force: true })
}
