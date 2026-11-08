package com.apptiary.streamtracktv.core.payload

import com.apptiary.streamtracktv.core.model.BucketSet
import com.apptiary.streamtracktv.core.model.KeyBucket
import com.apptiary.streamtracktv.core.model.ScreenBucket
import com.apptiary.streamtracktv.core.model.UsageBucket
import com.apptiary.streamtracktv.core.wire.KeyBucketDto
import com.apptiary.streamtracktv.core.wire.ScreenBucketDto
import com.apptiary.streamtracktv.core.wire.SyncPayloadDto
import com.apptiary.streamtracktv.core.wire.UsageBucketDto
import com.apptiary.streamtracktv.core.wire.WireJson
import com.apptiary.streamtracktv.core.wire.WireLimits
import com.apptiary.streamtracktv.core.wire.isValidPackageName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * One upload: the payload and the stored rows it accounts for.
 *
 * [rows] keeps the stored values, not the capped ones in [payload], so marking them as sent can
 * compare against what is in the database.
 */
public data class OutgoingBatch(val payload: SyncPayloadDto, val rows: BucketSet)

/** Rows of all kinds together that one batch may carry at most. */
public const val MAX_ROWS_PER_BATCH: Int = 3 * WireLimits.MAX_ROWS_PER_KIND

/**
 * Upper bound for one encoded payload. The server refuses bodies over 1 MiB (`SYNC_MAX_BYTES` in
 * apps/web); this leaves room for headers and for a payload that grows by a few bytes on the way.
 */
public const val MAX_BATCH_BYTES: Int = 900 * 1024

/**
 * Builds the next `POST /api/v1/sync` body from unsent rows, oldest first.
 *
 * A batch carries at most [WireLimits.MAX_ROWS_PER_KIND] rows of each kind, at most [maxRows] rows in
 * all, and is halved until its JSON fits into [maxBytes]; the rest stay pending. Rows of [excluded]
 * apps and package names the server would reject are left out of the payload but still listed in
 * [OutgoingBatch.rows], so they are settled instead of retried forever.
 */
@Suppress("LongParameterList") // The batch depends on each of these; bundling them would only move the list.
public fun buildBatch(
    pending: BucketSet,
    labels: Map<String, String>,
    excluded: Set<String>,
    now: Instant,
    zone: ZoneId,
    maxRows: Int = MAX_ROWS_PER_BATCH,
    maxBytes: Int = MAX_BATCH_BYTES,
): OutgoingBatch {
    var limit = maxRows.coerceAtLeast(1)
    while (true) {
        val rows = pending.oldest(limit)
        val payload =
            SyncPayloadDto(
                sentAt = formatSentAt(now, zone),
                timezone = zone.id.take(WireLimits.MAX_TIMEZONE_LENGTH),
                usage =
                    rows.usage
                        .filter { it.packageName !in excluded && isValidPackageName(it.packageName) }
                        .map { it.toDto(labels[it.packageName]) },
                keys = rows.keys.map { it.toDto() },
                screen = rows.screen.map { it.toDto() },
            )
        if (rows.size <= 1 || encodedSize(payload) <= maxBytes) return OutgoingBatch(payload, rows)
        limit = rows.size / 2
    }
}

/** Size of [payload] on the wire, in bytes. */
public fun encodedSize(
    payload: SyncPayloadDto,
): Int = WireJson.encodeToString(SyncPayloadDto.serializer(), payload).encodeToByteArray().size

/** The first [limit] rows, usage before keys before screen, each kind capped at the schema's row limit. */
private fun BucketSet.oldest(limit: Int): BucketSet {
    val usage = usage.take(minOf(limit, WireLimits.MAX_ROWS_PER_KIND))
    val keys = keys.take(minOf(limit - usage.size, WireLimits.MAX_ROWS_PER_KIND))
    val screen = screen.take(minOf(limit - usage.size - keys.size, WireLimits.MAX_ROWS_PER_KIND))
    return BucketSet(usage, keys, screen)
}

/** ISO 8601 with offset and whole seconds, e.g. `2026-11-08T20:15:00+01:00`. */
public fun formatSentAt(now: Instant, zone: ZoneId): String =
    now
        .truncatedTo(ChronoUnit.SECONDS)
        .atZone(zone)
        .toOffsetDateTime()
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

/** Display label as the server accepts it: trimmed, capped, and absent when blank. */
public fun wireLabel(
    label: String?,
): String? = label?.trim()?.take(WireLimits.MAX_LABEL_LENGTH)?.takeIf { it.isNotEmpty() }

public fun UsageBucket.toDto(label: String?): UsageBucketDto =
    UsageBucketDto(
        day = slot.dayString,
        hour = slot.hour,
        packageName = packageName,
        label = wireLabel(label),
        foregroundMs = foregroundMs.coerceIn(0, WireLimits.MS_PER_HOUR),
    )

public fun KeyBucket.toDto(): KeyBucketDto =
    KeyBucketDto(
        day = slot.dayString,
        hour = slot.hour,
        keyClass = keyClass,
        count = count.coerceIn(0, WireLimits.MAX_KEY_COUNT),
    )

public fun ScreenBucket.toDto(): ScreenBucketDto =
    ScreenBucketDto(
        day = slot.dayString,
        hour = slot.hour,
        screenOnMs = screenOnMs.coerceIn(0, WireLimits.MS_PER_HOUR),
    )
