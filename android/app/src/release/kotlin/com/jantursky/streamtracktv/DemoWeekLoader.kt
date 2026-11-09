package com.jantursky.streamtracktv

import com.jantursky.streamtracktv.data.UsageRepository

/** Release builds never contain demo data. */
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
fun demoWeekLoader(usage: UsageRepository): (suspend () -> Unit)? = null
