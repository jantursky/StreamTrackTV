import Link from 'next/link'

/** The squircle mark: a play triangle and two rising bars. */
export function LogoMark({ className = 'size-7' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect x={2} y={2} width={60} height={60} rx={19} className="fill-logo-tile" />
      <rect x={9} y={38} width={5.5} height={9} rx={2.75} className="fill-logo-ink" />
      <rect x={17} y={30} width={5.5} height={17} rx={2.75} className="fill-logo-ink" />
      <path
        d="M31 17l21 15-21 15z"
        strokeWidth={5}
        strokeLinejoin="round"
        className="fill-logo-cream stroke-logo-cream"
      />
    </svg>
  )
}

export function Brand({ href, name }: { href: string; name: string }) {
  return (
    <Link href={href} className="flex items-center gap-2 font-display text-body-lg font-medium text-ink">
      <LogoMark />
      <span>{name}</span>
    </Link>
  )
}
