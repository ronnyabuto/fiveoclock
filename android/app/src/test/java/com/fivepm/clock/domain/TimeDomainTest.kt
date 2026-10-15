package com.fivepm.clock.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TimeDomainTest {
    private fun epoch(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun zone(name: String, rank: Int = 0) = ZoneMeta(name, name, "", rank)

    @Test fun matchesTypeScriptFixtures() {
        fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
        ParityChecks.verify(resource("zones.json"), resource("parity-fixtures.json"))
    }

    @Test fun handlesUnsupportedAndEmptyZones() {
        assertNull(TimeDomain(emptyList()).state(0))
        assertNull(TimeDomain(listOf(zone("Not/AZone"))).winner(0))
        assertEquals("UTC", TimeDomain(listOf(zone("Not/AZone"), zone("UTC", 1))).winner(0)!!.meta.name)
    }

    @Test fun findsHalfMinuteBoundaryAndPreservesTiePreference() {
        val domain = TimeDomain(listOf(zone("Asia/Kolkata"), zone("Asia/Kathmandu", 1)))
        val midpoint = epoch("2026-10-15T11:22:30Z")
        assertEquals("Asia/Kathmandu", domain.winner(midpoint - 1)!!.meta.name)
        assertEquals("Asia/Kolkata", domain.winner(midpoint)!!.meta.name)
        assertEquals(midpoint, domain.state(midpoint - 1)!!.nextEventEpochMs)
        val reverse = TimeDomain(listOf(zone("Asia/Kathmandu"), zone("Asia/Kolkata", 1)))
        assertEquals("Asia/Kathmandu", reverse.winner(midpoint)!!.meta.name)
        assertEquals(midpoint + 1, reverse.state(midpoint)!!.nextEventEpochMs)
        assertEquals("Asia/Kolkata", reverse.winner(midpoint + 1)!!.meta.name)
    }

    @Test fun doesNotScheduleCountdownOnlyUpdates() {
        val domain = TimeDomain(listOf(zone("UTC")))
        val target = epoch("2026-01-15T17:00:00Z")
        for (now in listOf(target - 1, target, target + 1)) {
            assertNull(domain.state(now)!!.nextEventEpochMs)
        }
        assertEquals(0L, domain.state(target)!!.winner.signedDistanceMs)
    }

    @Test fun handlesMidnightAndEquivalentOffsets() {
        val domain = TimeDomain(listOf(zone("Pacific/Kiritimati"), zone("Pacific/Honolulu", 1)))
        val now = epoch("2026-01-15T23:59:59.999Z")
        assertEquals("Pacific/Kiritimati", domain.winner(now)!!.meta.name)
        assertEquals(domain.winner(now)!!.signedDistanceMs, domain.winner(now + 1)!!.signedDistanceMs - 1)
        assertNull(domain.state(now)!!.nextEventEpochMs)
    }

    @Test fun oneZoneDoesNotScheduleInvisibleCircularDistanceChanges() {
        val domain = TimeDomain(listOf(zone("UTC")))
        val antipode = epoch("2026-01-15T05:00:00Z")
        assertNull(domain.state(antipode - 1)!!.nextEventEpochMs)
        assertEquals(-DAY_MS / 2, domain.winner(antipode)!!.signedDistanceMs)
    }

    @Test fun competingZoneDstInvalidatesSchedule() {
        val domain = TimeDomain(listOf(zone("UTC"), zone("America/New_York", 1)))
        val transition = epoch("2026-03-08T07:00:00Z")
        assertEquals(transition, domain.state(transition - 1)!!.nextEventEpochMs)
        assertTrue(domain.state(transition)!!.nextEventEpochMs!! > transition)
    }

    @Test fun plannerNeverSkipsAVisibleStateChangeAcrossTheYear() {
        val zones = listOf(zone("UTC"), zone("Asia/Kathmandu", 1), zone("Australia/Eucla", 2),
            zone("Pacific/Chatham", 3), zone("America/New_York", 4), zone("Europe/London", 5))
        val domain = TimeDomain(zones)
        val start = epoch("2026-01-01T00:00:00Z")
        for (day in 0 until 366 step 7) {
            var now = start + day * DAY_MS
            val end = now + DAY_MS
            while (now < end) {
                val state = domain.state(now)!!
                val next = requireNotNull(state.nextEventEpochMs)
                assertTrue("Planner must progress at $now", next > now)
                for (sample in listOf(now + (next - now) / 2, next - 1)) {
                    val actual = domain.winner(sample)!!
                    assertEquals("Skipped winner at $sample", state.winner.meta.name, actual.meta.name)
                }
                now = next
            }
        }
    }
}
