// @vitest-environment happy-dom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { createDemoSource } from '../src/lib/data/demo-source'
import { sharedSource } from '../src/lib/data/source'
import { createTranslator } from '../src/lib/i18n/translate'
import type { SearchParams } from '../src/lib/time/range'
import type { Workspace } from '../src/lib/workspace'
import { SecurityView } from '../src/views/SecurityView'

const t = createTranslator()

async function show(source = createDemoSource(), params: SearchParams = {}) {
  const ws: Workspace = {
    source,
    basePath: '/demo',
    editable: false,
    settings: await source.settings(),
    locale: 'en',
    t,
  }
  render(await SecurityView({ ws, params, path: '/demo/security' }))
}

const rows = () => screen.getAllByTestId('security-event')
const names = () => rows().map((row) => row.querySelector('p')?.textContent ?? '')

afterEach(cleanup)

describe('SecurityView', () => {
  it('lists the demo story newest first, so wrong PINs before the reset can be read in order', async () => {
    await show()
    const list = names().map((name) => name.replace(/^⚠\s*/, '').replace(/\s*\(Needs attention\)$/, ''))
    expect(list[0]).toBe('PIN turned on')
    expect(list.indexOf('PIN reset confirmed on the web')).toBeLessThan(list.indexOf('Forgot PIN opened'))
    expect(list.indexOf('Forgot PIN opened')).toBeLessThan(list.lastIndexOf('Wrong PIN'))
    expect(list.at(-1)).toBe('App opened')
  })

  it('marks wrong PINs, waits, Forgot PIN and resets with text and an icon, not routine events', async () => {
    await show()
    for (const row of rows()) {
      const text = row.textContent
      const attention = /Wrong PIN|Wait started|Forgot PIN|reset confirmed/.test(text)
      expect(row.getAttribute('data-attention') === 'true').toBe(attention)
      expect(text.includes('Needs attention')).toBe(attention)
    }
  })

  it('shows the count of wrong tries and the wait, never anything typed', async () => {
    await show()
    expect(screen.getAllByText(/3 wrong tries so far/).length).toBe(1)
    expect(screen.getAllByText(/Wait of 30 s/).length).toBe(1)
    expect(document.body.textContent).not.toMatch(/1234|digits/)
  })

  it('filters by event type and by TV', async () => {
    await show(undefined, { type: 'pin_wrong' })
    expect(rows()).toHaveLength(4)
    expect(names().every((n) => n.includes('Wrong PIN'))).toBe(true)
    cleanup()
    await show(undefined, { device: 'demo-bedroom' })
    expect(screen.queryByTestId('security-events')).toBeNull()
    expect(screen.getByText('No events match this filter.')).toBeTruthy()
  })

  it('ignores an unknown type in the URL', async () => {
    await show(undefined, { type: 'pin_typed' })
    expect(rows().length).toBeGreaterThan(4)
  })

  it('offers the type and TV filters', async () => {
    await show()
    const type = screen.getByLabelText<HTMLSelectElement>('Event')
    expect(within(type).getAllByRole('option')).toHaveLength(11)
    expect(within(screen.getByLabelText('TV')).getAllByRole('option')).toHaveLength(3)
  })

  it('shows an empty state when a share link would be used, which never carries events', async () => {
    await show(sharedSource(createDemoSource(), null))
    expect(screen.getByText(/No events yet/)).toBeTruthy()
    expect(
      await sharedSource(createDemoSource(), null).securityEvents({ deviceIds: null, types: null, limit: 10 }),
    ).toEqual([])
  })
})
