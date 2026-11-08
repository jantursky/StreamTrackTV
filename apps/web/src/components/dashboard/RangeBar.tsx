import Link from 'next/link'
import { DeviceFilter } from '../client/DeviceFilter'
import { formatSpanLabel } from '@/lib/i18n/format'
import { rangeParams, stepRange, type DayRange, type SearchParams } from '@/lib/time/range'
import { withParams } from '@/lib/url'
import type { ViewContext, Workspace } from '@/lib/workspace'

interface RangeBarProps {
  ws: Workspace
  ctx: ViewContext
  path: string
  params: SearchParams
  range: DayRange
}

const CHEVRON = { back: 'M15 6l-6 6 6 6', forward: 'M9 6l6 6-6 6' }

function Chevron({ direction }: { direction: keyof typeof CHEVRON }) {
  return (
    <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path d={CHEVRON[direction]} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** Period tabs, stepping and the device filter. All state lives in the URL. */
export function RangeBar({ ws, ctx, path, params, range }: RangeBarProps) {
  const { t, locale } = ws
  const earlier = stepRange(range, -1, ctx.today)
  const later = stepRange(range, 1, ctx.today)
  const step = 'inline-flex size-8 items-center justify-center rounded-pill border border-line text-ink'
  const kinds = [
    { kind: 'day', label: t('range.day'), overrides: { range: 'day', date: range.to } },
    { kind: 'week', label: t('range.week'), overrides: { range: 'week', date: range.from } },
    { kind: 'custom', label: t('range.custom'), overrides: { range: 'custom', from: range.from, to: range.to } },
  ] as const

  return (
    <div className="flex flex-col gap-3" data-testid="range-bar">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <nav aria-label={t('range.label')}>
            <ul className="flex gap-1">
              {kinds.map((k) => (
                <li key={k.kind}>
                  <Link
                    href={withParams(path, params, k.overrides)}
                    aria-current={range.kind === k.kind ? 'true' : undefined}
                    className="tab"
                    scroll={false}
                  >
                    {k.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <div className="flex items-center gap-2">
            {earlier ? (
              <Link
                href={withParams(path, params, rangeParams(earlier))}
                aria-label={t('range.earlier')}
                className={step}
                scroll={false}
              >
                <Chevron direction="back" />
              </Link>
            ) : (
              <span className={`${step} opacity-40`} aria-hidden="true">
                <Chevron direction="back" />
              </span>
            )}
            <span className="min-w-36 text-center text-small text-ink tabular-nums" data-testid="range-label">
              {formatSpanLabel(range, locale)}
            </span>
            {later ? (
              <Link
                href={withParams(path, params, rangeParams(later))}
                aria-label={t('range.later')}
                className={step}
                scroll={false}
              >
                <Chevron direction="forward" />
              </Link>
            ) : (
              <span className={`${step} opacity-40`} aria-hidden="true">
                <Chevron direction="forward" />
              </span>
            )}
          </div>
        </div>
        {ctx.devices.length > 1 && (
          <DeviceFilter
            label={t('range.device')}
            allLabel={t('common.allDevices')}
            devices={ctx.devices}
            value={ctx.deviceIds?.[0] ?? null}
          />
        )}
      </div>
      {range.kind === 'custom' && (
        <form method="get" action={path} className="flex flex-wrap items-end gap-3">
          <input type="hidden" name="range" value="custom" />
          {ctx.deviceIds && <input type="hidden" name="device" value={ctx.deviceIds[0]} />}
          <label>
            <span className="label">{t('common.from')}</span>
            <input type="date" name="from" defaultValue={range.from} max={ctx.today} className="input" required />
          </label>
          <label>
            <span className="label">{t('common.to')}</span>
            <input type="date" name="to" defaultValue={range.to} max={ctx.today} className="input" required />
          </label>
          <button type="submit" className="btn-ghost">
            {t('common.apply')}
          </button>
        </form>
      )}
    </div>
  )
}
