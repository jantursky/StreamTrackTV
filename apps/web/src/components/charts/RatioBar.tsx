import { TONE_FILL, type Tone } from './scale'

interface RatioBarProps {
  /** 0..1 of the track; larger values are clamped. */
  ratio: number
  tone?: Tone
  /** Optional mark on the track, e.g. a limit at 0.8. */
  marker?: number
}

/** A thin horizontal bar for shares and progress. Decorative: the number is always printed next to it. */
export function RatioBar({ ratio, tone = 0, marker }: RatioBarProps) {
  const width = Math.min(1, Math.max(0, ratio)) * 100
  return (
    <svg viewBox="0 0 100 6" preserveAspectRatio="none" className="block h-1.5 w-full" aria-hidden="true">
      <rect x={0} y={0} width={100} height={6} rx={3} className="fill-line" />
      {width > 0 && <rect x={0} y={0} width={width} height={6} rx={3} className={TONE_FILL[tone]} />}
      {marker !== undefined && (
        <line
          x1={marker * 100}
          x2={marker * 100}
          y1={0}
          y2={6}
          className="stroke-ink"
          strokeWidth={1}
          vectorEffect="non-scaling-stroke"
        />
      )}
    </svg>
  )
}
