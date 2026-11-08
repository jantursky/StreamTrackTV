import type { SearchParams } from '@/lib/time/range'
import type { Workspace } from '@/lib/workspace'

export interface ViewProps {
  ws: Workspace
  params: SearchParams
  /** Path of the current section, e.g. `/demo/apps`. */
  path: string
}
