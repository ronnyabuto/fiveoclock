package com.fivepm.clock.domain

import java.time.Instant
import java.time.ZoneId
import java.time.zone.ZoneRules
import kotlin.math.abs

const val DAY_MS = 86_400_000L
const val TARGET_MS = 61_200_000L

data class ZoneMeta(val name: String, val city: String, val countryName: String, val rank: Int)
data class ZoneTime(val meta: ZoneMeta, val offsetSeconds: Int, val signedDistanceMs: Long) {
    val offsetLabel: String
        get() {
            if (offsetSeconds == 0) return "UTC"
            val minutes = abs(offsetSeconds) / 60
            return "UTC${if (offsetSeconds < 0) "-" else "+"}" +
                "%02d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)
        }
}
data class ClockState(val winner: ZoneTime, val nextEventEpochMs: Long)

class TimeDomain(zones: List<ZoneMeta>, rulesForZone: (String) -> ZoneRules = { ZoneId.of(it).rules }) {
    private val supported = zones.mapNotNull { meta ->
        try { meta to rulesForZone(meta.name) } catch (_: java.time.DateTimeException) { null }
    }

    fun evaluate(nowMs: Long): List<ZoneTime> {
        val instant = Instant.ofEpochMilli(nowMs)
        return supported.map { (meta, zone) ->
            val offset = zone.getOffset(instant).totalSeconds
            val localMs = Math.floorMod(nowMs + offset * 1000L, DAY_MS)
            ZoneTime(meta, offset, signedDistance(localMs))
        }
    }

    fun winner(nowMs: Long): ZoneTime? = select(evaluate(nowMs))

    fun state(nowMs: Long): ClockState? {
        val evaluated = evaluate(nowMs)
        val winner = select(evaluated) ?: return null
        val phases = evaluated.map { Math.floorMod(TARGET_MS - it.offsetSeconds * 1000L, DAY_MS) }
            .distinct().sorted()
        var nextEvent = Long.MAX_VALUE

        if (phases.size > 1) {
            for (index in phases.indices) {
                val left = phases[index]
                val right = if (index == phases.lastIndex) phases.first() + DAY_MS else phases[index + 1]
                val midpoint = Math.floorMod(left + (right - left) / 2, DAY_MS)
                val boundary = nowMs - Math.floorMod(nowMs, DAY_MS) + midpoint
                // The preferred city can win the exact tie but lose one millisecond later.
                for (candidate in listOf(boundary, boundary + 1, boundary + DAY_MS, boundary + DAY_MS + 1)) {
                    if (candidate > nowMs && candidate < nextEvent &&
                        selectAtFixedOffsets(candidate, evaluated)?.meta?.name != winner.meta.name) {
                        nextEvent = candidate
                    }
                }
            }
        }

        val instant = Instant.ofEpochMilli(nowMs)
        for ((_, zone) in supported) {
            val transition = zone.nextTransition(instant)?.instant?.toEpochMilli() ?: continue
            if (transition > nowMs) nextEvent = minOf(nextEvent, transition)
        }
        val target = nowMs - winner.signedDistanceMs
        // At exactly 17:00 the timer binds as count-up; no one-millisecond alarm is needed.
        val nextTarget = if (target > nowMs) target else target + DAY_MS
        nextEvent = minOf(nextEvent, nextTarget)
        val antipode = target + DAY_MS / 2
        val nextAntipode = if (antipode > nowMs) antipode else antipode + DAY_MS
        nextEvent = minOf(nextEvent, nextAntipode)
        return ClockState(winner, nextEvent)
    }

    private fun selectAtFixedOffsets(nowMs: Long, zones: List<ZoneTime>): ZoneTime? = select(zones.map {
        it.copy(signedDistanceMs = signedDistance(Math.floorMod(nowMs + it.offsetSeconds * 1000L, DAY_MS)))
    })

    private fun select(zones: List<ZoneTime>): ZoneTime? = zones.minWithOrNull(
        compareBy<ZoneTime> { abs(it.signedDistanceMs) }.thenBy { it.meta.rank },
    )

    companion object {
        fun signedDistance(localMs: Long): Long = Math.floorMod(localMs - TARGET_MS + DAY_MS / 2, DAY_MS) - DAY_MS / 2
    }
}
