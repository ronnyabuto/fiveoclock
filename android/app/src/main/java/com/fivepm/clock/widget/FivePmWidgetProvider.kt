package com.fivepm.clock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class FivePmWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetController.update(context, AppWidgetManager.ACTION_APPWIDGET_UPDATE)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        WidgetController.update(context, AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetController.update(context, AppWidgetManager.ACTION_APPWIDGET_DELETED)
    }

    override fun onDisabled(context: Context) { WidgetController.cancel(context) }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ALARM, REFRESH, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                WidgetController.update(context, intent.action!!)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ALARM = "com.fivepm.clock.ALARM"
        const val REFRESH = "com.fivepm.clock.REFRESH"
    }
}
