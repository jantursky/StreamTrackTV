import { describe, expect, it } from 'vitest'
import {
  addDays,
  addMonths,
  daysBetween,
  eachDay,
  isIsoDay,
  isIsoMonth,
  isTimeZone,
  lastDayOfMonth,
  monthGrid,
  pickTimeZone,
  startOfWeek,
  todayIn,
  weekdayIndex,
} from '../src/lib/time/days'
import {
  dayRange,
  formatSpan,
  parseRange,
  parseSpan,
  previousPeriod,
  rangeDays,
  rangeLength,
  rangeParams,
  stepRange,
  weekRange,
} from '../src/lib/time/range'

const TODAY = '2026-11-08' // a Sunday

describe('days', () => {
  it('validates real calendar days only', () => {
    expect(isIsoDay('2026-02-28')).toBe(true)
    expect(isIsoDay('2026-02-30')).toBe(false)
    expect(isIsoDay('2026-2-1')).toBe(false)
    expect(isIsoMonth('2026-11')).toBe(true)
    expect(isIsoMonth('2026-13')).toBe(false)
  })

  it('does day arithmetic across month and year ends', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
    expect(daysBetween('2026-11-01', '2026-11-08')).toBe(7)
    expect(eachDay('2026-10-30', '2026-11-02')).toEqual(['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'])
    expect(eachDay('2026-11-02', '2026-11-01')).toEqual([])
  })

  it('starts weeks on Monday', () => {
    expect(weekdayIndex('2026-11-02')).toBe(0)
    expect(weekdayIndex(TODAY)).toBe(6)
    expect(startOfWeek(TODAY)).toBe('2026-11-02')
  })

  it('handles months', () => {
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(lastDayOfMonth('2028-02')).toBe('2028-02-29')
  })

  it('lays out a month in Monday-first weeks', () => {
    const grid = monthGrid('2026-11')
    expect(grid).toHaveLength(6)
    expect(grid[0]).toEqual([null, null, null, null, null, null, '2026-11-01'])
    expect(grid.at(-1)?.[0]).toBe('2026-11-30')
    expect(grid.flat().filter(Boolean)).toHaveLength(30)
  })

  it('knows the current day in a time zone', () => {
    const instant = new Date('2026-11-08T23:30:00Z')
    expect(todayIn('UTC', instant)).toBe('2026-11-08')
    expect(todayIn('Europe/Bratislava', instant)).toBe('2026-11-09')
    expect(isTimeZone('Europe/Bratislava')).toBe(true)
    expect(isTimeZone('Mars/Olympus')).toBe(false)
  })

  it('keeps the previous day for a UTC-8 household until its own midnight', () => {
    // 07:30 UTC on Nov 9 is 23:30 on Nov 8 in Los Angeles (PST, UTC-8).
    const instant = new Date('2026-11-09T07:30:00Z')
    expect(todayIn('America/Los_Angeles', instant)).toBe('2026-11-08')
    expect(todayIn('UTC', instant)).toBe('2026-11-09')
    expect(todayIn('America/Los_Angeles', new Date('2026-11-09T08:00:00Z'))).toBe('2026-11-09')
  })

  it('prefers the chosen zone, then the last synced TV, then UTC', () => {
    expect(pickTimeZone('America/Los_Angeles', ['Europe/Bratislava'])).toEqual({
      timezone: 'America/Los_Angeles',
      source: 'account',
    })
    expect(pickTimeZone(null, [null, 'America/Los_Angeles', 'Europe/Bratislava'])).toEqual({
      timezone: 'America/Los_Angeles',
      source: 'device',
    })
    expect(pickTimeZone('Mars/Olympus', ['Mars/Olympus'])).toEqual({ timezone: 'UTC', source: 'default' })
    expect(pickTimeZone(null, [])).toEqual({ timezone: 'UTC', source: 'default' })
  })
})

describe('parseRange', () => {
  it('defaults to the current week, cut at today', () => {
    expect(parseRange({}, TODAY)).toEqual({ kind: 'week', from: '2026-11-02', to: '2026-11-08' })
    expect(parseRange({}, '2026-11-04')).toEqual({ kind: 'week', from: '2026-11-02', to: '2026-11-04' })
  })

  it('reads a day', () => {
    expect(parseRange({ range: 'day', date: '2026-11-03' }, TODAY)).toEqual(dayRange('2026-11-03'))
    expect(parseRange({ range: 'day' }, TODAY)).toEqual(dayRange(TODAY))
  })

  it('reads a custom span and falls back for invalid ones', () => {
    expect(parseRange({ range: 'custom', from: '2026-10-01', to: '2026-10-15' }, TODAY)).toEqual({
      kind: 'custom',
      from: '2026-10-01',
      to: '2026-10-15',
    })
    expect(parseRange({ range: 'custom', from: '2026-10-15', to: '2026-10-01' }, TODAY).kind).toBe('week')
    expect(parseRange({ range: 'custom', from: '2024-01-01', to: '2026-10-01' }, TODAY).kind).toBe('week')
    expect(parseRange({ range: 'custom', from: '2026-11-01', to: '2026-12-01' }, TODAY).kind).toBe('week')
  })

  it('ignores future and malformed dates', () => {
    expect(parseRange({ range: 'day', date: '2027-01-01' }, TODAY)).toEqual(dayRange(TODAY))
    expect(parseRange({ range: ['day', 'week'], date: 'nope' }, TODAY)).toEqual(dayRange(TODAY))
  })

  it('round-trips through rangeParams', () => {
    for (const range of [
      dayRange('2026-11-03'),
      weekRange('2026-10-20', TODAY),
      parseRange({ range: 'custom', from: '2026-10-01', to: '2026-10-09' }, TODAY),
    ]) {
      expect(parseRange(rangeParams(range), TODAY)).toEqual(range)
    }
  })
})

describe('range math', () => {
  it('finds the previous period of the same length', () => {
    expect(previousPeriod(weekRange('2026-11-04', '2026-11-04'))).toEqual({
      kind: 'week',
      from: '2026-10-26',
      to: '2026-11-01',
    })
    expect(previousPeriod({ kind: 'custom', from: '2026-11-01', to: '2026-11-03' })).toEqual({
      kind: 'custom',
      from: '2026-10-29',
      to: '2026-10-31',
    })
  })

  it('steps back and forward but never into the future', () => {
    expect(stepRange(dayRange(TODAY), 1, TODAY)).toBeNull()
    expect(stepRange(dayRange(TODAY), -1, TODAY)).toEqual(dayRange('2026-11-07'))
    expect(stepRange(weekRange(TODAY, TODAY), -1, TODAY)).toEqual(weekRange('2026-10-26', TODAY))
    expect(stepRange(weekRange(TODAY, TODAY), 1, TODAY)).toBeNull()
    expect(stepRange({ kind: 'custom', from: '2026-11-01', to: '2026-11-04' }, 1, TODAY)).toEqual({
      kind: 'custom',
      from: '2026-11-05',
      to: TODAY,
    })
    expect(stepRange({ kind: 'custom', from: '2026-11-05', to: '2026-11-08' }, 1, TODAY)).toBeNull()
  })

  it('lists the days of a range', () => {
    expect(rangeDays(weekRange(TODAY, TODAY))).toHaveLength(7)
    expect(rangeLength(dayRange(TODAY))).toBe(1)
  })
})

describe('parseSpan', () => {
  it('reads a day or an inclusive span', () => {
    expect(parseSpan('2026-11-01', TODAY)).toEqual(dayRange('2026-11-01'))
    expect(parseSpan('2026-11-01..2026-11-07', TODAY)).toEqual({ kind: 'custom', from: '2026-11-01', to: '2026-11-07' })
    expect(formatSpan({ kind: 'custom', from: '2026-11-01', to: '2026-11-07' })).toBe('2026-11-01..2026-11-07')
    expect(formatSpan(dayRange('2026-11-01'))).toBe('2026-11-01')
  })

  it('refuses reversed, future, missing and malformed spans', () => {
    expect(parseSpan(undefined, TODAY)).toBeNull()
    expect(parseSpan('2026-11-07..2026-11-01', TODAY)).toBeNull()
    expect(parseSpan('2026-11-09', TODAY)).toBeNull()
    expect(parseSpan('yesterday', TODAY)).toBeNull()
  })
})
