// The slice of `pg` the database tests use; the package ships no types of its own.
declare module 'pg' {
  export interface QueryResult<R> {
    rows: R[]
    rowCount: number | null
  }
  export class Client {
    constructor(config: { connectionString: string })
    connect(): Promise<void>
    query<R = Record<string, unknown>>(text: string, values?: readonly unknown[]): Promise<QueryResult<R>>
    end(): Promise<void>
  }
  const pg: { Client: typeof Client }
  export default pg
}
