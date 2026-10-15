package com.fivepm.clock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.fivepm.clock.widget.FivePmWidgetProvider
import com.fivepm.clock.widget.WidgetController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == OPEN_CLOCK && savedInstanceState == null) {
            WidgetController.update(this, "tap")
            if (openWebsite()) { finish(); return }
        }
        setContentView(R.layout.activity_main)
        val root = findViewById<android.view.View>(R.id.activity_root)
        val padding = (20 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        val preferences = ClockPreferences(this)
        val website = findViewById<EditText>(R.id.website)
        val seconds = findViewById<CheckBox>(R.id.show_seconds)
        if (savedInstanceState == null) {
            website.setText(preferences.website)
            seconds.isChecked = preferences.seconds
        }
        findViewById<Button>(R.id.save).setOnClickListener {
            val value = website.text.toString().trim()
            if (!ClockPreferences.validWebsite(value)) {
                website.error = getString(R.string.invalid_website)
            } else {
                preferences.save(seconds.isChecked, value)
                WidgetController.update(this, "settings")
                refreshDiagnostics()
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.pin_widget).setOnClickListener {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(ComponentName(this, FivePmWidgetProvider::class.java), null, null)
            } else Toast.makeText(this, R.string.manual_add, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.refresh_now).setOnClickListener {
            WidgetController.update(this, "settings_refresh")
            refreshDiagnostics()
        }
        findViewById<Button>(R.id.open_website).setOnClickListener { openWebsite() }
    }

    override fun onResume() {
        super.onResume()
        if (findViewById<android.view.View?>(R.id.activity_root) != null) {
            WidgetController.update(this, "activity_resume")
            refreshDiagnostics()
        }
    }

    private fun openWebsite(): Boolean {
        val website = ClockPreferences(this).website
        if (website.isBlank() || !ClockPreferences.validWebsite(website)) return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website)).addCategory(Intent.CATEGORY_BROWSABLE))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun refreshDiagnostics() {
        val state = WidgetController.diagnostics(this)
        val formatter = DateTimeFormatter.ofPattern("d MMM HH:mm:ss").withZone(ZoneId.systemDefault())
        fun display(epoch: Long) = if (epoch == 0L) getString(R.string.no_active_widget) else formatter.format(Instant.ofEpochMilli(epoch))
        findViewById<TextView>(R.id.diagnostics).text = getString(R.string.diagnostics_format,
            display(state.updated), display(state.nextEvent), state.latenessMs,
            getString(if (state.exact) R.string.precise else R.string.best_effort))
    }

    companion object { const val OPEN_CLOCK = "com.fivepm.clock.OPEN_CLOCK" }
}
