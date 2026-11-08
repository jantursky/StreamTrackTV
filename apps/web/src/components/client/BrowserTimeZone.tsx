'use client'

import { useEffect, useState } from 'react'

/** IANA zone of the browser, e.g. `Europe/Bratislava`. */
const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

/** Hidden form field with the browser's time zone, filled after hydration so server and client agree. */
export function TimeZoneField() {
  const [zone, setZone] = useState('')
  useEffect(() => {
    setZone(browserTimeZone())
  }, [])
  return <input type="hidden" name="timezone" value={zone} />
}

/**
 * Hands the browser's zone to the server once, for an account that has not chosen one yet. The server
 * ignores it when a zone is already set.
 */
export function TimeZoneSync({ adopt }: { adopt: (timezone: string) => Promise<void> }) {
  useEffect(() => {
    void adopt(browserTimeZone())
  }, [adopt])
  return null
}
