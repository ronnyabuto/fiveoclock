package com.fivepm.clock

import android.content.Context
import java.net.URI

class ClockPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
    val seconds: Boolean get() = preferences.getBoolean("seconds", true)
    val website: String get() = preferences.getString("website", DEFAULT_WEBSITE) ?: DEFAULT_WEBSITE

    fun save(seconds: Boolean, website: String) {
        require(validWebsite(website))
        preferences.edit().putBoolean("seconds", seconds).putString("website", website.trim()).apply()
    }

    companion object {
        const val DEFAULT_WEBSITE = "https://fiveoclock.vercel.app/"
        fun validWebsite(value: String): Boolean {
            if (value.isBlank()) return true
            return try {
                val uri = URI(value.trim())
                uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
            } catch (_: java.net.URISyntaxException) { false }
        }
    }
}
