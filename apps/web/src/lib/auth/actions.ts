'use server'

import { redirect } from 'next/navigation'
import { z } from 'zod'
import type { ActionState } from '../action-state'
import { formFields } from '../account/forms'
import { siteUrl } from '../env'
import { isTimeZone } from '../time/days'
import { createTranslator, type MessageKey } from '../i18n/translate'
import { createSessionClient } from '../supabase/server'
import { safeNext } from './redirects'

const email = z.email().max(254)
const password = z.string().min(8).max(72)

const fail = (key: MessageKey): ActionState => ({ status: 'error', message: createTranslator()(key) })
const done = (key: MessageKey): ActionState => ({ status: 'ok', message: createTranslator()(key) })

export async function signIn(_: ActionState, form: FormData): Promise<ActionState> {
  const input = z
    .object({ email, password: z.string().min(1), next: z.string().optional() })
    .safeParse(formFields(form))
  if (!input.success) return fail('auth.invalidCredentials')
  const client = await createSessionClient()
  if (client === null) return fail('auth.unavailable')
  const { error } = await client.auth.signInWithPassword({ email: input.data.email, password: input.data.password })
  if (error) return fail('auth.invalidCredentials')
  redirect(safeNext(input.data.next))
}

export async function signUp(_: ActionState, form: FormData): Promise<ActionState> {
  const fields = formFields(form)
  if (!email.safeParse(fields.email).success) return fail('auth.invalidEmail')
  const parsed = password.safeParse(fields.password)
  if (!parsed.success) return fail('auth.weakPassword')
  const client = await createSessionClient()
  if (client === null) return fail('auth.unavailable')
  // The database keeps the zone only if it knows the name; an empty or odd value leaves it unset.
  const timezone = fields.timezone !== undefined && isTimeZone(fields.timezone) ? fields.timezone : undefined
  const { error } = await client.auth.signUp({
    email: fields.email ?? '',
    password: parsed.data,
    options: { emailRedirectTo: `${siteUrl()}/auth/confirm?next=/app`, data: { timezone } },
  })
  // The same answer whether or not the address already has an account, so sign-up does not reveal users.
  return error && error.code !== 'user_already_exists' ? fail('common.failed') : done('auth.signUpDone')
}

export async function requestPasswordReset(_: ActionState, form: FormData): Promise<ActionState> {
  const parsed = email.safeParse(formFields(form).email)
  if (!parsed.success) return fail('auth.invalidEmail')
  const client = await createSessionClient()
  if (client === null) return fail('auth.unavailable')
  await client.auth.resetPasswordForEmail(parsed.data, {
    redirectTo: `${siteUrl()}/auth/confirm?next=/reset-password`,
  })
  return done('auth.forgotDone')
}

export async function updatePassword(_: ActionState, form: FormData): Promise<ActionState> {
  const parsed = password.safeParse(formFields(form).password)
  if (!parsed.success) return fail('auth.weakPassword')
  const client = await createSessionClient()
  if (client === null) return fail('auth.unavailable')
  const { error } = await client.auth.updateUser({ password: parsed.data })
  if (error) return fail('auth.linkInvalid')
  redirect('/app')
}

export async function signOut(): Promise<void> {
  const client = await createSessionClient()
  await client?.auth.signOut()
  redirect('/')
}
