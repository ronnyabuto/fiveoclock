package com.fivepm.clock.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.fivepm.clock.ClockPreferences
import com.fivepm.clock.MainActivity
import com.fivepm.clock.R
import com.fivepm.clock.domain.ClockState

object WidgetRenderer {
    fun render(context: Context, state: ClockState?, nowMs: Long): RemoteViews = RemoteViews(mapOf(
        SizeF(150f, 160f) to layout(context, R.layout.widget_compact, state, nowMs),
        SizeF(320f, 160f) to layout(context, R.layout.widget_wide, state, nowMs),
    ))

    internal fun layout(context: Context, layout: Int, state: ClockState?, nowMs: Long): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val preferences = ClockPreferences(context)
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_CLOCK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        views.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(context, 1,
            Intent(context, FivePmWidgetProvider::class.java).setAction(FivePmWidgetProvider.REFRESH), PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.settings, PendingIntent.getActivity(context, 2,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))

        val winner = state?.winner
        if (winner == null) {
            for (id in listOf(R.id.digital_clock, R.id.analogue_clock, R.id.distance)) views.setViewVisibility(id, View.GONE)
            views.setChronometer(R.id.distance, SystemClock.elapsedRealtime(), null, false)
            views.setTextViewText(R.id.city, context.getString(R.string.unavailable))
            views.setTextViewText(R.id.country, context.getString(R.string.tap_refresh))
            views.setTextViewText(R.id.offset, "")
            return views
        }

        val format = if (preferences.seconds) "HH:mm:ss" else "HH:mm"
        views.setViewVisibility(R.id.digital_clock, View.VISIBLE)
        views.setViewVisibility(R.id.distance, View.VISIBLE)
        views.setViewVisibility(R.id.analogue_clock, if (layout == R.layout.widget_wide) View.VISIBLE else View.GONE)
        views.setCharSequence(R.id.digital_clock, "setFormat12Hour", format)
        views.setCharSequence(R.id.digital_clock, "setFormat24Hour", format)
        views.setString(R.id.digital_clock, "setTimeZone", winner.meta.name)
        views.setString(R.id.analogue_clock, "setTimeZone", winner.meta.name)
        views.setIcon(R.id.analogue_clock, "setSecondHand", if (preferences.seconds) Icon.createWithResource(context, R.drawable.clock_second) else null)
        views.setTextViewText(R.id.city, winner.meta.city)
        views.setTextViewText(R.id.country, winner.meta.countryName)
        views.setTextViewText(R.id.offset, winner.offsetLabel)
        val before = winner.signedDistanceMs < 0
        val targetEpoch = nowMs - winner.signedDistanceMs
        val base = SystemClock.elapsedRealtime() + targetEpoch - System.currentTimeMillis()
        views.setChronometerCountDown(R.id.distance, before)
        views.setChronometer(R.id.distance, base, context.getString(if (before) R.string.before_five else R.string.after_five), true)
        return views
    }
}
