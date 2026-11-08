const HOUR = 3_600_000
const MINUTE = 60_000

/** Smallest 1, 2 or 5 times a power of ten that is at least `raw`. */
export function niceStep(raw: number): number {
  if (raw <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].find((m) => m * power >= raw - 1e-9) ?? 10
  return step * power
}

/** Evenly spaced ticks from zero to a round value at or above `max`, in multiples of `unit`. */
export function linearTicks(max: number, count = 4, unit = 1): number[] {
  const step = niceStep(max / unit / count) * unit
  const top = Math.max(step, Math.ceil(max / step - 1e-9) * step)
  return Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step)
}

/** Ticks for millisecond values that land on whole minutes or hours. */
export function durationTicks(maxMs: number, count = 4): number[] {
  return linearTicks(maxMs, count, maxMs >= HOUR ? HOUR : MINUTE)
}

/** Bucket 0..5 for heat colors; zero stays zero so empty cells read as empty. */
export function heatLevel(value: number, max: number): number {
  if (value <= 0 || max <= 0) return 0
  return Math.min(5, Math.max(1, Math.ceil((value / max) * 5)))
}

export const HEAT_CLASSES = ['bg-heat-0', 'bg-heat-1', 'bg-heat-2', 'bg-heat-3', 'bg-heat-4', 'bg-heat-5'] as const

/** Series tones: the accent first, then stone steps (design rule: never a second hue). */
export const TONE_FILL = [
  'fill-series-1',
  'fill-series-2',
  'fill-series-3',
  'fill-series-4',
  'fill-series-5',
  'fill-series-6',
  'fill-series-7',
] as const
export type Tone = 0 | 1 | 2 | 3 | 4 | 5 | 6
