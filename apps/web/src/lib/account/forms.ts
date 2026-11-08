import { z } from 'zod'
import { metricsForScope } from '../analytics/goals'
import { CATEGORIES, GOAL_METRICS, GOAL_PERIODS, GOAL_SCOPES, type GoalScope } from '../data/types'
import { isTimeZone } from '../time/days'
import { LOCALES } from '../i18n/translate'

/**
 * Schemas of every dashboard form. Server actions parse `FormData` with these before touching the
 * database, so the database only ever sees validated values.
 */

const id = z.uuid()
const name = z.string().trim().min(1).max(60)

export const categoryForm = z.object({
  packageName: z.string().min(1).max(255),
  category: z.enum(CATEGORIES),
})

export const deviceForm = z.object({
  id,
  name,
  memberId: z.union([id, z.literal('')]).transform((v) => (v === '' ? null : v)),
})

export const idForm = z.object({ id })

export const deleteDeviceDataForm = z.object({ id, confirm: z.string().trim() })

export const memberForm = z.object({ name })

const HOUR = 3_600_000

/** The goal target arrives as `scope:value`, e.g. `device:<uuid>` or `category:streaming`. */
export const goalForm = z
  .object({
    target: z.string().regex(new RegExp(`^(${GOAL_SCOPES.join('|')}):.{1,255}$`)),
    metric: z.enum(GOAL_METRICS),
    period: z.enum(GOAL_PERIODS),
    // Hours for time metrics (decimals allowed), a whole count for keypresses.
    limit: z.coerce.number().positive().max(100_000),
  })
  .transform(({ target, ...goal }) => {
    const split = target.indexOf(':')
    return { ...goal, scope: target.slice(0, split) as GoalScope, target: target.slice(split + 1) }
  })
  .refine((goal) => metricsForScope(goal.scope).includes(goal.metric))
  .transform((goal) => ({
    ...goal,
    limit: goal.metric === 'keypresses' ? Math.round(goal.limit) : Math.round(goal.limit * HOUR),
  }))

export const RETENTION_CHOICES = [30, 90, 180, 365, 730] as const

export const settingsForm = z.object({
  timezone: z.string().refine(isTimeZone),
  locale: z.string().refine((locale) => LOCALES.includes(locale)),
  retentionDays: z.coerce.number().int().min(7).max(3650),
  digestEnabled: z
    .literal('on')
    .optional()
    .transform((v) => v === 'on'),
})

export const SHARE_DAYS = [1, 7, 30] as const

export const shareForm = z.object({
  deviceId: z.union([id, z.literal('')]).transform((v) => (v === '' ? null : v)),
  days: z.coerce.number().refine((d) => (SHARE_DAYS as readonly number[]).includes(d)),
})

export const deleteAccountForm = z.object({ confirm: z.literal('DELETE') })

export const pairForm = z.object({ code: z.string().trim().min(1).max(20) })

/** Plain object of the string fields of a form, ready for a zod schema. */
export function formFields(form: FormData): Record<string, string> {
  const fields: Record<string, string> = {}
  for (const [key, value] of form.entries()) if (typeof value === 'string') fields[key] = value
  return fields
}
