import { redirect } from 'next/navigation'
import { cache } from 'react'
import { isDemoOnly } from '../env'
import { createSessionClient } from '../supabase/server'
import { safeNext } from './redirects'

export interface SessionUser {
  id: string
  email: string | null
}

/** The signed-in user of this request, verified against the Supabase signing keys; once per request. */
export const currentUser = cache(async (): Promise<SessionUser | null> => {
  if (isDemoOnly()) return null
  const client = await createSessionClient()
  if (client === null) return null
  const { data } = await client.auth.getClaims()
  const claims = data?.claims
  if (!claims?.sub) return null
  return { id: claims.sub, email: typeof claims.email === 'string' ? claims.email : null }
})

export async function requireUser(next: string): Promise<SessionUser> {
  const user = await currentUser()
  if (user === null) redirect(`/login?next=${encodeURIComponent(safeNext(next))}`)
  return user
}
