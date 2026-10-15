package com.fivepm.clock

import android.content.Context
import android.appwidget.AppWidgetManager
import java.net.URI

enum class ClockStyle { DIGITAL, ANALOGUE }
data class ClockAppearance(val style: ClockStyle = ClockStyle.DIGITAL, val seconds: Boolean = false)

class ClockPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
    val seconds: Boolean get() = preferences.getBoolean("seconds", false)
    val style: ClockStyle get() = readStyle("style", ClockStyle.DIGITAL)
    val website: String get() = preferences.getString("website", DEFAULT_WEBSITE) ?: DEFAULT_WEBSITE

    fun save(seconds: Boolean, website: String, style: ClockStyle = this.style, applyToAllWidgets: Boolean = false) {
        require(validWebsite(website))
        val editor = preferences.edit()
        if (applyToAllWidgets) {
            preferences.all.keys.filter { it.startsWith("widget.") && (it.endsWith(".style") || it.endsWith(".seconds")) }
                .forEach { editor.remove(it) }
        }
        editor.putBoolean("seconds", seconds).putString("website", website.trim())
            .putString("style", style.name).apply()
    }

    fun appearance(widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): ClockAppearance = ClockAppearance(
        readStyle("widget.$widgetId.style", style), preferences.getBoolean("widget.$widgetId.seconds", seconds),
    )

    fun saveAppearance(widgetId: Int, appearance: ClockAppearance) {
        require(widgetId > 0)
        preferences.edit().putString("widget.$widgetId.style", appearance.style.name)
            .putBoolean("widget.$widgetId.seconds", appearance.seconds).apply()
    }

    fun removeWidget(widgetId: Int) {
        preferences.edit().remove("widget.$widgetId.style").remove("widget.$widgetId.seconds").apply()
    }

    private fun readStyle(key: String, fallback: ClockStyle): ClockStyle =
        ClockStyle.entries.firstOrNull { it.name == preferences.getString(key, fallback.name) } ?: fallback

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
