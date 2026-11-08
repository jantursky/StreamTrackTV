'use client'

import Link from 'next/link'
import { usePathname, useSearchParams } from 'next/navigation'
import { SHARED_PARAMS } from '@/lib/url'

interface NavTabsProps {
  label: string
  items: readonly { href: string; label: string }[]
}

/** Section tabs. Links keep the selected period and device, so switching sections keeps the context. */
export function NavTabs({ label, items }: NavTabsProps) {
  const pathname = usePathname()
  const search = useSearchParams()
  const shared = new URLSearchParams()
  for (const key of SHARED_PARAMS) {
    const value = search.get(key)
    if (value) shared.set(key, value)
  }
  const query = shared.toString()

  return (
    <nav aria-label={label} className="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <ul className="flex gap-2 py-1">
        {items.map((item) => (
          <li key={item.href}>
            <Link
              href={query ? `${item.href}?${query}` : item.href}
              aria-current={pathname === item.href ? 'page' : undefined}
              className="tab"
            >
              {item.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
