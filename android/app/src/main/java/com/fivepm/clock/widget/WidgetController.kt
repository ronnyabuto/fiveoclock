package com.fivepm.clock.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import com.fivepm.clock.BuildConfig
import com.fivepm.clock.ClockPreferences

object WidgetController {
    private const val DIAGNOSTICS = "widget_diagnostics"

    @Synchronized fun update(context: Context, reason: String) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, FivePmWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancel(context)
            return
        }
        val now = System.currentTimeMillis()
        val previous = context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE)
        val scheduled = previous.getLong("nextEvent", 0)
        val lateness = if (reason == FivePmWidgetProvider.ALARM && scheduled > 0) maxOf(0, now - scheduled) else previous.getLong("lateness", 0)
        val state = ZoneRepository.domain(context).state(now)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // Allow exact-boundary ties to settle before selecting and rearming.
        val nextEvent = state?.nextEventEpochMs?.plus(50)
        val exact = if (nextEvent != null) schedule(alarmManager, nextEvent, alarmIntent(context))
            else { alarmManager.cancel(alarmIntent(context)); false }
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR, recoveryIntent(context))
        val preferences = ClockPreferences(context)
        for (id in ids) {
            val sizes = manager.getAppWidgetOptions(id).getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty()
            manager.updateAppWidget(id, WidgetRenderer.render(context, state, preferences.appearance(id), sizes))
        }
        previous.edit().putLong("updated", now).putLong("nextEvent", nextEvent ?: 0)
            .putLong("lateness", lateness).putString("reason", reason).putBoolean("exact", exact).apply()
        if (BuildConfig.DEBUG) Log.i("FivePmClock", "reason=$reason scheduled=$scheduled received=$now lateMs=$lateness next=$nextEvent exact=$exact city=${state?.winner?.meta?.city}")
    }

    // API 33+ USE_EXACT_ALARM is declared; the SDK annotation only lists SCHEDULE_EXACT_ALARM.
    @SuppressLint("MissingPermission")
    private fun schedule(manager: AlarmManager, epoch: Long, alarm: PendingIntent): Boolean {
        if (manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, alarm)
                return true
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, alarm)
            }
        } else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, alarm)
        return false
    }

    fun diagnostics(context: Context): Diagnostics {
        val values = context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE)
        return Diagnostics(values.getLong("updated", 0), values.getLong("nextEvent", 0),
            values.getLong("lateness", 0), values.getBoolean("exact", false))
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(alarmIntent(context))
        manager.cancel(recoveryIntent(context))
        context.getSharedPreferences(DIAGNOSTICS, Context.MODE_PRIVATE).edit().remove("nextEvent").apply()
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, FivePmWidgetProvider::class.java).setAction(FivePmWidgetProvider.ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun recoveryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 1,
        Intent(context, FivePmWidgetProvider::class.java).setAction(FivePmWidgetProvider.RECOVERY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    data class Diagnostics(val updated: Long, val nextEvent: Long, val latenessMs: Long, val exact: Boolean)
}
