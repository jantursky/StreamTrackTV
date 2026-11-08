import Link from 'next/link'
import { BarChart } from '@/components/charts/BarChart'
import { durationTicks } from '@/components/charts/scale'
import { Shell } from '@/components/dashboard/Shell'
import { Headline } from '@/components/ui/Headline'
import { screenByDay, totals } from '@/lib/analytics/aggregate'
import { createDemoSource } from '@/lib/data/demo-source'
import { isDemoOnly } from '@/lib/env'
import { formatDay, formatDuration, formatDurationShort, formatNumber } from '@/lib/i18n/format'
import { createTranslator } from '@/lib/i18n/translate'
import { addDays, eachDay } from '@/lib/time/days'

const t = createTranslator()
const REPOSITORY = 'https://github.com/jantursky/StreamTrackTV'
const FEATURES = ['overview', 'calendar', 'habits', 'goals', 'household', 'export'] as const
const PRIVACY = ['keys', 'content', 'hosting', 'tokens'] as const

/** A week of the demo living room, as a live preview of the dashboard. */
async function Preview() {
  const source = createDemoSource()
  const today = await source.today()
  const days = eachDay(addDays(today, -6), today)
  const [device] = await source.devices()
  const buckets = await source.buckets({ from: days[0] ?? today, to: today }, device ? [device.id] : null)
  const daily = screenByDay(buckets, days)
  const sums = totals(buckets)
  return (
    <div className="card rounded-feature shadow-float" aria-label={t('landing.previewTitle')} role="figure">
      <p className="text-small">{t('landing.previewTitle')}</p>
      <div className="mt-2 mb-6 flex flex-wrap gap-x-8 gap-y-2">
        <p>
          <span className="block font-display text-heading text-ink tabular-nums">
            {formatDuration(sums.screenOnMs, t)}
          </span>
          <span className="text-small">{t('kpi.screenOn')}</span>
        </p>
        <p>
          <span className="block font-display text-heading text-ink tabular-nums">
            {formatNumber(sums.keypresses, 'en')}
          </span>
          <span className="text-small">{t('kpi.keypresses')}</span>
        </p>
      </div>
      <BarChart
        caption={t('overview.trend')}
        labels={days.map((d) => formatDay(d, 'en', 'weekday'))}
        series={[{ name: t('kpi.screenOn'), tone: 0, values: daily }]}
        ticks={durationTicks(Math.max(...daily, 1))}
        format={(v) => formatDurationShort(v, t)}
      />
    </div>
  )
}

export default function LandingPage() {
  const accounts = !isDemoOnly()
  return (
    <Shell
      t={t}
      actions={
        <>
          <Link href={REPOSITORY} className="btn-ghost hidden sm:inline-flex">
            {t('nav.docs')}
          </Link>
          {accounts && (
            <Link href="/login" className="btn-ghost">
              {t('nav.signIn')}
            </Link>
          )}
        </>
      }
    >
      <div className="flex flex-col gap-24 pt-8 sm:pt-12">
        <section className="grid items-center gap-12 lg:grid-cols-2" aria-labelledby="hero">
          <div className="flex flex-col items-start gap-6">
            <p className="text-small font-medium text-ink">{t('landing.eyebrow')}</p>
            <Headline id="hero" size="display" text={t('landing.headline')} highlight={t('landing.highlight')} />
            <p className="max-w-xl text-body-lg">{t('landing.lead')}</p>
            <div className="flex flex-wrap gap-3">
              <Link href="/demo" className="btn-primary">
                {t('landing.ctaDemo')}
              </Link>
              {accounts && (
                <Link href="/signup" className="btn-ghost">
                  {t('landing.ctaStart')}
                </Link>
              )}
            </div>
          </div>
          <Preview />
        </section>

        <section aria-labelledby="features" className="flex flex-col gap-8">
          <h2 id="features" className="text-heading">
            {t('landing.featuresTitle')}
          </h2>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <li key={f} className="card rounded-feature">
                <h3 className="text-subheading">{t(`landing.features.${f}.title`)}</h3>
                <p className="mt-2">{t(`landing.features.${f}.body`)}</p>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="privacy" className="grid gap-8 lg:grid-cols-3">
          <h2 id="privacy" className="text-heading">
            {t('landing.privacyTitle')}
          </h2>
          <ul className="flex flex-col divide-y divide-line border-y border-line lg:col-span-2">
            {PRIVACY.map((p) => (
              <li key={p} className="py-4 text-body-lg">
                {t(`landing.privacy.${p}`)}
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="deploy" className="flex flex-col items-start gap-4">
          <h2 id="deploy" className="text-heading">
            {t('landing.deployTitle')}
          </h2>
          <p className="max-w-2xl text-body-lg">{t('landing.deployBody')}</p>
          <Link href={REPOSITORY} className="btn-ghost">
            {t('nav.docs')}
          </Link>
        </section>

        <footer className="flex flex-wrap justify-between gap-3 border-t border-line pt-6 text-small">
          <span>
            {t('app.name')} · {t('landing.footer')}
          </span>
          <Link href="/demo" className="link">
            {t('nav.demo')}
          </Link>
        </footer>
      </div>
    </Shell>
  )
}
