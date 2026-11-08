import Link from 'next/link'
import { HEAT_CLASSES, heatLevel } from './scale'

interface MonthCalendarProps {
  caption: string
  weekdays: readonly string[]
  /** Weeks of the month, Monday first; `null` pads days of the neighbouring months. */
  weeks: readonly (readonly (string | null)[])[]
  values: ReadonlyMap<string, number>
  today: string
  selected: ReadonlySet<string>
  hrefFor: (day: string) => string
  cellLabel: (day: string, value: number) => string
}

/** Month grid colored by screen time; each past day links to its detail. */
export function MonthCalendar({
  caption,
  weekdays,
  weeks,
  values,
  today,
  selected,
  hrefFor,
  cellLabel,
}: MonthCalendarProps) {
  const max = Math.max(0, ...values.values())
  return (
    <table className="w-full table-fixed border-separate border-spacing-1">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>
          {weekdays.map((w) => (
            <th key={w} scope="col" className="pb-1 text-caption font-normal text-faint">
              {w}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {weeks.map((week, row) => (
          <tr key={week.find((d) => d !== null) ?? row}>
            {week.map((day, column) => {
              if (day === null) return <td key={`pad-${row}-${column}`} />
              const value = values.get(day) ?? 0
              const level = heatLevel(value, max)
              const number = Number(day.slice(8))
              const tone = level >= 4 ? 'text-on-accent' : 'text-ink'
              const ring = selected.has(day) ? 'outline-2 outline-offset-1 outline-ink' : ''
              const cell = `flex aspect-square min-h-9 flex-col justify-between rounded-icon p-1 text-small sm:p-1.5 ${HEAT_CLASSES[level] ?? HEAT_CLASSES[0]} ${tone} ${ring}`
              return (
                <td key={day}>
                  {day > today ? (
                    <span className={`${cell} opacity-40`}>{number}</span>
                  ) : (
                    <Link
                      href={hrefFor(day)}
                      aria-label={cellLabel(day, value)}
                      aria-current={selected.has(day) ? 'date' : undefined}
                      className={cell}
                      scroll={false}
                    >
                      {number}
                    </Link>
                  )}
                </td>
              )
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
