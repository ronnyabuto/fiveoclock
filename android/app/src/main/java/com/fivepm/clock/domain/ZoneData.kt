package com.fivepm.clock.domain

import org.json.JSONObject

object ZoneData {
    fun parse(json: String): List<ZoneMeta> {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1) { "Unsupported zone data schema" }
        val zones = root.getJSONArray("zones")
        return List(zones.length()) { index ->
            val zone = zones.getJSONObject(index)
            ZoneMeta(zone.getString("name"), zone.getString("city"), zone.getString("countryName"), zone.getInt("rank"))
        }.also { result ->
            require(result.map { it.name }.distinct().size == result.size) { "Duplicate zone identifiers" }
            require(result.map { it.rank }.distinct().size == result.size) { "Duplicate selection ranks" }
        }
    }
}
