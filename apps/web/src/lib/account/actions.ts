'use server'

import type { SupabaseClient } from '@supabase/supabase-js'
import { revalidatePath } from 'next/cache'
import { redirect } from 'next/navigation'
import type { z } from 'zod'
import type { ActionState } from '../action-state'
import { currentUser, type SessionUser } from '../auth/session'
import { siteUrl } from '../env'
import { createTranslator, type MessageKey, type Translate } from '../i18n/translate'
import { confirmPairing } from '../pairing/pairing'
import { generateToken, sha256 } from '../security/tokens'
import { isTimeZone } from '../time/days'
import { createAdminClient, createSessionClient } from '../supabase/server'
import { supabasePairingStore, supabaseRateLimiter } from '../supabase/stores'
import { unwrap } from '../supabase/query'
import {
  categoryForm,
  deleteAccountForm,
  deleteDeviceDataForm,
  deviceForm,
  formFields,
  goalForm,
  idForm,
  memberForm,
  pairForm,
  settingsForm,
  shareForm,
} from './forms'

interface Context {
  client: SupabaseClient
  user: SessionUser
  t: Translate
}

const ok = (t: Translate, key: MessageKey = 'common.saved'): ActionState => ({ status: 'ok', message: t(key) })
const fail = (t: Translate, key: MessageKey): ActionState => ({ status: 'error', message: t(key) })

/**
 * Shared frame of every dashboard action: a verified session, validated input, then the change through
 * the user's own client so row level security applies, and a refresh of the dashboard.
 */
async function run<S extends z.ZodType>(
  form: FormData,
  schema: S,
  task: (input: z.output<S>, context: Context) => Promise<ActionState>,
): Promise<ActionState> {
  const t = createTranslator()
  const user = await currentUser()
  const client = user && (await createSessionClient())
  if (!user || !client) return fail(t, 'common.readOnly')
  const parsed = schema.safeParse(formFields(form))
  if (!parsed.success) return fail(t, 'common.invalid')
  try {
    const state = await task(parsed.data, { client, user, t })
    revalidatePath('/app', 'layout')
    return state
  } catch {
    return fail(t, 'common.failed')
  }
}

export async function setAppCategory(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, categoryForm, async (input, { client, user, t }) => {
    unwrap(
      await client
        .from('apps')
        .update({ category: input.category })
        .eq('user_id', user.id)
        .eq('package_name', input.packageName),
    )
    return ok(t)
  })
}

export async function updateDevice(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, deviceForm, async (input, { client, user, t }) => {
    unwrap(
      await client
        .from('devices')
        .update({ name: input.name, member_id: input.memberId })
        .eq('user_id', user.id)
        .eq('id', input.id),
    )
    return ok(t)
  })
}

export async function revokeDevice(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, idForm, async (input, { client, user, t }) => {
    unwrap(
      await client
        .from('devices')
        .update({ revoked_at: new Date().toISOString() })
        .eq('user_id', user.id)
        .eq('id', input.id),
    )
    return ok(t, 'devices.revokedNotice')
  })
}

export async function deleteDeviceData(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, deleteDeviceDataForm, async (input, { client, user, t }) => {
    const device = unwrap(
      await client.from('devices').select('name').eq('user_id', user.id).eq('id', input.id).maybeSingle(),
    ) as { name: string } | null
    if (device?.name !== input.confirm) return fail(t, 'common.invalid')
    for (const table of ['usage_buckets', 'key_buckets', 'screen_buckets']) {
      unwrap(await client.from(table).delete().eq('user_id', user.id).eq('device_id', input.id))
    }
    return ok(t, 'devices.dataDeleted')
  })
}

export async function addMember(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, memberForm, async (input, { client, user, t }) => {
    unwrap(await client.from('household_members').insert({ user_id: user.id, name: input.name }))
    return ok(t)
  })
}

export async function deleteMember(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, idForm, async (input, { client, user, t }) => {
    unwrap(await client.from('household_members').delete().eq('user_id', user.id).eq('id', input.id))
    return ok(t, 'common.deleted')
  })
}

export async function addGoal(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, goalForm, async (input, { client, user, t }) => {
    unwrap(
      await client.from('goals').insert({
        user_id: user.id,
        scope: input.scope,
        target: input.target,
        metric: input.metric,
        period: input.period,
        limit_value: input.limit,
      }),
    )
    return ok(t)
  })
}

export async function deleteGoal(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, idForm, async (input, { client, user, t }) => {
    unwrap(await client.from('goals').delete().eq('user_id', user.id).eq('id', input.id))
    return ok(t, 'common.deleted')
  })
}

export async function saveSettings(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, settingsForm, async (input, { client, user, t }) => {
    unwrap(
      await client
        .from('settings')
        .update({
          timezone: input.timezone,
          locale: input.locale,
          retention_days: input.retentionDays,
          digest_enabled: input.digestEnabled,
        })
        .eq('user_id', user.id),
    )
    return ok(t)
  })
}

/** Sets the browser's zone on an account that has none yet; a zone the user chose is never replaced. */
export async function adoptTimeZone(timezone: string): Promise<void> {
  const user = await currentUser()
  const client = user && (await createSessionClient())
  if (!user || !client || !isTimeZone(timezone)) return
  unwrap(await client.from('settings').update({ timezone }).eq('user_id', user.id).is('timezone', null))
  revalidatePath('/app', 'layout')
}

/** The token is returned once inside the link and only its hash is stored. */
export async function createShareLink(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, shareForm, async (input, { client, user, t }) => {
    const token = generateToken()
    unwrap(
      await client.from('share_links').insert({
        user_id: user.id,
        token_hash: sha256(token),
        device_id: input.deviceId,
        expires_at: new Date(Date.now() + input.days * 86_400_000).toISOString(),
      }),
    )
    return { status: 'ok', message: t('settings.shareCreated'), value: `${siteUrl()}/s/${token}` }
  })
}

export async function revokeShareLink(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, idForm, async (input, { client, user, t }) => {
    unwrap(await client.from('share_links').delete().eq('user_id', user.id).eq('id', input.id))
    return ok(t, 'common.deleted')
  })
}

export async function pairDevice(_: ActionState, form: FormData): Promise<ActionState> {
  return run(form, pairForm, async (input, { user, t }) => {
    const admin = createAdminClient()
    if (admin === null) return fail(t, 'common.failed')
    const result = await confirmPairing(input.code, user.id, {
      store: supabasePairingStore(admin),
      limiter: supabaseRateLimiter(admin),
      now: () => new Date(),
    })
    if (result.status === 'paired') return { status: 'ok', message: t('pair.paired', { name: result.deviceName }) }
    if (result.status === 'rate_limited') return fail(t, 'pair.rateLimited')
    return fail(t, result.status === 'expired' ? 'pair.expired' : 'pair.invalid')
  })
}

/** Deletes the auth user; every table cascades from it, so no row of the household is left behind. */
export async function deleteAccount(_: ActionState, form: FormData): Promise<ActionState> {
  const state = await run(form, deleteAccountForm, async (_input, { client, user, t }) => {
    const admin = createAdminClient()
    if (admin === null) return fail(t, 'common.failed')
    const { error } = await admin.auth.admin.deleteUser(user.id)
    if (error) return fail(t, 'common.failed')
    await client.auth.signOut()
    return ok(t, 'common.deleted')
  })
  if (state.status === 'ok') redirect('/')
  return state
}
