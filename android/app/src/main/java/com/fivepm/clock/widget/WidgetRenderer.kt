package com.fivepm.clock.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.graphics.Paint
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.fivepm.clock.ClockAppearance
import com.fivepm.clock.ClockStyle
import com.fivepm.clock.MainActivity
import com.fivepm.clock.R
import com.fivepm.clock.domain.ClockState
import kotlin.math.roundToInt

object WidgetRenderer {
    internal fun layouts(style: ClockStyle): Map<SizeF, Int> = when (style) {
        ClockStyle.DIGITAL -> mapOf(
            SizeF(150f, 160f) to R.layout.widget_compact,
            SizeF(280f, 160f) to R.layout.widget_wide,
            SizeF(150f, 240f) to R.layout.widget_tall_compact,
            SizeF(240f, 240f) to R.layout.widget_tall,
        )
        ClockStyle.ANALOGUE -> mapOf(
            SizeF(150f, 160f) to R.layout.widget_analogue_compact,
            SizeF(300f, 160f) to R.layout.widget_analogue_wide,
            SizeF(150f, 280f) to R.layout.widget_analogue_tall_compact,
            SizeF(240f, 280f) to R.layout.widget_analogue_tall,
        )
    }

    internal fun resourceFor(style: ClockStyle, size: SizeF): Int = layouts(style).entries
        .filter { (minimum, _) -> minimum.width <= size.width && minimum.height <= size.height }
        .maxByOrNull { (minimum, _) -> minimum.width * minimum.height }?.value ?: layouts(style).values.first()

    fun render(context: Context, state: ClockState?, appearance: ClockAppearance, sizes: List<SizeF> = emptyList()): RemoteViews {
        val available = sizes.filter { it.width > 0 && it.height > 0 && it.width.isFinite() && it.height.isFinite() }.distinct().take(16)
        val resources = if (available.isEmpty()) layouts(appearance.style) else available.associateWith { resourceFor(appearance.style, it) }
        return RemoteViews(resources.mapValues { (size, resource) -> layout(context, resource, state, appearance, size) })
    }

    internal fun layout(context: Context, resource: Int, state: ClockState?, appearance: ClockAppearance, size: SizeF? = null): RemoteViews {
        val views = RemoteViews(context.packageName, resource)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_CLOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val retry = PendingIntent.getBroadcast(context, 1,
            Intent(context, FivePmWidgetProvider::class.java).setAction(FivePmWidgetProvider.REFRESH), PendingIntent.FLAG_IMMUTABLE)
        val winner = state?.winner
        views.setOnClickPendingIntent(R.id.widget_root, if (winner == null) retry else open)
        val analogue = appearance.style == ClockStyle.ANALOGUE
        views.setViewVisibility(R.id.digital_clock, if (winner != null && !analogue) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.analogue_clock, if (winner != null && analogue) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.city, winner?.meta?.city ?: context.getString(R.string.unavailable))
        views.setTextViewText(R.id.country, winner?.meta?.countryName ?: context.getString(R.string.tap_retry))
        val largeText = context.resources.configuration.fontScale >= 1.3f
        val compact = resource in listOf(R.layout.widget_compact, R.layout.widget_analogue_compact)
        val tall = resource in listOf(R.layout.widget_tall, R.layout.widget_tall_compact, R.layout.widget_analogue_tall, R.layout.widget_analogue_tall_compact)
        val narrowTall = resource in listOf(R.layout.widget_tall_compact, R.layout.widget_analogue_tall_compact)
        views.setTextViewText(R.id.context_label, context.getString(
            if (largeText && (compact || narrowTall || (analogue && !tall))) R.string.near_five else R.string.closest_to_five))
        views.setViewVisibility(R.id.country, if (winner != null && (largeText || (analogue && compact) || winner.meta.countryName.isBlank())) View.GONE else View.VISIBLE)
        views.setTextViewTextSize(R.id.context_label, TypedValue.COMPLEX_UNIT_SP, if (largeText || compact) 11f else if (tall && !analogue) 13f else 12f)
        val citySize = if (analogue) {
            if (largeText) { if (tall) 20f else 18f } else { if (compact) 18f else if (tall) 24f else 26f }
        } else {
            if (largeText) { if (tall) 26f else if (compact) 18f else 22f } else { if (tall) 32f else if (compact) 22f else 26f }
        }
        views.setTextViewTextSize(R.id.city, TypedValue.COMPLEX_UNIT_SP, citySize)
        if (analogue) {
            val preferredFace = if (largeText) { if (compact) 52f else if (tall) 104f else 112f }
                else { if (compact) 88f else if (tall) 144f else 128f }
            val density = context.resources.displayMetrics.density
            val widthDp = size?.width ?: if (narrowTall || compact) 150f else 240f
            val paddingDp = if (compact) 12f else 16f
            val availablePixels = (widthDp * density).toInt() - 2 * (paddingDp * density).roundToInt()
            val face = minOf(preferredFace, availablePixels / density)
            views.setViewLayoutWidth(R.id.analogue_clock, face, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.analogue_clock, face, TypedValue.COMPLEX_UNIT_DIP)
        } else {
            val timeSize = if (largeText) { if (tall) 48f else if (compact) 26f else 36f }
                else { if (tall) 64f else if (compact) 34f else 46f }
            val horizontalPadding = if (compact || narrowTall) 24f else if (tall) 40f else 32f
            val minWidth = (size?.width ?: if (compact || resource == R.layout.widget_tall_compact) 150f else if (tall) 240f else 280f) - horizontalPadding
            views.setTextViewTextSize(R.id.digital_clock, TypedValue.COMPLEX_UNIT_SP,
                fittingTimeSize(context, timeSize, minWidth, appearance.seconds))
        }
        if (winner == null) return views
        if (analogue) {
            views.setString(R.id.analogue_clock, "setTimeZone", winner.meta.name)
            views.setIcon(R.id.analogue_clock, "setSecondHand", if (appearance.seconds) Icon.createWithResource(context, R.drawable.clock_second) else null)
        } else {
            views.setCharSequence(R.id.digital_clock, "setFormat12Hour", if (appearance.seconds) "h:mm:ss a" else "h:mm a")
            views.setCharSequence(R.id.digital_clock, "setFormat24Hour", if (appearance.seconds) "HH:mm:ss" else "HH:mm")
            views.setString(R.id.digital_clock, "setTimeZone", winner.meta.name)
        }
        return views
    }

    private fun fittingTimeSize(context: Context, preferredSp: Float, widthDp: Float, seconds: Boolean): Float {
        val paint = Paint().apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
        }
        val metrics = context.resources.displayMetrics
        val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, metrics)
        val samples = if (android.text.format.DateFormat.is24HourFormat(context)) {
            listOf(if (seconds) "23:59:59" else "23:59")
        } else {
            val markers = java.text.DateFormatSymbols(context.resources.configuration.locales[0]).amPmStrings
            markers.map { if (seconds) "12:59:59 $it" else "12:59 $it" }
        }
        var size = preferredSp
        while (size > 12f) {
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics)
            if (samples.all { paint.measureText(it) <= width }) break
            size -= 1f
        }
        return size
    }
}
