package com.apptiary.streamtracktv.core.wire

import kotlinx.serialization.json.Json

/**
 * JSON settings shared by the HTTP client, the privacy screen and the tests.
 *
 * Optional fields that are null are left out (zod's `.optional()` rejects `null`), unknown response
 * fields are ignored so the server can grow, and the poll response is discriminated by `status`.
 */
public val WireJson: Json =
    Json {
        explicitNulls = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "status"
    }

/** Same settings, indented for display on the TV. */
public val PrettyWireJson: Json =
    Json(WireJson) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
