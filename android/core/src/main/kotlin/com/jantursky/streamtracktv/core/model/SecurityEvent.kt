package com.jantursky.streamtracktv.core.model

/**
 * One entry of the TV's security log. [atMs] is the TV's clock; [eventId] is made on the TV so a batch the
 * server already stored, but whose answer was lost, is not stored twice. [value] is the wrong tries so far
 * for a wrong PIN and the wait in seconds for a started wait; nothing typed ever ends up here.
 */
public data class SecurityEvent(
    val id: Long,
    val eventId: String,
    val type: SecurityEventType,
    val atMs: Long,
    val value: Int? = null,
)
