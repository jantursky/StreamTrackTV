import { TONE_FILL, type Tone } from './scale'

export interface Series {
  name: string
  tone: Tone
  values: readonly number[]
}

interface BarChartProps {
  /** Describes the chart for screen readers; the values follow as a table. */
  caption: string
  labels: readonly string[]
  series: readonly Series[]
  /** Ascending, starting at zero; the last tick is the top of the scale. */
  ticks: readonly number[]
  format: (value: number) => string
  layout?: 'grouped' | 'stacked'
  /** Show every n-th x label, so 24 hours stay readable on a phone. */
  labelEvery?: number
  /** Index of a column to emphasize, e.g. today. */
  emphasis?: number
}

const SLOT = 10
const HEIGHT = 100

/**
 * Bars to scale from zero, drawn as SVG and stretched to the container width. The picture is hidden from
 * assistive technology, which reads the same numbers from a visually hidden table instead.
 */
export function BarChart({
  caption,
  labels,
  series,
  ticks,
  format,
  layout = 'grouped',
  labelEvery = 1,
  emphasis,
}: BarChartProps) {
  const top = ticks.at(-1) ?? 1
  const columns = labels.length
  const scale = (value: number) => (Math.max(0, value) / top) * HEIGHT
  const groupWidth = SLOT * 0.7
  const barWidth = layout === 'stacked' ? groupWidth : groupWidth / series.length

  return (
    <figure className="relative m-0">
      <figcaption className="sr-only">{caption}</figcaption>
      <div className="flex gap-2" aria-hidden="true">
        <div className="flex h-40 flex-col-reverse justify-between text-right text-caption text-faint tabular-nums">
          {ticks.map((tick) => (
            <span key={tick} className="-my-1.5 leading-3">
              {format(tick)}
            </span>
          ))}
        </div>
        <div className="min-w-0 flex-1">
          <svg
            viewBox={`0 0 ${columns * SLOT} ${HEIGHT}`}
            preserveAspectRatio="none"
            className="block h-40 w-full overflow-visible"
          >
            {ticks.map((tick) => (
              <line
                key={tick}
                x1={0}
                x2={columns * SLOT}
                y1={HEIGHT - scale(tick)}
                y2={HEIGHT - scale(tick)}
                className="stroke-line"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
              />
            ))}
            {labels.map((label, column) => {
              let stackTop = HEIGHT
              const x = column * SLOT + (SLOT - groupWidth) / 2
              return (
                <g key={label} className={emphasis !== undefined && emphasis !== column ? 'opacity-60' : undefined}>
                  {series.map((s, index) => {
                    const height = scale(s.values[column] ?? 0)
                    const y = layout === 'stacked' ? stackTop - height : HEIGHT - height
                    if (layout === 'stacked') stackTop = y
                    return (
                      <rect
                        key={s.name}
                        x={layout === 'stacked' ? x : x + index * barWidth}
                        y={y}
                        width={barWidth}
                        height={height}
                        className={TONE_FILL[s.tone]}
                      />
                    )
                  })}
                </g>
              )
            })}
          </svg>
          <div className="mt-1 flex text-caption text-faint">
            {labels.map((label, i) => (
              <span key={label} className="min-w-0 flex-1 overflow-visible text-center whitespace-nowrap">
                {i % labelEvery === 0 ? label : ''}
              </span>
            ))}
          </div>
        </div>
      </div>
      <div className="sr-only">
        <table>
          <thead>
            <tr>
              <th scope="col">{caption}</th>
              {series.map((s) => (
                <th key={s.name} scope="col">
                  {s.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {labels.map((label, i) => (
              <tr key={label}>
                <th scope="row">{label}</th>
                {series.map((s) => (
                  <td key={s.name}>{format(s.values[i] ?? 0)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  )
}
