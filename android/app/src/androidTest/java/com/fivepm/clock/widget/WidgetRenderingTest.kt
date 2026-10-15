package com.fivepm.clock.widget

import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fivepm.clock.ClockPreferences
import com.fivepm.clock.R
import com.fivepm.clock.domain.TimeDomain
import com.fivepm.clock.domain.ZoneMeta
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Suppress("DEPRECATION")
    @Test fun remoteActionsApplyToBothLayoutsAndBothTimerDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = ClockPreferences(context)
        val previousSeconds = preferences.seconds
        val website = preferences.website
        try {
            instrumentation.runOnMainSync {
                val domain = TimeDomain(listOf(ZoneMeta("Asia/Kathmandu", "Kathmandu", "Nepal", 0)))
                for (seconds in listOf(true, false)) {
                    preferences.save(seconds, website)
                    for (layout in listOf(R.layout.widget_compact, R.layout.widget_wide)) {
                        for (iso in listOf("2026-01-15T11:14:00Z", "2026-01-15T11:15:00Z", "2026-01-15T11:16:00Z")) {
                            val now = Instant.parse(iso).toEpochMilli()
                            val state = domain.state(now)!!
                            val parent = FrameLayout(context)
                            val view = WidgetRenderer.layout(context, layout, state, now).apply(context, parent)
                            assertEquals("Kathmandu", view.findViewById<TextView>(R.id.city).text)
                            val clock = view.findViewById<TextClock>(R.id.digital_clock)
                            assertEquals("Asia/Kathmandu", clock.timeZone)
                            assertEquals(if (seconds) "HH:mm:ss" else "HH:mm", clock.format24Hour)
                            assertEquals("Asia/Kathmandu", view.findViewById<android.widget.AnalogClock>(R.id.analogue_clock).timeZone)
                            assertEquals(if (layout == R.layout.widget_wide) View.VISIBLE else View.GONE, view.findViewById<View>(R.id.analogue_clock).visibility)
                            val timer = view.findViewById<Chronometer>(R.id.distance)
                            assertEquals(state.winner.signedDistanceMs < 0, timer.isCountDown)
                            val expectedTarget = now - state.winner.signedDistanceMs
                            assertTrue(kotlin.math.abs(timer.base - SystemClock.elapsedRealtime() + System.currentTimeMillis() - expectedTarget) < 1000)
                            assertEquals(context.getString(if (timer.isCountDown) R.string.before_five else R.string.after_five), timer.format)
                            timer.stop()
                        }
                    }
                }
            }
        } finally { preferences.save(previousSeconds, website) }
    }

    @Test fun unavailableDataHidesClocksAndStopsTimer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val view = WidgetRenderer.layout(context, R.layout.widget_wide, null, System.currentTimeMillis())
                .apply(context, FrameLayout(context))
            assertEquals(View.GONE, view.findViewById<View>(R.id.digital_clock).visibility)
            assertEquals(View.GONE, view.findViewById<View>(R.id.distance).visibility)
            assertEquals(context.getString(R.string.unavailable), view.findViewById<TextView>(R.id.city).text)
            val now = System.currentTimeMillis()
            val state = TimeDomain(listOf(ZoneMeta("UTC", "UTC", "", 0))).state(now)
            WidgetRenderer.layout(context, R.layout.widget_wide, state, now).reapply(context, view)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.digital_clock).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.analogue_clock).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.distance).visibility)
            view.findViewById<Chronometer>(R.id.distance).stop()
        }
    }
}
