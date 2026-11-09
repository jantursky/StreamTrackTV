package com.jantursky.streamtracktv.sync

import com.jantursky.streamtracktv.core.wire.ApiErrorDto

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    data class Failed(val failure: ApiFailure) : ApiResult<Nothing>
}

internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Ok -> ApiResult.Ok(transform(value))
        is ApiResult.Failed -> this
    }

/** Why a request did not succeed, in the terms the sync and pairing logic decide on. */
sealed interface ApiFailure {
    /** 401: the device token was revoked or never existed. */
    data object Unauthorized : ApiFailure

    /** 429, with the server's `Retry-After` in seconds when it sent one. */
    data class RateLimited(val retryAfterSeconds: Long?) : ApiFailure

    /** Any other 4xx. Retrying the same request will not help. */
    data class Rejected(val status: Int, val error: ApiErrorDto?) : ApiFailure

    data class ServerError(val status: Int) : ApiFailure

    /** No response at all: offline, DNS, TLS, timeout. */
    data class Network(val message: String) : ApiFailure

    /** 2xx with a body that does not match the schema. */
    data class BadResponse(val message: String) : ApiFailure
}

/** Short technical description for the sync status line. */
fun ApiFailure.describe(): String =
    when (this) {
        ApiFailure.Unauthorized -> "HTTP 401"
        is ApiFailure.RateLimited -> "HTTP 429"
        is ApiFailure.Rejected -> "HTTP $status" + (error?.let { " ${it.message}" } ?: "")
        is ApiFailure.ServerError -> "HTTP $status"
        is ApiFailure.Network -> message
        is ApiFailure.BadResponse -> message
    }
