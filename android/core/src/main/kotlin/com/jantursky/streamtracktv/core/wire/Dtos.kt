package com.jantursky.streamtracktv.core.wire

import com.jantursky.streamtracktv.core.model.KeyClass
import com.jantursky.streamtracktv.core.model.SecurityEvent
import com.jantursky.streamtracktv.core.model.SecurityEventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors of the zod schemas in packages/schema/src/index.ts. Field names are the wire names.

@Serializable
public data class UsageBucketDto(
    val day: String,
    val hour: Int,
    val packageName: String,
    val label: String? = null,
    val foregroundMs: Long,
)

@Serializable
public data class KeyBucketDto(val day: String, val hour: Int, val keyClass: KeyClass, val count: Long)

@Serializable
public data class ScreenBucketDto(val day: String, val hour: Int, val screenOnMs: Long)

/** Body of `POST /api/v1/sync`. Rows carry absolute values, so re-sending is idempotent. */
@Serializable
public data class SyncPayloadDto(
    val schemaVersion: Int = SCHEMA_VERSION,
    val sentAt: String,
    val timezone: String,
    val usage: List<UsageBucketDto>,
    val keys: List<KeyBucketDto>,
    val screen: List<ScreenBucketDto>,
)

@Serializable
public data class AcceptedCountsDto(val usage: Int, val keys: Int, val screen: Int)

@Serializable
public data class SyncResponseDto(val accepted: AcceptedCountsDto, val serverTime: String)

@Serializable
public data class PairStartRequestDto(val deviceName: String, val model: String? = null)

@Serializable
public data class PairStartResponseDto(val code: String, val pollToken: String, val expiresAt: String)

@Serializable
public data class PairPollRequestDto(val pollToken: String)

/** Discriminated by the `status` field, see [WireJson]. */
@Serializable
public sealed interface PairPollResponseDto {
    @Serializable
    @SerialName("pending")
    public data object Pending : PairPollResponseDto

    @Serializable
    @SerialName("expired")
    public data object Expired : PairPollResponseDto

    @Serializable
    @SerialName("claimed")
    public data class Claimed(val deviceToken: String) : PairPollResponseDto
}

/** Body of `POST /api/v1/pin-reset/start`. It carries nothing: the device is known from its token. */
@Serializable
public class PinResetStartRequestDto

@Serializable
public data class PinResetStartResponseDto(val code: String, val pollToken: String, val expiresAt: String)

@Serializable
public data class PinResetPollRequestDto(val pollToken: String)

/** Discriminated by the `status` field, see [WireJson]. */
@Serializable
public sealed interface PinResetPollResponseDto {
    @Serializable
    @SerialName("pending")
    public data object Pending : PinResetPollResponseDto

    @Serializable
    @SerialName("expired")
    public data object Expired : PinResetPollResponseDto

    @Serializable
    @SerialName("confirmed")
    public data object Confirmed : PinResetPollResponseDto
}

/** One security event. [at] is an ISO instant on the TV's clock; [eventId] lets the server skip a resent event. */
@Serializable
public data class SecurityEventDto(
    val eventId: String,
    val type: SecurityEventType,
    val at: String,
    val value: Int? = null,
)

/** The wire form of a recorded event; the time is an ISO instant. */
public fun SecurityEvent.toDto(): SecurityEventDto =
    SecurityEventDto(
        eventId,
        type,
        java.time.Instant
            .ofEpochMilli(atMs)
            .toString(),
        value,
    )

/** Body of `POST /api/v1/events`. */
@Serializable
public data class EventsRequestDto(val schemaVersion: Int = SCHEMA_VERSION, val events: List<SecurityEventDto>)

@Serializable
public data class EventsResponseDto(val accepted: Int)

@Serializable
public enum class ApiErrorCode {
    @SerialName("invalid_payload")
    INVALID_PAYLOAD,

    @SerialName("unauthorized")
    UNAUTHORIZED,

    @SerialName("rate_limited")
    RATE_LIMITED,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("server_error")
    SERVER_ERROR,
}

/** Shape of every non-2xx response of the API. */
@Serializable
public data class ApiErrorDto(val error: ApiErrorCode, val message: String)
