import type { SupabaseClient } from '@supabase/supabase-js'
import { z } from 'zod'
import { selectBuckets } from '../analytics/aggregate'
import { appDirectory } from '../data/catalog'
import { createSupabaseSource } from '../data/supabase-source'
import type { MailConfig } from '../env'
import { createTranslator } from '../i18n/translate'
import { parseRows, unwrap } from '../supabase/query'
import { buildDigest, digestWeeks } from './digest'
import { sendMail } from './mail'

/** A user whose digest went out less than six days ago is skipped, so a retried cron never doubles up. */
const RESEND_AFTER_MS = 6 * 86_400_000

export interface DigestRun {
  sent: number
  skipped: number
  failed: number
}

/** Sends the weekly summary to every user who turned it on. Runs with the service role. */
export async function runDigest(admin: SupabaseClient, mail: MailConfig, now: Date): Promise<DigestRun> {
  const users = parseRows(
    z.object({ user_id: z.string(), digest_sent_at: z.string().nullable() }),
    await admin.from('settings').select('user_id, digest_sent_at').eq('digest_enabled', true),
  )
  const result: DigestRun = { sent: 0, skipped: 0, failed: 0 }

  for (const { user_id: userId, digest_sent_at: sentAt } of users) {
    if (sentAt !== null && now.getTime() - Date.parse(sentAt) < RESEND_AFTER_MS) {
      result.skipped++
      continue
    }
    try {
      const { data, error } = await admin.auth.admin.getUserById(userId)
      const email = data.user?.email
      if (error || !email) {
        result.skipped++
        continue
      }
      const source = createSupabaseSource(admin, userId, () => now)
      const [today, settings, apps] = await Promise.all([source.today(), source.settings(), source.apps(null)])
      const { current, previous } = digestWeeks(today)
      const buckets = await source.buckets({ from: previous.from, to: current.to }, null)
      const digest = buildDigest(
        current,
        selectBuckets(buckets, current),
        selectBuckets(buckets, previous),
        appDirectory(apps),
        createTranslator(settings.locale),
        settings.locale,
      )
      await sendMail(mail, email, digest)
      unwrap(await admin.from('settings').update({ digest_sent_at: now.toISOString() }).eq('user_id', userId))
      result.sent++
    } catch (error) {
      console.error(`digest for ${userId} failed`, error)
      result.failed++
    }
  }
  return result
}
