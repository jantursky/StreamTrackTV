import type { ReactNode } from 'react'

interface PanelProps {
  title: string
  /** Optional controls or a figure shown right of the title. */
  aside?: ReactNode
  children: ReactNode
  className?: string
}

/** A card with a section heading; the heading also labels the region for assistive technology. */
export function Panel({ title, aside, children, className = '' }: PanelProps) {
  const id = `panel-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
  return (
    <section aria-labelledby={id} className={`card min-w-0 ${className}`}>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="text-body-lg">
          {title}
        </h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

export function Kpi({ label, value, detail }: { label: string; value: string; detail?: string }) {
  return (
    <div className="card min-w-0 p-4 sm:p-6">
      <p className="text-small">{label}</p>
      <p className="mt-1 font-display text-subheading text-ink tabular-nums sm:text-heading">{value}</p>
      {detail !== undefined && <p className="mt-1 text-small text-faint">{detail}</p>}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-small text-faint">{children}</p>
}
