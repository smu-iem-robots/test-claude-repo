// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.victorialauncher.R
import java.util.Locale

/**
 * The last weather another app sent us, and whether it is still worth drawing.
 *
 * Victoria has no network permission and is not getting one, so there is no fetching here: a
 * weather app that already has the forecast broadcasts it, and this is where what arrived is
 * kept. Nothing asks for a location either — the sender says where it is.
 *
 * Written to disk rather than held in memory because a widget outlives the process that drew
 * it. A launcher gets killed often, and weather that vanishes every time it does is worse than
 * no weather at all.
 */
object ClockWidgetWeather {

    private const val FILE = "clock_widget_weather"
    private const val KEY_TEMP_K = "tempK"
    private const val KEY_CONDITION = "condition"
    private const val KEY_RECEIVED_AT = "receivedAt"
    private const val KEY_WIND_KMH = "windKmh"
    private const val KEY_WIND_DEGREES = "windDegrees"

    /**
     * Past this, say nothing rather than something old.
     *
     * A forecast is a claim about now. Breezy sends on its own schedule and after a long doze
     * the last one can be hours stale, which is the one case where a blank row is the honest
     * answer.
     */
    private const val FRESH_FOR_MS = 3 * 60 * 60 * 1000L

    /** Kelvin, and wide enough for anywhere on Earth without accepting a parse gone wrong. */
    private val PLAUSIBLE_K = 150..350

    /** Enough for "Partly cloudy"; past this something is wrong with the sender, not the sky. */
    private const val MAX_CONDITION_CHARS = 40

    /** From calm to a little past the strongest gust ever measured, 408 km/h. */
    private val PLAUSIBLE_WIND_KMH = 0.0..420.0

    data class Reading(
        val tempK: Int,
        val condition: String,
        /** Null when the sender said nothing about the wind, which is not the same as calm. */
        val windKmh: Double? = null,
        /** Where the wind comes from, 0 to 360, or null when no direction came with it. */
        val windDegrees: Int? = null,
    )

    /** Null when nothing has arrived, or when what did is too old to repeat. */
    fun current(context: Context): Reading? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val tempK = prefs.getInt(KEY_TEMP_K, 0)
        if (tempK !in PLAUSIBLE_K) return null
        val receivedAt = prefs.getLong(KEY_RECEIVED_AT, 0L)
        if (receivedAt <= 0L || System.currentTimeMillis() - receivedAt > FRESH_FOR_MS) return null
        // Under the same age limit as the temperature, since they arrived in the same broadcast.
        val windKmh = prefs.getFloat(KEY_WIND_KMH, -1f).toDouble()
        val windDegrees = prefs.getInt(KEY_WIND_DEGREES, -1)
        return Reading(
            tempK = tempK,
            condition = prefs.getString(KEY_CONDITION, null).orEmpty(),
            windKmh = windKmh.takeIf { it in PLAUSIBLE_WIND_KMH },
            windDegrees = windDegrees.takeIf { it in 0..360 },
        )
    }

    /**
     * Returns false for anything that does not look like a temperature, having stored nothing.
     *
     * The wind is optional and checked on its own: a speed or direction that is missing or does not
     * look like one is dropped, and the temperature is kept. Dropped, not left as it was — the
     * last reading's wind beside this reading's temperature would be a claim nobody sent.
     */
    fun store(
        context: Context,
        tempK: Int,
        condition: String?,
        windKmh: Double? = null,
        windDegrees: Int? = null,
    ): Boolean {
        if (tempK !in PLAUSIBLE_K) return false
        val wind = windKmh?.takeIf { it in PLAUSIBLE_WIND_KMH }
        val degrees = windDegrees?.takeIf { it in 0..360 }
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP_K, tempK)
            .putString(KEY_CONDITION, condition?.take(MAX_CONDITION_CHARS)?.trim().orEmpty())
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
        if (wind != null) editor.putFloat(KEY_WIND_KMH, wind.toFloat()) else editor.remove(KEY_WIND_KMH)
        if (degrees != null) editor.putInt(KEY_WIND_DEGREES, degrees) else editor.remove(KEY_WIND_DEGREES)
        editor.apply()
        return true
    }

    fun formatted(reading: Reading, fahrenheit: Boolean): String = row(reading, fahrenheit, wind = null)

    /**
     * Temperature, then [wind] when there is one, then the condition, two spaces apart.
     *
     * The condition goes last because it is the part a narrow widget can do without. The row is
     * one line, cut with an ellipsis where it runs out of room, and the condition is free text
     * of any length from the sender, where the temperature and the wind are short and are what
     * the row is read for — the wind most of all just after it has been switched on, when losing
     * it off the end looked like the setting doing nothing. Without the wind this is exactly the
     * row from before there was any.
     */
    internal fun row(reading: Reading, fahrenheit: Boolean, wind: String?): String {
        val celsius = reading.tempK - 273.15
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        val unit = if (fahrenheit) "F" else "C"
        val degrees = String.format(Locale.getDefault(), "%.0f°%s", value, unit)
        return listOfNotNull(degrees, wind, reading.condition.takeIf { it.isNotEmpty() })
            .joinToString("  ")
    }

    /**
     * The whole row as [config] draws it: see [row], with the wind if it asks for that and the
     * sender gave one.
     *
     * One function for the widget and for its settings preview, so the preview cannot promise a
     * row the widget does not draw.
     */
    fun line(context: Context, reading: Reading, config: ClockWidgetConfig): String {
        val kmh = reading.windKmh
        if (kmh == null || config.windUnit == WindUnit.OFF) {
            return formatted(reading, config.weatherFahrenheit)
        }
        // Names rather than a fixed "SW", since not every language abbreviates the points alike.
        val compass = runCatching {
            context.resources.getStringArray(R.array.widget_clock_wind_compass).toList()
        }.getOrDefault(emptyList())
        val wind = ClockWidgetWind.formatted(kmh, reading.windDegrees, config.windUnit, compass)
        return row(reading, config.weatherFahrenheit, wind)
    }

    /** Where Fahrenheit is what people mean by a temperature, so the first draw is not wrong. */
    fun fahrenheitByDefault(): Boolean =
        Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    /**
     * Turns the receiver on, or off again once no widget wants weather.
     *
     * It ships disabled. A receiver another app can reach is surface that should not exist
     * while the feature is switched off, and this is the one way to have none at all rather
     * than some that is merely careful.
     */
    fun setReceiverEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, ClockWidgetWeatherReceiver::class.java),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
