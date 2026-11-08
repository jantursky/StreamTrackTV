'use client'

import { useActionState, type ReactNode } from 'react'
import { useFormStatus } from 'react-dom'
import { IDLE, type FormAction } from '@/lib/action-state'

interface ActionFormProps {
  action: FormAction
  children: ReactNode
  className?: string
  /** Label of the read-only field that shows a returned value, e.g. a new share link. */
  valueLabel?: string
}

/** A form bound to a server action, with an announced status line. Works without JavaScript too. */
export function ActionForm({ action, children, className, valueLabel }: ActionFormProps) {
  const [state, formAction] = useActionState(action, IDLE)
  return (
    <form action={formAction} className={className}>
      {children}
      {state.value !== undefined && valueLabel !== undefined && (
        <label className="mt-3 block w-full">
          <span className="label">{valueLabel}</span>
          <input
            className="input font-mono text-small"
            readOnly
            value={state.value}
            onFocus={(e) => {
              e.target.select()
            }}
          />
        </label>
      )}
      <p
        role="status"
        aria-live="polite"
        className={`text-small empty:hidden ${state.status === 'error' ? 'text-error' : 'text-muted'} w-full`}
      >
        {state.message}
      </p>
    </form>
  )
}

export function SubmitButton({
  children,
  variant = 'primary',
}: {
  children: ReactNode
  variant?: 'primary' | 'ghost'
}) {
  const { pending } = useFormStatus()
  return (
    <button type="submit" disabled={pending} className={variant === 'primary' ? 'btn-primary' : 'btn-ghost'}>
      {children}
    </button>
  )
}
