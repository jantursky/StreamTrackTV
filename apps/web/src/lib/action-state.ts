/** Result of a server action, rendered by `ActionForm`. */
export interface ActionState {
  status: 'idle' | 'ok' | 'error'
  message: string
  /** A value to show once, such as a freshly created share link. */
  value?: string
}

export const IDLE: ActionState = { status: 'idle', message: '' }

export type FormAction = (previous: ActionState, form: FormData) => Promise<ActionState>
