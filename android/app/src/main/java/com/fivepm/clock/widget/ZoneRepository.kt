package com.fivepm.clock.widget

import android.content.Context
import android.util.Log
import com.fivepm.clock.domain.TimeDomain
import com.fivepm.clock.domain.ZoneData

object ZoneRepository {
    @Volatile private var cached: TimeDomain? = null

    fun domain(context: Context): TimeDomain = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    private fun load(context: Context): TimeDomain = try {
        val json = context.assets.open("zones.json").bufferedReader().use { it.readText() }
        TimeDomain(ZoneData.parse(json))
    } catch (error: Exception) {
        Log.e("FivePmClock", "Unable to read bundled time zones", error)
        TimeDomain(emptyList())
    }
}
