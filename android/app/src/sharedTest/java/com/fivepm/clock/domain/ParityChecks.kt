package com.fivepm.clock.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object ParityChecks {
    fun verify(zoneJson: String, fixtureJson: String) {
        val zones = ZoneData.parse(zoneJson)
        val runtime = TimeDomain(zones)
        val fixtures = JSONObject(fixtureJson)
        val offsetSets = fixtures.getJSONArray("offsetSets")
        val frozen = List(offsetSets.length()) { index ->
            val offsets = offsetSets.getJSONObject(index)
            TimeDomain(zones) { name -> java.time.ZoneOffset.ofTotalSeconds(offsets.getInt(name)).rules }
        }
        val drift = sortedSetOf<String>()
        var compatible = 0
        val cases = fixtures.getJSONArray("cases")
        assertTrue(cases.length() >= 1000)
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val now = case.getLong("epochMs")
            val domain = frozen[case.getInt("offsetSetId")]
            val expectedOffsets = offsetSets.getJSONObject(case.getInt("offsetSetId"))
            val actualOffsets = runtime.evaluate(now)
            val mismatched = actualOffsets.filter { it.offsetSeconds != expectedOffsets.getInt(it.meta.name) }
            assertEquals("Runtime must support every bundled zone", zones.size, actualOffsets.size)
            if (mismatched.isEmpty()) {
                compatible++
                assertEquals("Live runtime parity at $now", case.getString("name"), runtime.winner(now)!!.meta.name)
            } else {
                drift.addAll(mismatched.map { it.meta.name })
            }
            val winner = requireNotNull(domain.winner(now))
            val label = "case $index at $now"
            assertEquals(label, case.getString("name"), winner.meta.name)
            assertEquals(label, case.getString("city"), winner.meta.city)
            assertEquals(label, case.getString("countryName"), winner.meta.countryName)
            assertEquals(label, case.getInt("offsetSeconds"), winner.offsetSeconds)
            assertEquals(label, case.getLong("signedDistanceMs"), winner.signedDistanceMs)
            val distance = kotlin.math.abs(winner.signedDistanceMs)
            val actualTies = domain.evaluate(now).filter { kotlin.math.abs(it.signedDistanceMs) == distance }
                .map { it.meta.name }.sorted()
            val tiedNames = case.getJSONArray("tiedNames")
            assertEquals(label, List(tiedNames.length()) { tiedNames.getString(it) }, actualTies)
        }
        assertTrue("At least one fixture must have matching runtime rules", compatible > 0)
        println("Selection parity: ${cases.length()}/${cases.length()}; live timezone-compatible inputs: $compatible/${cases.length()}; tzdata drift: $drift")
    }
}
