package com.fivepm.clock.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fivepm.clock.ClockAppearance
import com.fivepm.clock.ClockStyle
import com.fivepm.clock.R
import com.fivepm.clock.domain.TimeDomain
import com.fivepm.clock.domain.ZoneMeta
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Suppress("DEPRECATION")
    @Test fun nativeLayoutsFitTheirBoundsAcrossStylesAndFontScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val base = instrumentation.targetContext
            val cases = listOf("Tokyo" to "Japan", "San Fernando del Valle de Catamarca" to "Argentina")
            val failures = mutableListOf<String>()
            for (scale in listOf(1f, 1.3f, 2f)) {
                val configuration = Configuration(base.resources.configuration).apply { fontScale = scale }
                val context = base.createConfigurationContext(configuration)
                for (style in ClockStyle.entries) for (seconds in listOf(false, true)) {
                    val sizes = WidgetRenderer.layouts(style).keys + listOf(SizeF(213f, 395f), SizeF(360f, 300f))
                    for (size in sizes) for ((city, country) in cases) {
                        val resource = WidgetRenderer.resourceFor(style, size)
                        val now = System.currentTimeMillis()
                        val state = TimeDomain(listOf(ZoneMeta("Asia/Tokyo", city, country, 0))).state(now)!!
                        val view = WidgetRenderer.layout(context, resource, state, ClockAppearance(style, seconds), size)
                            .apply(context, FrameLayout(context))
                        val density = context.resources.displayMetrics.density
                        val width = (size.width * density).toInt()
                        val height = (size.height * density).toInt()
                        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, width, height)
                        val label = "$style ${size.width}x${size.height} font=$scale seconds=$seconds city=$city"
                        assertEquals(city, view.findViewById<TextView>(R.id.city).text.toString())
                        val clock = view.findViewById<TextClock>(R.id.digital_clock)
                        assertEquals(if (style == ClockStyle.DIGITAL) View.VISIBLE else View.GONE, clock.visibility)
                        val face = view.findViewById<android.widget.AnalogClock>(R.id.analogue_clock)
                        assertEquals(if (style == ClockStyle.ANALOGUE) View.VISIBLE else View.GONE, face.visibility)
                        if (style == ClockStyle.DIGITAL) {
                            assertEquals("Asia/Tokyo", clock.timeZone)
                            assertEquals(if (seconds) "HH:mm:ss" else "HH:mm", clock.format24Hour)
                            assertEquals(if (seconds) "h:mm:ss a" else "h:mm a", clock.format12Hour)
                            if (clock.layout.getLineWidth(0) > clock.width - clock.paddingLeft - clock.paddingRight + 1) {
                                failures.add("$label: clock text clipped")
                            }
                        } else assertEquals("Asia/Tokyo", face.timeZone)
                        for (id in listOf(R.id.context_label, R.id.city, R.id.digital_clock, R.id.analogue_clock, R.id.country)) {
                            val child = view.findViewById<View>(id)
                            if (child.visibility != View.VISIBLE) continue
                            if (child is TextView && child.text.isNotEmpty() &&
                                (child.height == 0 || child.layout.getLineBottom(child.layout.lineCount - 1) > child.height - child.paddingTop - child.paddingBottom)) {
                                failures.add("$label: ${context.resources.getResourceEntryName(id)} text cut vertically")
                            }
                            val rect = Rect(0, 0, child.width, child.height)
                            (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, rect)
                            if (rect.top < view.paddingTop || rect.bottom > height - view.paddingBottom ||
                                rect.left < view.paddingLeft || rect.right > width - view.paddingRight) {
                                failures.add("$label: ${context.resources.getResourceEntryName(id)} outside content: $rect")
                            }
                        }
                        if (city == "Tokyo" && !seconds) {
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            view.draw(Canvas(bitmap))
                            val format = if (android.text.format.DateFormat.is24HourFormat(context)) "24h" else "12h"
                            val directory = File(base.cacheDir, "widget-evidence/$format").apply { mkdirs() }
                            File(directory, "${style.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}-font-$scale.png")
                                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                }
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        }
    }

    @Test fun unavailableStateHasOneRetryActionAndRecoversOnReapply() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (style in ClockStyle.entries) {
                val appearance = ClockAppearance(style)
                val resource = WidgetRenderer.layouts(style).values.last()
                val view = WidgetRenderer.layout(context, resource, null, appearance).apply(context, FrameLayout(context))
                assertEquals(View.GONE, view.findViewById<View>(R.id.digital_clock).visibility)
                assertEquals(View.GONE, view.findViewById<View>(R.id.analogue_clock).visibility)
                assertEquals(context.getString(R.string.tap_retry), view.findViewById<TextView>(R.id.country).text)
                val state = TimeDomain(listOf(ZoneMeta("UTC", "UTC", "", 0))).state(System.currentTimeMillis())
                WidgetRenderer.layout(context, resource, state, appearance).reapply(context, view)
                assertEquals("UTC", view.findViewById<TextView>(R.id.city).text)
                assertEquals(View.VISIBLE, view.findViewById<View>(if (style == ClockStyle.DIGITAL) R.id.digital_clock else R.id.analogue_clock).visibility)
            }
        }
    }
}
