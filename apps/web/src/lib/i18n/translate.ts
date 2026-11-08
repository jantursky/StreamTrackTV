import { en, type Messages } from './en'

/** Dotted paths to every string of the dictionary, e.g. `nav.overview`. */
type Leaves<T, Prefix extends string = ''> = {
  [K in keyof T & string]: T[K] extends string ? `${Prefix}${K}` : Leaves<T[K], `${Prefix}${K}.`>
}[keyof T & string]

export type MessageKey = Leaves<Messages>
export type Vars = Record<string, string | number>
export type Translate = (key: MessageKey, vars?: Vars) => string

const DICTIONARIES: Readonly<Record<string, Messages>> = { en }
export const LOCALES = Object.keys(DICTIONARIES)
export const DEFAULT_LOCALE = 'en'

function lookup(messages: Messages, key: string): string {
  let node: unknown = messages
  for (const part of key.split('.')) node = (node as Record<string, unknown>)[part]
  if (typeof node !== 'string') throw new Error(`Missing message: ${key}`)
  return node
}

/** `t('kpi.devices', { count: 2 })` fills `{count}` placeholders; unknown placeholders stay visible. */
export function createTranslator(locale: string = DEFAULT_LOCALE): Translate {
  const messages = DICTIONARIES[locale] ?? en
  return (key, vars) =>
    lookup(messages, key).replace(/\{(\w+)\}/g, (match, name: string) => (vars?.[name] ?? match).toString())
}
