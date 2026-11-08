'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useId } from 'react'

interface DeviceFilterProps {
  label: string
  allLabel: string
  devices: readonly { id: string; name: string }[]
  value: string | null
}

/** Device picker that keeps its value in the `device` URL parameter. */
export function DeviceFilter({ label, allLabel, devices, value }: DeviceFilterProps) {
  const router = useRouter()
  const pathname = usePathname()
  const search = useSearchParams()
  const id = useId()

  function select(device: string) {
    const params = new URLSearchParams(search.toString())
    if (device === '') params.delete('device')
    else params.set('device', device)
    const query = params.toString()
    router.push(query ? `${pathname}?${query}` : pathname, { scroll: false })
  }

  return (
    <div className="flex items-center gap-2">
      <label htmlFor={id} className="text-small text-muted">
        {label}
      </label>
      <select
        id={id}
        className="input w-auto max-w-48 py-1.5"
        value={value ?? ''}
        onChange={(event) => {
          select(event.target.value)
        }}
      >
        <option value="">{allLabel}</option>
        {devices.map((d) => (
          <option key={d.id} value={d.id}>
            {d.name}
          </option>
        ))}
      </select>
    </div>
  )
}
