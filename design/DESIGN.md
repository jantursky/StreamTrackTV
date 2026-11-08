# StreamTrack TV design system

Warm-stone paper canvas, monochrome neutrals, one cyan accent reserved for primary actions. Used by the web dashboard, the docs site and (adapted for 10-foot viewing) the Android TV app.

## Color

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `--accent` | `#3ba6f1` | `#5bb6f5` | Primary action fill, active links, focus ring. The only chromatic filled element. |
| `--accent-edge` | `#3398e1` | `#7cc4f7` | Outlined action borders, highlight text |
| `--accent-wash` | `#c1e1f7` | `#12324a` | Pill behind the highlighted headline span |
| `--ink` | `#0c0a09` | `#fafaf9` | Headings, strong icons |
| `--soot` | `#1c1917` | `#e7e5e4` | Inverted surfaces, active tab pill |
| `--text` | `#78716c` | `#a8a29e` | Body and secondary copy |
| `--text-faint` | `#a8a29e` | `#78716c` | Helper text, disabled |
| `--border-strong` | `#d6d3d1` | `#44403c` | Input outlines, secondary borders |
| `--border` | `#e8e6e5` | `#292524` | Card, nav and hairline borders |
| `--canvas` | `#fafaf9` | `#0c0a09` | Page background |
| `--surface` | `#ffffff` | `#1c1917` | Cards and inputs only |

Chart series use the accent plus neutral stone steps; never add a second hue. Status colors (error, warning) appear only as small text or icon marks, not fills.

## Typography

- Display and headings: Inter Tight, weight 400 and 500 (npm `@fontsource-variable/inter-tight`). The original system uses a proprietary face; Inter Tight is the open substitute.
- Body and UI: Inter, weight 400/500/600 (npm `@fontsource-variable/inter`). Body is 14 px, line height 1.64.
- Scale: caption 10/12, small 13, body 14, body-lg 16, subheading 20, heading 32 (tracking -0.025em), display 52 (tracking -0.021em).
- Emphasis through size and the highlight span, not heavier weights.

## Layout

- 4 px base unit. Steps: 4, 8, 12, 16, 24, 32, 40, 48, 64, 80, 96.
- Max content width 1200 px, section gap 96 px, card padding 24 px, element gap 8 px.

## Shape and depth

- Radius: pill (9999) for buttons and tags, 16 feature card, 10 card, 6 input, 4 icon.
- 1 px hairlines are the main separator. Content cards have no shadow beyond `0 4px 16px rgb(0 0 0 / 5%)`.
- One floating preview per page may use `0 12px 45px rgb(12 10 9 / 12%)`.

## Components

- Primary button: pill, accent fill, 1 px accent-edge border, weight 500, padding 8 × 16.
- Ghost button: pill, transparent, 1 px border, ink text.
- Card: surface, 10 px radius, 1 px border, 24 px padding.
- Input: surface, 6 px radius, 1 px border-strong, 2 px accent focus ring.
- Tab pills: active = soot fill with canvas text; inactive = transparent with hairline.
- Highlight span: accent-edge text on accent-wash pill, once per headline.

## TV adaptation

- Same tokens, dark theme by default (living-room viewing). Base text 24 sp, min 18 sp; 48 dp safe-area padding.
- Every focusable element shows a 3 dp accent ring plus a slight scale (1.04) on focus; no focus state relies on color alone.
- Cards use the 16 px radius; pill buttons keep the pill shape.

## Do

- Keep `--canvas` as the page background and white (`--surface`) only for cards.
- One highlight span per headline.
- Cyan primary button is the only chromatic filled control.
- Check every screen in light and dark.

## Don't

- No extra accent colors, gradients, glassmorphism or decorative washes.
- No heavy shadows on cards.
- No dark or neutral fill on primary buttons.
- No Inter for display-size headlines.

## Logo

Mark: a squircle tile with a play triangle and two rising bars (streaming plus tracking). Colors come from the ecosystem, not from the design system: Android green and a deep teal from the Android palette.

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `--logo-tile` | `#3ddc84` | `#52e394` | Tile fill |
| `--logo-ink` | `#073042` | `#073042` | Bars |
| `--logo-cream` | `#fafaf9` | `#fafaf9` | Play triangle |

Files: `apps/docs/public/{logo,icon,icon-dark}.svg`, `apps/web/public/{favicon.svg,apple-touch-icon.png}`. The UI accent stays the cyan from the palette above.
