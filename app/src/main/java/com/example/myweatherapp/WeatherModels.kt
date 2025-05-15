package com.example.myweatherapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class WeatherCondition(
    val main: String? = null, // e.g., "Clouds", "Rain"
    val description: String? = null, // e.g., "overcast clouds"
    val icon: String? = null // e.g., "04d" - for weather icons
)

@Serializable
data class MainWeatherData(
    val temp: Double? = null, // Temperature. OpenWeatherMap default is Kelvin
    @SerialName("feels_like")
    val feelsLike: Double? = null,
    @SerialName("temp_min")
    val tempMin: Double? = null,
    @SerialName("temp_max")
    val tempMax: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null
)

@Serializable
data class WindData(
    val speed: Double? = null,
    val deg: Int? = null // Wind direction in degrees
)

@Serializable
data class SysData( // Contains sunrise/sunset times, country code etc.
    val country: String? = null,
    val sunrise: Long? = null, // Unix timestamp, UTC
    val sunset: Long? = null   // Unix timestamp, UTC
)

@Serializable
data class WeatherDataResponse(
    val weather: List<WeatherCondition>? = null,
    val main: MainWeatherData? = null,
    val wind: WindData? = null,
    val sys: SysData? = null,
    val name: String? = null, // City name
    val cod: Int? = null,     // API response code e.g. 200 for success, 404 for city not found
    val timezone: Int? = null, // Shift in seconds from UTC
    @SerialName("message")    // OpenWeatherMap often uses "message" for error strings
    val errorMessage: String? = null
)

@Serializable
data class ForecastListItem(
    val dt: Long, // Timestamp
    val main: MainWeatherData, // Can reuse MainWeatherData from current weather
    val weather: List<WeatherCondition>, // Can reuse WeatherCondition
    val wind: WindData, // Can reuse WindData
    @SerialName("dt_txt")
    val dtTxt: String // Date-time string, e.g., "2024-05-15 18:00:00"
    // Add other fields as needed, like 'pop' for probability of precipitation if available
)

@Serializable
data class CityData( // Some forecast APIs include city info
    val id: Int? = null,
    val name: String? = null,
    val country: String? = null,
    val population: Long? = null,
    val timezone: Int? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null
)

@Serializable
data class ForecastResponse(
    val cod: String? = null, // API response code (often a string "200" for this endpoint)
    val message: Int? = null, // Internal parameter
    val cnt: Int? = null, // Number of forecast items returned
    @SerialName("list")
    val items: List<ForecastListItem>? = null, // The list of forecast entries
    val city: CityData? = null
)

// New data classes for grouped forecast
@Serializable
data class HourlyForecastItem(
    val dt: Long, // Timestamp
    val main: MainWeatherData,
    val weather: List<WeatherCondition>,
    val wind: WindData,
    val dtTxt: String // Date-time string
    // Keep a reference to the original ForecastListItem if needed, or copy all relevant fields
)

@Serializable
data class DailyForecast(
    val dateMillis: Long, // To identify the day, could be midnight of that day
    val dayName: String, // e.g., "Mon, May 17"
    val hourlyItems: MutableList<HourlyForecastItem> = mutableListOf(),
    var isExpanded: Boolean = false // For UI state
)

// For parsing API error responses (like 404 City not found)
@Serializable
data class ApiErrorResponse(
    val cod: String? = null, // OpenWeatherMap error "cod" is often a String (e.g., "404")
    val message: String? = null // The error message from the API (e.g., "city not found")
)

// Custom exception for API-specific errors
class OpenWeatherApiException(
    apiMessage: String, // The user-friendly message from the API or a formulated one
    val httpStatusCode: Int, // e.g., 404, 401
    val apiErrorCode: String? = null // e.g., "404", "401"
) : Exception(apiMessage)