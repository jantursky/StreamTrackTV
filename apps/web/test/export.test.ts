import { describe, expect, it } from 'vitest'
import type { Buckets, Device } from '../src/lib/data/types'
import {
  csvCell,
  exportFileName,
  exportRows,
  parseExportQuery,
  renderExport,
  toCsv,
  toJsonExport,
} from '../src/lib/export/export'

const devices: Device[] = [
  { id: 'd1', name: 'Living, "main" TV', model: 'X', memberId: null, lastSeenAt: null, revokedAt: null, createdAt: '' },
  { id: 'd2', name: '=HYPERLINK("x")', model: null, memberId: null, lastSeenAt: null, revokedAt: null, createdAt: '' },
]

const buckets: Buckets = {
  usage: [{ deviceId: 'd1', day: '2026-11-08', hour: 20, packageName: 'com.netflix.ninja', foregroundMs: 1_800_000 }],
  keys: [{ deviceId: 'd2', day: '2026-11-07', hour: 21, keyClass: 'dpad', count: 12 }],
  screen: [{ deviceId: 'd1', day: '2026-11-08', hour: 20, screenOnMs: 3_000_000 }],
}

describe('CSV export', () => {
  it('writes one sorted row per bucket with a header', () => {
    expect(toCsv(exportRows(buckets, devices))).toBe(
      [
        'device_id,device,day,hour,kind,key,value',
        `d2,"'=HYPERLINK(""x"")",2026-11-07,21,key,dpad,12`,
        'd1,"Living, ""main"" TV",2026-11-08,20,app,com.netflix.ninja,1800000',
        'd1,"Living, ""main"" TV",2026-11-08,20,screen,,3000000',
        '',
      ].join('\r\n'),
    )
  })

  it('quotes and neutralizes spreadsheet formulas', () => {
    expect(csvCell('plain')).toBe('plain')
    expect(csvCell('a,b')).toBe('"a,b"')
    expect(csvCell('+1')).toBe("'+1")
    expect(csvCell('@sum')).toBe("'@sum")
    expect(csvCell(-5)).toBe('-5')
    expect(csvCell('line\nbreak')).toBe('"line\nbreak"')
  })

  it('names unknown devices with an empty cell', () => {
    expect(exportRows(buckets, [])[0]?.device).toBe('')
  })
})

describe('JSON export', () => {
  it('keeps raw buckets and device names', () => {
    const json = toJsonExport(
      { from: '2026-11-01', to: '2026-11-08' },
      buckets,
      devices,
      new Date('2026-11-08T21:00:00Z'),
    )
    expect(json).toEqual({
      exportedAt: '2026-11-08T21:00:00.000Z',
      from: '2026-11-01',
      to: '2026-11-08',
      devices: [
        { id: 'd1', name: 'Living, "main" TV', model: 'X' },
        { id: 'd2', name: '=HYPERLINK("x")', model: null },
      ],
      ...buckets,
    })
  })
})

describe('export query and file', () => {
  it('validates the query', () => {
    const q = (s: string) => parseExportQuery(new URLSearchParams(s))
    expect(q('from=2026-11-01&to=2026-11-08')).toEqual({ from: '2026-11-01', to: '2026-11-08', format: 'csv' })
    expect(q('from=2026-11-01&to=2026-11-08&format=json&device=6f1c1b4e-3c51-4a52-9a4b-1d1f7d8f0b11')?.device).toBe(
      '6f1c1b4e-3c51-4a52-9a4b-1d1f7d8f0b11',
    )
    expect(q('from=2026-11-08&to=2026-11-01')).toBeNull()
    expect(q('from=2024-01-01&to=2026-11-01')).toBeNull()
    expect(q('from=2026-11-01&to=2026-11-08&format=xml')).toBeNull()
    expect(q('from=2026-11-01&to=2026-11-08&device=1')).toBeNull()
    expect(q('to=2026-11-08')).toBeNull()
  })

  it('renders CSV and JSON files with type and name', () => {
    const now = new Date('2026-11-08T21:00:00Z')
    const csv = renderExport({ from: '2026-11-01', to: '2026-11-08', format: 'csv' }, buckets, devices, now)
    expect(csv.contentType).toBe('text/csv; charset=utf-8')
    expect(csv.fileName).toBe('streamtrack-2026-11-01_2026-11-08.csv')
    expect(csv.body.split('\r\n')[0]).toBe('device_id,device,day,hour,kind,key,value')
    const json = renderExport({ from: '2026-11-01', to: '2026-11-08', format: 'json' }, buckets, devices, now)
    expect(JSON.parse(json.body)).toMatchObject({ from: '2026-11-01', usage: buckets.usage })
    expect(exportFileName({ from: 'a', to: 'b' }, 'json')).toBe('streamtrack-a_b.json')
  })
})
