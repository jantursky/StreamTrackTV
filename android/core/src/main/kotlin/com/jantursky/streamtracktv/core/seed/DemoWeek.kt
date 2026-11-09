package com.jantursky.streamtracktv.core.seed

import com.jantursky.streamtracktv.core.model.BucketSet
import com.jantursky.streamtracktv.core.model.HourSlot
import com.jantursky.streamtracktv.core.model.KeyBucket
import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.ScreenBucket
import com.jantursky.streamtracktv.core.model.UsageBucket
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** A believable week of TV use, for emulator previews and screenshots. Never used in release builds. */
public data class DemoWeek(val buckets: BucketSet, val labels: Map<String, String>)

private const val MINUTE_MS = 60_000L
private const val DAYS = 7
private const val LEFT_ON_FROM_HOUR = 20
private const val BINGE_DAY_OFFSET = 2
private val BINGE_HOURS = 19..22
private const val LAUNCHER = "com.google.android.tvlauncher"
private const val NETFLIX = "com.netflix.ninja"

private val streamingApps =
    linkedMapOf(
        NETFLIX to "Netflix",
        "com.google.android.youtube.tv" to "YouTube",
        "com.disney.disneyplus" to "Disney+",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.plexapp.android" to "Plex",
        "com.spotify.tv.android" to "Spotify",
        "tv.twitch.android.app" to "Twitch",
    )

/**
 * Generates the 7 days ending with [today], up to and excluding [currentHour] of today.
 *
 * Weekday evenings and longer weekend afternoons are active. Two days before [today] has a 4-hour
 * Netflix binge and the evening before [today] has the TV left on at the home screen, so every
 * dashboard insight shows.
 * The same [seed] always gives the same week.
 */
public fun demoWeek(today: LocalDate, currentHour: Int, seed: Long = 7L): DemoWeek {
    val random = Random(seed)
    val usage = mutableListOf<UsageBucket>()
    val keys = mutableListOf<KeyBucket>()
    val screen = mutableListOf<ScreenBucket>()

    for (offset in DAYS - 1 downTo 0) {
        val day = today.minusDays(offset.toLong())
        val lastHour = if (offset == 0) currentHour - 1 else HourSlot.LAST_HOUR
        for (hour in activeHours(day, random).filter { it <= lastHour }) {
            val slot = HourSlot(day, hour)
            val leftOn = offset == 1 && hour >= LEFT_ON_FROM_HOUR
            val plan =
                when {
                    leftOn -> {
                        listOf(LAUNCHER to 60 * MINUTE_MS)
                    }

                    offset == BINGE_DAY_OFFSET && hour in BINGE_HOURS -> {
                        listOf(NETFLIX to (52 + random.nextInt(8)) * MINUTE_MS)
                    }

                    else -> {
                        planHour(random)
                    }
                }
            screen += ScreenBucket(slot, plan.sumOf { it.second })
            plan.forEach { (pkg, ms) -> usage += UsageBucket(slot, pkg, ms) }
            if (!leftOn) keys += keyPresses(slot, random)
        }
    }
    val labels = streamingApps + (LAUNCHER to "Home")
    return DemoWeek(BucketSet(usage, keys, screen), labels)
}

private fun activeHours(day: LocalDate, random: Random): List<Int> {
    val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
    val start = if (weekend) 14 + random.nextInt(3) else 18 + random.nextInt(2)
    val end = 22 + random.nextInt(2)
    val morning = if (weekend && random.nextBoolean()) listOf(9, 10) else emptyList()
    return morning + (start..end)
}

/** Apps in the foreground during one hour, as (package, milliseconds) pairs. */
private fun planHour(random: Random): List<Pair<String, Long>> {
    val total = (25 + random.nextInt(35)) * MINUTE_MS
    val apps = streamingApps.keys.toList()
    val main = apps[random.nextInt(apps.size)]
    if (random.nextInt(3) > 0) return listOf(main to total)
    val second = apps.filter { it != main }[random.nextInt(apps.size - 1)]
    val split = total * (40 + random.nextInt(40)) / 100
    return listOf(main to split, second to total - split)
}

private fun keyPresses(slot: HourSlot, random: Random): List<KeyBucket> =
    listOf(
        KeyBucket(slot, KeyClass.DPAD, 20L + random.nextInt(140)),
        KeyBucket(slot, KeyClass.SELECT, 3L + random.nextInt(25)),
        KeyBucket(slot, KeyClass.BACK, 1L + random.nextInt(12)),
        KeyBucket(slot, KeyClass.MEDIA, random.nextInt(10).toLong()),
        KeyBucket(slot, KeyClass.VOLUME, random.nextInt(8).toLong()),
        KeyBucket(slot, KeyClass.HOME, random.nextInt(3).toLong()),
        KeyBucket(slot, KeyClass.OTHER, random.nextInt(3).toLong()),
    ).filter { it.count > 0 }
