interface SparklineProps {
  values: readonly number[]
  /** Spoken summary, e.g. "Screen time per day, from 2 h to 5 h". */
  label: string
  className?: string
}

/** Trend line scaled from zero to the largest value. */
export function Sparkline({ values, label, className = 'h-10 w-full' }: SparklineProps) {
  const max = Math.max(...values, 1)
  const step = values.length > 1 ? 100 / (values.length - 1) : 100
  const points = values.map((v, i) => `${(i * step).toFixed(2)},${(30 - (v / max) * 28 - 1).toFixed(2)}`).join(' ')
  return (
    <svg viewBox="0 0 100 30" preserveAspectRatio="none" role="img" aria-label={label} className={className}>
      <line x1={0} x2={100} y1={29} y2={29} className="stroke-line" vectorEffect="non-scaling-stroke" />
      <polyline
        points={points}
        fill="none"
        className="stroke-accent"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  )
}
