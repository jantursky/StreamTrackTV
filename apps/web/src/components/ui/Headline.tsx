import type { ReactNode } from 'react'

interface HeadlineProps {
  /** Message containing one `{highlight}` placeholder. */
  text: string
  highlight: ReactNode
  as?: 'h1' | 'h2'
  size?: 'heading' | 'display'
  id?: string
}

/** Headline with exactly one highlighted span, as the design system asks. */
export function Headline({ text, highlight, as: Tag = 'h1', size = 'heading', id }: HeadlineProps) {
  const [before = '', after = ''] = text.split('{highlight}')
  const sizing = size === 'display' ? 'text-heading sm:text-display' : 'text-heading'
  return (
    <Tag id={id} className={`font-display font-normal text-balance text-ink ${sizing}`}>
      {before}
      <span className="highlight">{highlight}</span>
      {after}
    </Tag>
  )
}
