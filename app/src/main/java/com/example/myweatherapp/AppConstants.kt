package com.example.myweatherapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Object holding application-wide constants and utility functions.
 */
object AppConstants {
    // Base URL for the OpenWeatherMap API.
    const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    /**
     * Formats a Unix timestamp (in seconds) to a human-readable time string (e.g., "6:00 AM").
     * Adjusts the time based on the provided timezone offset if available; otherwise, uses device default.
     * @param timestampSeconds The Unix timestamp in seconds.
     * @param timezoneOffsetSeconds Optional timezone offset from UTC in seconds.
     * @return Formatted time string or "N/A" if timestamp is null or formatting fails.
     */
    fun formatUnixTimestampToTime(timestampSeconds: Long?, timezoneOffsetSeconds: Int? = null): String {
        if (timestampSeconds == null) return "N/A"
        return try {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            // If a specific city timezone offset is provided, use it to display time relative to that city.
            if (timezoneOffsetSeconds != null) {
                val offsetHours = timezoneOffsetSeconds / 3600
                val offsetMinutes = (Math.abs(timezoneOffsetSeconds) % 3600) / 60
                val sign = if (offsetHours >= 0) "+" else "-"
                val gmtString = String.format("GMT%s%02d:%02d", sign, Math.abs(offsetHours), offsetMinutes)
                sdf.timeZone = TimeZone.getTimeZone(gmtString)
            } else {
                // For global moments like sunrise/sunset, if no city-specific offset is given,
                // displaying in the device's local time is a common fallback.
                sdf.timeZone = TimeZone.getDefault()
            }
            val netDate = Date(timestampSeconds * 1000) // Convert seconds to milliseconds for Date constructor.
            sdf.format(netDate)
        } catch (e: Exception) {
            "N/A" // Fallback in case of any formatting errors.
        }
    }

    /**
     * Maps OpenWeatherMap icon codes to local Lottie animation resource IDs.
     * @param iconCode The icon code from the API (e.g., "01d", "04n").
     * @return The resource ID of the corresponding Lottie animation, or null if no mapping exists.
     */
    fun getLottieAnimationForIcon(iconCode: String?): Int? {
        return when (iconCode) {
            // Day Lottie animations
            "01d" -> R.raw.day // Clear sky
            "02d" -> R.raw.clouds // Few clouds
            "03d" -> R.raw.clouds // Scattered clouds
            "04d" -> R.raw.clouds // Broken clouds / Overcast clouds
            "09d" -> R.raw.rain   // Shower rain
            "10d" -> R.raw.rain   // Rain
            "11d" -> R.raw.thunderstorm // Thunderstorm
            "13d" -> R.raw.snow   // Snow
            "50d" -> R.raw.fog    // Mist, Fog, Haze etc.

            // Night Lottie animations
            "01n" -> R.raw.night  // Clear sky
            "02n" -> R.raw.clouds // Few clouds
            "03n" -> R.raw.clouds // Scattered clouds
            "04n" -> R.raw.clouds // Broken clouds / Overcast clouds
            "09n" -> R.raw.rain   // Shower rain
            "10n" -> R.raw.rain   // Rain
            "11n" -> R.raw.thunderstorm // Thunderstorm
            "13n" -> R.raw.snow   // Snow
            "50n" -> R.raw.fog    // Mist, Fog, Haze etc.
            
            else -> null // No specific animation for unknown codes or if iconCode is null.
        }
    }

    /**
     * Formats wind data into a display string including a directional arrow.
     * @param speed Wind speed (not currently used in output string but available).
     * @param degrees Wind direction in degrees.
     * @return A string like "WIND: ▲" or "WIND: -" if direction is unavailable.
     */
    fun formatWind(speed: Double?, degrees: Int?): String {
        // Wind speed is available if needed in the future: 
        // val speedString = speed?.let { "%.1f m/s".format(Locale.US, it) } ?: "N/A"
        
        // Determine the arrow character based on wind direction in degrees.
        val arrowString = degrees?.let {
            // Array of 16 Unicode arrow characters for different directions.
            val arrows = arrayOf(
                "▲", "⬈", "⬈", "⬈", "►", "⬊", "⬊", "⬊", // N, NE, E, SE (with intermediate NE/SE)
                "▼", "⬋", "⬋", "⬋", "◄", "⬉", "⬉", "⬉"  // S, SW, W, NW (with intermediate SW/NW)
            )
            // Normalize degrees and calculate index for the arrows array.
            // Each primary direction covers 22.5 degrees (360 / 16 segments).
            // Offset by 11.25 to center the segments (e.g., N is 348.75 - 11.25).
            val normalizedDegrees = (it + 11.25) % 360
            val index = (normalizedDegrees / 22.5).toInt()
            arrows[index % 16] // Ensure index wraps around for the 16-point compass.
        } ?: "-" // Default to a dash if wind direction degrees are not available.

        return "WIND: $arrowString"
    }
}