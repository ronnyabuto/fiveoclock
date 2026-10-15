package com.fivepm.clock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetConfigurationTest {
    @Test fun launcherConfigurationSavesThisWidgetAndReturnsItsId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = Int.MAX_VALUE
        val preferences = ClockPreferences(context)
        try {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            ActivityScenario.launchActivityForResult<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<RadioGroup>(R.id.clock_style).check(R.id.style_analogue)
                    activity.findViewById<CheckBox>(R.id.show_seconds).isChecked = false
                    activity.findViewById<Button>(R.id.save).performClick()
                }
                assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                assertEquals(id, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
            }
            assertEquals(ClockAppearance(ClockStyle.ANALOGUE, false), preferences.appearance(id))
        } finally { preferences.removeWidget(id) }
    }

    @Test fun appSettingsApplyAnalogueToAnExistingDigitalWidget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ClockPreferences(context)
        val original = preferences.appearance()
        val website = preferences.website
        val id = Int.MAX_VALUE
        try {
            preferences.saveAppearance(id, ClockAppearance(ClockStyle.DIGITAL, true))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<RadioGroup>(R.id.clock_style).check(R.id.style_analogue)
                    activity.findViewById<CheckBox>(R.id.show_seconds).isChecked = false
                    activity.findViewById<Button>(R.id.save).performClick()
                    assertEquals(ClockAppearance(ClockStyle.ANALOGUE, false), preferences.appearance(id))
                }
            }
        } finally {
            preferences.removeWidget(id)
            preferences.save(original.seconds, website, original.style)
        }
    }

    @Test fun incompleteConfigurationReturnsCanceled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (id in listOf(AppWidgetManager.INVALID_APPWIDGET_ID, Int.MAX_VALUE)) {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val scenario = ActivityScenario.launchActivityForResult<MainActivity>(intent)
            try {
                if (id > 0) scenario.onActivity { it.finish() }
                assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
                assertEquals(id, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            } finally { scenario.close() }
        }
    }

    @Test fun perWidgetAppearanceSurvivesDefaultChangesAndIsRemovedOnDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ClockPreferences(context)
        val original = preferences.appearance()
        val website = preferences.website
        val id = Int.MAX_VALUE
        try {
            preferences.save(false, website, ClockStyle.DIGITAL)
            preferences.saveAppearance(id, ClockAppearance(ClockStyle.ANALOGUE, false))
            preferences.save(true, website, ClockStyle.DIGITAL)
            assertEquals(ClockAppearance(ClockStyle.ANALOGUE, false), preferences.appearance(id))
            assertEquals(ClockAppearance(ClockStyle.DIGITAL, true), preferences.appearance(id - 1))
            preferences.removeWidget(id)
            assertEquals(ClockAppearance(ClockStyle.DIGITAL, true), preferences.appearance(id))
        } finally {
            preferences.removeWidget(id)
            preferences.save(original.seconds, website, original.style)
        }
    }
}
