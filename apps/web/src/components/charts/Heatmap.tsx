import { HEAT_CLASSES, heatLevel } from './scale'

interface HeatmapProps {
  caption: string
  rowLabels: readonly string[]
  columnLabels: readonly string[]
  /** `values[row][column]`. */
  values: readonly (readonly number[])[]
  format: (value: number) => string
  cellLabel: (row: number, column: number, value: string) => string
  /** Show every n-th column label. */
  labelEvery?: number
  legend: { less: string; more: string }
}

/** A real table, so screen readers can walk it; color only adds the shape at a glance. */
export function Heatmap({
  caption,
  rowLabels,
  columnLabels,
  values,
  format,
  cellLabel,
  labelEvery = 1,
  legend,
}: HeatmapProps) {
  const max = Math.max(0, ...values.flat())
  // Labels span `labelEvery` columns each, so every column keeps the same width.
  const groups = Array.from({ length: Math.ceil(columnLabels.length / labelEvery) }, (_, g) => ({
    start: g * labelEvery,
    span: Math.min(labelEvery, columnLabels.length - g * labelEvery),
  }))
  return (
    <div className="relative overflow-x-auto">
      <table className="w-full min-w-[560px] table-fixed border-separate border-spacing-[3px]">
        <caption className="sr-only">{caption}</caption>
        <colgroup>
          <col className="w-12" />
          {columnLabels.map((label) => (
            <col key={label} />
          ))}
        </colgroup>
        <thead>
          <tr>
            <td />
            {groups.map(({ start, span }) => (
              <th
                key={columnLabels[start]}
                scope="colgroup"
                colSpan={span}
                className="text-left text-caption font-normal text-faint"
              >
                {columnLabels[start]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rowLabels.map((rowLabel, row) => (
            <tr key={rowLabel}>
              <th scope="row" className="pr-2 text-left text-small font-normal whitespace-nowrap">
                {rowLabel}
              </th>
              {columnLabels.map((columnLabel, column) => {
                const value = values[row]?.[column] ?? 0
                const text = cellLabel(row, column, format(value))
                return (
                  <td
                    key={columnLabel}
                    title={text}
                    className={`h-6 rounded-icon ${HEAT_CLASSES[heatLevel(value, max)] ?? HEAT_CLASSES[0]}`}
                  >
                    <span className="sr-only">{text}</span>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <HeatLegend {...legend} />
    </div>
  )
}

export function HeatLegend({ less, more }: { less: string; more: string }) {
  return (
    <div className="mt-3 flex items-center justify-end gap-1.5 text-caption text-faint" aria-hidden="true">
      {less}
      {HEAT_CLASSES.map((c) => (
        <span key={c} className={`size-3 rounded-icon ${c}`} />
      ))}
      {more}
    </div>
  )
}
