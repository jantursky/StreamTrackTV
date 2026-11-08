import type { AppInfo, AppRecord, Category } from './types'

/** Defaults for common Android TV apps, so a fresh dashboard groups usage sensibly before any edits. */
const KNOWN_APPS: Readonly<Record<string, { label: string; category: Category }>> = {
  'com.netflix.ninja': { label: 'Netflix', category: 'streaming' },
  'com.disney.disneyplus': { label: 'Disney+', category: 'streaming' },
  'com.amazon.amazonvideo.livingroom': { label: 'Prime Video', category: 'streaming' },
  'com.wbd.stream': { label: 'Max', category: 'streaming' },
  'com.apple.atve.androidtv.appletv': { label: 'Apple TV', category: 'streaming' },
  'com.google.android.youtube.tv': { label: 'YouTube', category: 'video' },
  'com.google.android.youtube.tvkids': { label: 'YouTube Kids', category: 'kids' },
  'tv.twitch.android.app': { label: 'Twitch', category: 'video' },
  'com.plexapp.android': { label: 'Plex', category: 'video' },
  'org.xbmc.kodi': { label: 'Kodi', category: 'video' },
  'com.spotify.tv.android': { label: 'Spotify', category: 'music' },
  'com.aspiro.tidal': { label: 'Tidal', category: 'music' },
  'com.google.android.tvlauncher': { label: 'Home screen', category: 'system' },
  'com.google.android.apps.tv.launcherx': { label: 'Google TV home', category: 'system' },
  'com.android.tv.settings': { label: 'Settings', category: 'system' },
}

export function resolveApp(packageName: string, record?: AppRecord): AppInfo {
  const known = KNOWN_APPS[packageName]
  return {
    packageName,
    label: record?.label ?? known?.label ?? packageName,
    category: record?.category ?? known?.category ?? 'other',
  }
}

/** Lookup that resolves every package, also ones the user never saw in the apps table. */
export function appDirectory(records: readonly AppRecord[]): (packageName: string) => AppInfo {
  const byPackage = new Map(records.map((r) => [r.packageName, r]))
  const cache = new Map<string, AppInfo>()
  return (packageName) => {
    let info = cache.get(packageName)
    if (info === undefined) {
      info = resolveApp(packageName, byPackage.get(packageName))
      cache.set(packageName, info)
    }
    return info
  }
}
