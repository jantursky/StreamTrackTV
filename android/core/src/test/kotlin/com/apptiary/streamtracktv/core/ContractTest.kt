package com.apptiary.streamtracktv.core

import com.apptiary.streamtracktv.core.model.KeyClass
import com.apptiary.streamtracktv.core.wire.AcceptedCountsDto
import com.apptiary.streamtracktv.core.wire.ApiErrorCode
import com.apptiary.streamtracktv.core.wire.ApiErrorDto
import com.apptiary.streamtracktv.core.wire.KeyBucketDto
import com.apptiary.streamtracktv.core.wire.PairPollRequestDto
import com.apptiary.streamtracktv.core.wire.PairPollResponseDto
import com.apptiary.streamtracktv.core.wire.PairStartRequestDto
import com.apptiary.streamtracktv.core.wire.PairStartResponseDto
import com.apptiary.streamtracktv.core.wire.SCHEMA_VERSION
import com.apptiary.streamtracktv.core.wire.ScreenBucketDto
import com.apptiary.streamtracktv.core.wire.SyncPayloadDto
import com.apptiary.streamtracktv.core.wire.SyncResponseDto
import com.apptiary.streamtracktv.core.wire.UsageBucketDto
import com.apptiary.streamtracktv.core.wire.WireJson
import com.apptiary.streamtracktv.core.wire.WireLimits
import com.apptiary.streamtracktv.core.wire.isValidPackageName
import com.apptiary.streamtracktv.core.wire.isValidPairCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The DTOs against JSON samples shaped exactly like the zod schemas in `packages/schema`. */
class ContractTest {
    private fun sample(name: String): String =
        requireNotNull(javaClass.getResource("/contract/$name")) { "Missing sample $name" }.readText()

    /** Decodes the sample, re-encodes it and checks nothing was lost, added or renamed. */
    private inline fun <reified T> roundTrip(name: String): T {
        val text = sample(name)
        val serializer: KSerializer<T> = serializer()
        val value = WireJson.decodeFromString(serializer, text)
        val reEncoded = WireJson.encodeToJsonElement(serializer, value)
        assertEquals(Json.parseToJsonElement(text), reEncoded, "Round trip of $name changed the JSON")
        return value
    }

    @Test
    fun `sync payload matches the schema sample`() {
        val payload = roundTrip<SyncPayloadDto>("sync-payload.json")
        assertEquals(
            SyncPayloadDto(
                schemaVersion = 1,
                sentAt = "2026-11-08T20:15:00+01:00",
                timezone = "Europe/Bratislava",
                usage =
                    listOf(
                        UsageBucketDto("2026-11-08", 19, "com.netflix.ninja", "Netflix", 1_800_000),
                        UsageBucketDto("2026-11-08", 19, "com.google.android.youtube.tv", null, 600_000),
                    ),
                keys = listOf(KeyBucketDto("2026-11-08", 19, KeyClass.DPAD, 42)),
                screen = listOf(ScreenBucketDto("2026-11-08", 19, 3_000_000)),
            ),
            payload,
        )
    }

    @Test
    fun `a missing label is omitted rather than sent as null`() {
        val json =
            WireJson.encodeToString(
                UsageBucketDto.serializer(),
                UsageBucketDto("2026-11-08", 1, "com.a.b", null, 5),
            )
        assertEquals("""{"day":"2026-11-08","hour":1,"packageName":"com.a.b","foregroundMs":5}""", json)
    }

    @Test
    fun `the schema version is always written`() {
        val element: JsonElement =
            WireJson.encodeToJsonElement(
                SyncPayloadDto.serializer(),
                SyncPayloadDto(
                    sentAt = "x",
                    timezone = "UTC",
                    usage = emptyList(),
                    keys = emptyList(),
                    screen = emptyList(),
                ),
            )
        assertEquals(
            """{"schemaVersion":1,"sentAt":"x","timezone":"UTC","usage":[],"keys":[],"screen":[]}""",
            element.toString(),
        )
        assertEquals(1, SCHEMA_VERSION)
    }

    @Test
    fun `responses and pairing messages match the schema samples`() {
        assertEquals(
            SyncResponseDto(AcceptedCountsDto(2, 1, 1), "2026-11-08T19:15:01.123Z"),
            roundTrip<SyncResponseDto>("sync-response.json"),
        )
        assertEquals(
            PairStartRequestDto("Living room TV", "Chromecast HD"),
            roundTrip<PairStartRequestDto>("pair-start-request.json"),
        )
        assertEquals("K7M2QX", roundTrip<PairStartResponseDto>("pair-start-response.json").code)
        assertEquals(32, roundTrip<PairPollRequestDto>("pair-poll-request.json").pollToken.length)
        assertEquals(ApiErrorDto(ApiErrorCode.RATE_LIMITED, "Slow down"), roundTrip<ApiErrorDto>("api-error.json"))
    }

    @Test
    fun `poll responses are discriminated by status`() {
        assertEquals(PairPollResponseDto.Pending, roundTrip<PairPollResponseDto>("pair-poll-pending.json"))
        assertEquals(PairPollResponseDto.Expired, roundTrip<PairPollResponseDto>("pair-poll-expired.json"))
        val claimed = roundTrip<PairPollResponseDto>("pair-poll-claimed.json")
        assertEquals(64, (claimed as PairPollResponseDto.Claimed).deviceToken.length)
        assertFailsWith<SerializationException> {
            WireJson.decodeFromString(PairPollResponseDto.serializer(), """{"status":"claimed"}""")
        }
    }

    @Test
    fun `raw key codes are not a key class`() {
        assertFailsWith<SerializationException> {
            WireJson.decodeFromString(
                KeyBucketDto.serializer(),
                """{"day":"2026-11-08","hour":1,"keyClass":"KEYCODE_DPAD_UP","count":1}""",
            )
        }
    }

    @Test
    fun `validators follow the schema patterns`() {
        assertTrue(isValidPackageName("com.netflix.ninja"))
        assertTrue(isValidPackageName("tv.twitch.android.app"))
        assertTrue(isValidPackageName("com.a_b.c1"))
        assertFalse(isValidPackageName("android"))
        assertFalse(isValidPackageName("not a package"))
        assertFalse(isValidPackageName("com.1bad"))
        assertFalse(isValidPackageName("com." + "a".repeat(WireLimits.MAX_PACKAGE_LENGTH)))
        assertTrue(isValidPairCode("K7M2QX"))
        listOf(
            "K7M2Q0",
            "K7M2QO",
            "K7M2QI",
            "K7M2Q1",
            "k7m2qx",
            "K7M2Q",
        ).forEach { assertFalse(isValidPairCode(it), it) }
    }

    @Test
    fun `limits mirror the schema package`() {
        assertEquals(2000, WireLimits.MAX_ROWS_PER_KIND)
        assertEquals(255, WireLimits.MAX_PACKAGE_LENGTH)
        assertEquals(120, WireLimits.MAX_LABEL_LENGTH)
        assertEquals(3_600_000, WireLimits.MS_PER_HOUR)
        assertEquals(6, WireLimits.PAIR_CODE_LENGTH)
        assertEquals(600, WireLimits.PAIR_CODE_TTL_SECONDS)
    }
}
