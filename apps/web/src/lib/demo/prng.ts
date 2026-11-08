/** Mulberry32: a tiny, fast, seedable PRNG. Not for secrets; only for reproducible demo data. */
export function mulberry32(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296
  }
}

export interface Random {
  next(): number
  /** Integer in [min, max], both inclusive. */
  int(min: number, max: number): number
  chance(probability: number): boolean
  /** Picks an item with probability proportional to its weight. */
  weighted<T>(items: readonly (readonly [T, number])[]): T
}

export function createRandom(seed: number): Random {
  const next = mulberry32(seed)
  return {
    next,
    int: (min, max) => min + Math.floor(next() * (max - min + 1)),
    chance: (probability) => next() < probability,
    weighted(items) {
      const total = items.reduce((acc, [, w]) => acc + w, 0)
      let roll = next() * total
      for (const [item, weight] of items) {
        roll -= weight
        if (roll < 0) return item
      }
      const last = items.at(-1)
      if (last === undefined) throw new Error('weighted() needs at least one item')
      return last[0]
    },
  }
}
