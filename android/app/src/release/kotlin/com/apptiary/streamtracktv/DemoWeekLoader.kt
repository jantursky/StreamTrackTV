package com.apptiary.streamtracktv

import com.apptiary.streamtracktv.data.UsageRepository

/** Release builds never contain demo data. */
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
fun demoWeekLoader(usage: UsageRepository): (suspend () -> Unit)? = null
