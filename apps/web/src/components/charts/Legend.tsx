import { TONE_FILL, type Tone } from './scale'

export function Legend({ items }: { items: readonly { name: string; tone: Tone }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-small" aria-hidden="true">
      {items.map((item) => (
        <li key={item.name} className="flex items-center gap-1.5">
          <svg viewBox="0 0 10 10" className="size-2.5">
            <rect width={10} height={10} rx={2} className={TONE_FILL[item.tone]} />
          </svg>
          {item.name}
        </li>
      ))}
    </ul>
  )
}
