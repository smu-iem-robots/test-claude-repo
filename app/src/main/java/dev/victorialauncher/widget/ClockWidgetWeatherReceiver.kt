// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * Takes weather from whichever app is already fetching it, over the broadcast Gadgetbridge
 * defined and Breezy Weather sends.
 *
 * This has to be exported, or the app sending it cannot reach us, and a broadcast carries no
 * trustworthy word on who sent it — so anything arriving here is a stranger's input and is
 * treated as such. What that buys an attacker is the wrong temperature on your own home
 * screen, which is the whole of it; there is nothing else here to reach.
 *
 * Two deliberate omissions. `WeatherGz` is ignored, so nothing untrusted is ever decompressed —
 * a few hundred bytes claiming to expand into a great many is a denial of service that costs
 * the sender nothing, and the gzipped extra only ever carried locations we do not draw.
 * `WeatherSecondaryJson` is ignored for the same reason: one widget shows one temperature.
 */
class ClockWidgetWeatherReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GENERIC_WEATHER) return
        val json = runCatching { intent.getStringExtra(EXTRA_WEATHER_JSON) }.getOrNull() ?: return
        // Read before parsing: a parser handed something enormous is the problem, not the
        // answer it eventually gives.
        if (json.length > MAX_JSON_CHARS) return

        val stored = runCatching {
            val obj = JSONObject(json)
            // Gadgetbridge's own WeatherSpec is the shape here: kelvin as whole degrees, wind in
            // km/h and the degrees it blows from. Breezy leaves out what it does not know rather
            // than sending a zero, so an absent wind reaches the store as no wind, not as calm.
            ClockWidgetWeather.store(
                context,
                tempK = obj.optInt("currentTemp", 0),
                condition = obj.optString("currentCondition", ""),
                windKmh = obj.optDouble("windSpeed", Double.NaN),
                windDegrees = obj.optInt("windDirection", -1),
            )
        }.getOrDefault(false)
        if (!stored) return

        // The widgets are only ever drawn when something asks, and weather arriving is the
        // only thing that knows this one changed.
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ClockWidgetProvider.componentName(context))
                ?.forEach { ClockWidgetProvider.render(context, manager, it) }
        }
    }

    private companion object {
        const val ACTION_GENERIC_WEATHER =
            "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        const val EXTRA_WEATHER_JSON = "WeatherJson"

        /** One location's forecast is a few hundred bytes; this is room to spare and no more. */
        const val MAX_JSON_CHARS = 64 * 1024
    }
}
