import { z } from 'zod'

/** The part of a PostgREST response we rely on; rows are validated with zod before use. */
export interface QueryResult {
  data: unknown
  error: { message: string } | null
}

export function unwrap(result: QueryResult): unknown {
  if (result.error) throw new Error(result.error.message)
  return result.data
}

export function parseRows<T>(schema: z.ZodType<T>, result: QueryResult): T[] {
  return z.array(schema).parse(unwrap(result) ?? [])
}

export function parseRow<T>(schema: z.ZodType<T>, result: QueryResult): T | null {
  const data = unwrap(result)
  return data === null ? null : schema.parse(data)
}

/** PostgREST caps responses (1000 rows by default), so long ranges are read page by page. */
const PAGE_SIZE = 1000

export async function fetchAllRows<T>(
  schema: z.ZodType<T>,
  page: (from: number, to: number) => PromiseLike<QueryResult>,
): Promise<T[]> {
  const rows: T[] = []
  for (let from = 0; ; from += PAGE_SIZE) {
    const batch = parseRows(schema, await page(from, from + PAGE_SIZE - 1))
    rows.push(...batch)
    if (batch.length < PAGE_SIZE) return rows
  }
}
