// @vitest-environment happy-dom
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BarChart } from '../src/components/charts/BarChart'
import { Heatmap } from '../src/components/charts/Heatmap'
import { Legend } from '../src/components/charts/Legend'
import { MonthCalendar } from '../src/components/charts/MonthCalendar'
import { RatioBar } from '../src/components/charts/RatioBar'
import { Sparkline } from '../src/components/charts/Sparkline'
import { ActionForm, SubmitButton } from '../src/components/client/ActionForm'
import { TimeZoneField, TimeZoneSync } from '../src/components/client/BrowserTimeZone'
import { DeviceFilter } from '../src/components/client/DeviceFilter'
import { NavTabs } from '../src/components/client/NavTabs'
import { ThemeToggle } from '../src/components/client/ThemeToggle'
import { GoalList, formatGoalValue, goalTargetName } from '../src/components/dashboard/GoalList'
import { RangeBar } from '../src/components/dashboard/RangeBar'
import { Brand } from '../src/components/ui/Brand'
import { Headline } from '../src/components/ui/Headline'
import { Empty, Kpi, Panel } from '../src/components/ui/Panel'
import type { ActionState } from '../src/lib/action-state'
import { appDirectory } from '../src/lib/data/catalog'
import { createDemoSource } from '../src/lib/data/demo-source'
import type { Device, Goal } from '../src/lib/data/types'
import { createTranslator } from '../src/lib/i18n/translate'
import { monthGrid } from '../src/lib/time/days'
import type { Workspace } from '../src/lib/workspace'

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  pathname: '/demo/apps',
  search: new URLSearchParams('range=week&q=net'),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: navigation.push }),
  usePathname: () => navigation.pathname,
  useSearchParams: () => navigation.search,
}))

const t = createTranslator()

afterEach(cleanup)

describe('BarChart', () => {
  const props = {
    caption: 'Screen time per day',
    labels: ['Mon', 'Tue', 'Wed'],
    ticks: [0, 50, 100],
    format: (v: number) => `${v} m`,
  }

  it('draws bars to scale and exposes the numbers as a table', () => {
    const { container } = render(
      <BarChart {...props} series={[{ name: 'Screen on', tone: 0, values: [100, 50, 0] }]} emphasis={1} />,
    )
    const heights = [...container.querySelectorAll('rect')].map((r) => Number(r.getAttribute('height')))
    expect(heights).toEqual([100, 50, 0])
    expect(container.querySelectorAll('g.opacity-60')).toHaveLength(2)
    const table = screen.getByRole('table')
    expect(within(table).getByRole('row', { name: 'Tue 50 m' })).toBeTruthy()
    expect(screen.getByText('Screen time per day', { selector: 'figcaption' })).toBeTruthy()
  })

  it('stacks series on top of each other', () => {
    const { container } = render(
      <BarChart
        {...props}
        layout="stacked"
        labelEvery={2}
        series={[
          { name: 'A', tone: 0, values: [30, 0, 0] },
          { name: 'B', tone: 1, values: [20, 0, 0] },
        ]}
      />,
    )
    const [a, b] = [...container.querySelectorAll('rect')]
    expect(a?.getAttribute('y')).toBe('70')
    expect(b?.getAttribute('y')).toBe('50')
    expect(b?.getAttribute('class')).toBe('fill-series-2')
  })
})

describe('small charts', () => {
  it('Sparkline labels the trend and scales from zero', () => {
    render(<Sparkline values={[0, 10, 5]} label="Trend" />)
    const svg = screen.getByRole('img', { name: 'Trend' })
    expect(svg.querySelector('polyline')?.getAttribute('points')).toBe('0.00,29.00 50.00,1.00 100.00,15.00')
  })

  it('RatioBar clamps and marks', () => {
    const { container } = render(<RatioBar ratio={1.4} marker={0.8} />)
    expect(container.querySelectorAll('rect')[1]?.getAttribute('width')).toBe('100')
    expect(container.querySelector('line')?.getAttribute('x1')).toBe('80')
    const empty = render(<RatioBar ratio={0} />)
    expect(empty.container.querySelectorAll('rect')).toHaveLength(1)
  })

  it('Legend lists series', () => {
    render(<Legend items={[{ name: 'D-Pad', tone: 0 }]} />)
    expect(screen.getByText('D-Pad')).toBeTruthy()
  })

  it('Heatmap is a table with labelled cells and a legend', () => {
    render(
      <Heatmap
        caption="Grid"
        rowLabels={['Mon', 'Tue']}
        columnLabels={['00:00', '01:00', '02:00']}
        values={[
          [0, 10, 0],
          [5, 0, 0],
        ]}
        format={(v) => `${v}`}
        cellLabel={(r, c, v) => `${r}-${c}=${v}`}
        labelEvery={2}
        legend={{ less: 'Less', more: 'More' }}
      />,
    )
    expect(screen.getByRole('table', { name: 'Grid' })).toBeTruthy()
    const cell = screen.getByText('0-1=10').closest('td')
    expect(cell?.className).toContain('bg-heat-5')
    expect(screen.getByText('1-0=5').closest('td')?.className).toContain('bg-heat-3')
    expect(screen.getByText(/Less/).textContent).toBe('LessMore')
    // Every hour gets its own column; a label spans the columns it stands for.
    const table = screen.getByRole('table', { name: 'Grid' })
    expect(table.className).toContain('table-fixed')
    expect(table.querySelectorAll('colgroup col')).toHaveLength(4)
    const headers = within(table).getAllByRole('columnheader')
    expect(headers.map((h) => [h.textContent, h.getAttribute('colspan')])).toEqual([
      ['00:00', '2'],
      ['02:00', '1'],
    ])
    expect(table.querySelectorAll('tbody tr')[0]?.querySelectorAll('td')).toHaveLength(3)
  })

  it('MonthCalendar links past days and marks the selection', () => {
    render(
      <MonthCalendar
        caption="November 2026"
        weekdays={['M', 'T', 'W', 'T', 'F', 'S', 'S']}
        weeks={monthGrid('2026-11')}
        values={new Map([['2026-11-03', 100]])}
        today="2026-11-08"
        selected={new Set(['2026-11-03'])}
        hrefFor={(d) => `/demo/calendar?day=${d}`}
        cellLabel={(d, v) => `${d}: ${v}`}
      />,
    )
    const selected = screen.getByRole('link', { name: '2026-11-03: 100' })
    expect(selected.getAttribute('href')).toBe('/demo/calendar?day=2026-11-03')
    expect(selected.getAttribute('aria-current')).toBe('date')
    expect(selected.className).toContain('bg-heat-5')
    expect(screen.queryByRole('link', { name: /2026-11-09/ })).toBeNull()
    expect(screen.getAllByRole('link')).toHaveLength(8)
  })
})

describe('ui', () => {
  it('Headline renders exactly one highlight span', () => {
    const { container } = render(<Headline text="Know how your TV is {highlight} used." highlight="really" />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Know how your TV is really used.')
    expect(container.querySelectorAll('.highlight')).toHaveLength(1)
  })

  it('Panel labels its region by the title', () => {
    render(
      <Panel title="Top apps" aside={<span>aside</span>}>
        <Kpi label="Screen on" value="2 h" detail="+5%" />
        <Empty>Nothing</Empty>
      </Panel>,
    )
    expect(screen.getByRole('region', { name: 'Top apps' })).toBeTruthy()
    expect(screen.getByText('+5%')).toBeTruthy()
  })

  it('Brand links home', () => {
    render(<Brand href="/" name="StreamTrack TV" />)
    expect(screen.getByRole('link', { name: 'StreamTrack TV' }).getAttribute('href')).toBe('/')
  })
})

describe('GoalList', () => {
  const devices: Device[] = [
    { id: 'd1', name: 'Living room TV', model: null, memberId: null, lastSeenAt: null, revokedAt: null, createdAt: '' },
  ]
  const resolve = appDirectory([])
  const lookup = { devices, members: [{ id: 'm1', name: 'Mia' }], resolve, t }
  const goal = (g: Partial<Goal>): Goal => ({
    id: 'g',
    scope: 'device',
    target: 'd1',
    metric: 'screen_on',
    period: 'day',
    limit: 3_600_000,
    ...g,
  })

  it('names every kind of target', () => {
    expect(goalTargetName(goal({}), lookup)).toBe('Living room TV')
    expect(goalTargetName(goal({ target: 'gone' }), lookup)).toBe('Unknown')
    expect(goalTargetName(goal({ scope: 'member', target: 'm1' }), lookup)).toBe('Mia')
    expect(goalTargetName(goal({ scope: 'member', target: 'x' }), lookup)).toBe('Unknown')
    expect(goalTargetName(goal({ scope: 'app', target: 'com.netflix.ninja' }), lookup)).toBe('Netflix')
    expect(goalTargetName(goal({ scope: 'category', target: 'music' }), lookup)).toBe('Music')
    expect(goalTargetName(goal({ scope: 'category', target: 'custom' }), lookup)).toBe('custom')
  })

  it('spells out status and progress', () => {
    render(
      <GoalList
        items={[
          { goal: goal({}), used: 4_500_000, ratio: 1.25, status: 'over' },
          { goal: goal({ id: 'k', metric: 'keypresses', limit: 1000 }), used: 850, ratio: 0.85, status: 'near' },
        ]}
        targetName={(g) => goalTargetName(g, lookup)}
        t={t}
        locale="en"
      />,
    )
    expect(screen.getByText('Screen on of Living room TV per day')).toBeTruthy()
    expect(screen.getByText('Over limit')).toBeTruthy()
    expect(screen.getByText('1 h 15 min of 1 h')).toBeTruthy()
    expect(screen.getByText('850 of 1,000')).toBeTruthy()
    expect(formatGoalValue(goal({ metric: 'keypresses' }), 12345, t, 'en')).toBe('12,345')
  })
})

describe('ThemeToggle', () => {
  beforeEach(() => {
    delete document.documentElement.dataset.theme
    document.cookie = 'theme=; max-age=0; path=/'
  })

  it('cycles system, light, dark and remembers the choice in a cookie', () => {
    const labels = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }
    render(<ThemeToggle initial="system" labels={labels} />)
    fireEvent.click(screen.getByRole('button', { name: 'Theme: system' }))
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.cookie).toContain('theme=light')
    fireEvent.click(screen.getByRole('button', { name: 'Theme: light' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    fireEvent.click(screen.getByRole('button', { name: 'Theme: dark' }))
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(document.cookie).not.toContain('theme=dark')
  })
})

describe('URL-driven client components', () => {
  beforeEach(() => {
    navigation.push.mockReset()
  })

  it('DeviceFilter writes the device into the URL and keeps other parameters', () => {
    render(
      <DeviceFilter
        label="Device"
        allLabel="All devices"
        devices={[{ id: 'd1', name: 'Living room TV' }]}
        value={null}
      />,
    )
    fireEvent.change(screen.getByLabelText('Device'), { target: { value: 'd1' } })
    expect(navigation.push).toHaveBeenCalledWith('/demo/apps?range=week&q=net&device=d1', { scroll: false })
  })

  it('DeviceFilter removes the parameter for all devices', () => {
    navigation.search = new URLSearchParams('device=d1')
    render(<DeviceFilter label="Device" allLabel="All" devices={[{ id: 'd1', name: 'TV' }]} value="d1" />)
    fireEvent.change(screen.getByLabelText('Device'), { target: { value: '' } })
    expect(navigation.push).toHaveBeenCalledWith('/demo/apps', { scroll: false })
  })

  it('NavTabs marks the current section and carries the period over', () => {
    navigation.search = new URLSearchParams('range=day&date=2026-11-05&q=net')
    render(
      <NavTabs
        label="Sections"
        items={[
          { href: '/demo', label: 'Overview' },
          { href: '/demo/apps', label: 'Apps' },
        ]}
      />,
    )
    const apps = screen.getByRole('link', { name: 'Apps' })
    expect(apps.getAttribute('aria-current')).toBe('page')
    expect(screen.getByRole('link', { name: 'Overview' }).getAttribute('href')).toBe('/demo?range=day&date=2026-11-05')
  })
})

describe('ActionForm', () => {
  it('shows the result of the action in a live region', async () => {
    const action = vi.fn((_: ActionState, form: FormData) =>
      Promise.resolve<ActionState>({
        status: 'ok',
        message: `Saved ${form.get('name') as string}`,
        value: 'https://x/s/abc',
      }),
    )
    render(
      <ActionForm action={action} valueLabel="Link">
        <input name="name" defaultValue="Den" />
        <SubmitButton>Save</SubmitButton>
      </ActionForm>,
    )
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      await Promise.resolve()
    })
    expect((await screen.findByRole('status')).textContent).toBe('Saved Den')
    expect(screen.getByLabelText<HTMLInputElement>('Link').value).toBe('https://x/s/abc')
  })

  it('marks errors', async () => {
    render(
      <ActionForm action={() => Promise.resolve<ActionState>({ status: 'error', message: 'Nope' })}>
        <SubmitButton variant="ghost">Go</SubmitButton>
      </ActionForm>,
    )
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Go' }))
      await Promise.resolve()
    })
    expect((await screen.findByRole('status')).className).toContain('text-error')
  })
})

describe('RangeBar', () => {
  it('links the period kinds and steps, and offers a custom form', async () => {
    navigation.search = new URLSearchParams()
    const source = createDemoSource()
    const ws: Workspace = {
      source,
      basePath: '/demo',
      editable: false,
      settings: await source.settings(),
      locale: 'en',
      t,
    }
    const devices = await source.devices()
    const ctx = { today: '2026-11-08', devices, deviceIds: ['demo-bedroom'], resolve: appDirectory([]) }
    render(
      <RangeBar
        ws={ws}
        ctx={ctx}
        path="/demo"
        params={{ range: 'custom', from: '2026-11-01', to: '2026-11-04', device: 'demo-bedroom' }}
        range={{ kind: 'custom', from: '2026-11-01', to: '2026-11-04' }}
      />,
    )
    const query = (name: string) =>
      Object.fromEntries(
        new URL(screen.getByRole('link', { name }).getAttribute('href') ?? '', 'https://x').searchParams,
      )
    expect(query('Day')).toEqual({ range: 'day', date: '2026-11-04', device: 'demo-bedroom' })
    expect(screen.getByRole('link', { name: 'Custom' }).getAttribute('aria-current')).toBe('true')
    expect(query('Earlier period')).toEqual({
      range: 'custom',
      from: '2026-10-28',
      to: '2026-10-31',
      device: 'demo-bedroom',
    })
    expect(query('Later period')).toEqual({
      range: 'custom',
      from: '2026-11-05',
      to: '2026-11-08',
      device: 'demo-bedroom',
    })
    expect(screen.getByTestId('range-label').textContent).toBe('Nov 1 – Nov 4')
    expect(screen.getByLabelText<HTMLInputElement>('From').value).toBe('2026-11-01')
    expect(screen.getByLabelText<HTMLSelectElement>('Device').value).toBe('demo-bedroom')
  })
})

describe('browser time zone', () => {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone

  it('fills a hidden sign-up field after hydration', () => {
    const { container } = render(<TimeZoneField />)
    expect(container.querySelector<HTMLInputElement>('input[name="timezone"]')?.value).toBe(zone)
  })

  it('hands the zone to the server once', () => {
    const adopt = vi.fn(() => Promise.resolve())
    const { rerender, container } = render(<TimeZoneSync adopt={adopt} />)
    rerender(<TimeZoneSync adopt={adopt} />)
    expect(adopt).toHaveBeenCalledTimes(1)
    expect(adopt).toHaveBeenCalledWith(zone)
    expect(container.innerHTML).toBe('')
  })
})
