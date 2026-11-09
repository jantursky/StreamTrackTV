package com.jantursky.streamtracktv.core.wire

/** Wire format version, mirrors `SCHEMA_VERSION` in `packages/schema`. */
public const val SCHEMA_VERSION: Int = 1

/**
 * Upper bounds the ingest API enforces, mirrors `LIMITS` in `packages/schema` one to one: each
 * constant is the `LIMITS` key in upper snake case (`maxRowsPerKind` is [MAX_ROWS_PER_KIND]), one per
 * line, so a test on the schema side can read this file and compare the values.
 */
public object WireLimits {
    public const val MAX_ROWS_PER_KIND: Int = 2000
    public const val MAX_PACKAGE_LENGTH: Int = 255
    public const val MAX_LABEL_LENGTH: Int = 120
    public const val MS_PER_HOUR: Long = 3_600_000
    public const val PAIR_CODE_LENGTH: Int = 6
    public const val PAIR_CODE_TTL_SECONDS: Long = 600
    public const val MAX_KEY_COUNT: Long = 1_000_000
    public const val MAX_TIMEZONE_LENGTH: Int = 64
    public const val MAX_DEVICE_NAME_LENGTH: Int = 60
    public const val MAX_MODEL_LENGTH: Int = 80
    public const val MIN_TOKEN_LENGTH: Int = 32
    public const val PIN_MIN_LENGTH: Int = 4
    public const val PIN_MAX_LENGTH: Int = 8
    public const val PIN_RESET_TTL_SECONDS: Long = 600
    public const val MAX_EVENTS_PER_BATCH: Int = 100
    public const val MAX_EVENT_VALUE: Int = 100_000
    public const val MAX_STORED_EVENTS: Int = 500
    public const val MAX_EVENT_FUTURE_MS: Long = 86_400_000
}

/** Characters of a pairing code, mirrors `PAIR_CODE_ALPHABET` in `packages/schema`. */
public const val PAIR_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// Mirrors the package name regex of `packageName` in `packages/schema`.
private val packageNamePattern = Regex("^[A-Za-z]\\w*(\\.[A-Za-z]\\w*)+$")
private val pairCodePattern = Regex("^[$PAIR_CODE_ALPHABET]{${WireLimits.PAIR_CODE_LENGTH}}$")

/** True when the server would accept [name] as a package name. */
public fun isValidPackageName(name: String): Boolean =
    name.length <= WireLimits.MAX_PACKAGE_LENGTH && packageNamePattern.matches(name)

/** True for a pairing code without the ambiguous characters I, O, 0 and 1. */
public fun isValidPairCode(code: String): Boolean = pairCodePattern.matches(code)
