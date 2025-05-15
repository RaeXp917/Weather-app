package com.example.myweatherapp

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.example.myweatherapp.BuildConfig
import com.example.myweatherapp.AppConstants
import io.ktor.client.plugins.ResponseException

/**
 * Service class responsible for making network calls to the OpenWeatherMap API using Ktor.
 * It handles fetching current weather and forecast data, and includes basic error parsing.
 */
class WeatherApiService {

    // Ktor HttpClient configured for Android with JSON content negotiation.
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true // For easier debugging of JSON responses
                isLenient = true // Allows parsing of non-standard JSON properties
                ignoreUnknownKeys = true // Prevents crashes if API adds new fields not in our models
            })
        }
    }

    // --- CURRENT WEATHER METHODS ---

    /**
     * Fetches the current weather data for a specified city name.
     * @param cityName The name of the city.
     * @return WeatherDataResponse containing current weather details.
     * @throws IllegalStateException if API key is missing.
     * @throws OpenWeatherApiException if the API returns an error (e.g., 404 city not found).
     * @throws Exception for other network or parsing issues.
     */
    suspend fun fetchCurrentWeather(cityName: String): WeatherDataResponse {
        val actualApiKey = BuildConfig.API_KEY
        val baseUrl = AppConstants.BASE_URL

        // Critical check: Ensure API key is available before making a call.
        if (actualApiKey.isNullOrEmpty() || actualApiKey == "null") {
            Log.e("WeatherApiService", "API Key is missing or invalid in BuildConfig for current weather.")
            throw IllegalStateException("API Key is missing or invalid")
        }
        val urlString = "${baseUrl}weather?q=$cityName&appid=$actualApiKey&units=metric"
        Log.d("WeatherApiService", "Requesting URL (current by city): $urlString")
        try {
            return client.get(urlString).body() // Ktor automatically parses JSON to WeatherDataResponse
        } catch (e: ResponseException) {
            // Handle HTTP errors (4xx, 5xx) from the API.
            val errorBody = try { e.response.body<ApiErrorResponse>() } catch (_: Exception) { null } // Attempt to parse the error body
            Log.e("WeatherApiService", "CurrentWeather API Error: HTTP ${e.response.status.value}, Body: $errorBody, Message: ${e.message}")
            throw OpenWeatherApiException(
                apiMessage = errorBody?.message ?: "Error fetching current weather for $cityName",
                httpStatusCode = e.response.status.value,
                apiErrorCode = errorBody?.cod
            )
        }
    }

    /**
     * Fetches the current weather data for specified geographical coordinates.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @return WeatherDataResponse containing current weather details.
     * @throws OpenWeatherApiException for API errors.
     */
    suspend fun fetchCurrentWeatherByCoords(latitude: Double, longitude: Double): WeatherDataResponse {
        val actualApiKey = BuildConfig.API_KEY
        val baseUrl = AppConstants.BASE_URL

        if (actualApiKey.isNullOrEmpty() || actualApiKey == "null") {
            Log.e("WeatherApiService", "API Key is missing or invalid in BuildConfig for current weather by coords.")
            throw IllegalStateException("API Key is missing or invalid")
        }
        val urlString = "${baseUrl}weather?lat=$latitude&lon=$longitude&appid=$actualApiKey&units=metric"
        Log.d("WeatherApiService", "Requesting URL (current by coords): $urlString")
        try {
            return client.get(urlString).body()
        } catch (e: ResponseException) {
            val errorBody = try { e.response.body<ApiErrorResponse>() } catch (_: Exception) { null }
            Log.e("WeatherApiService", "CurrentWeatherByCoords API Error: HTTP ${e.response.status.value}, Body: $errorBody, Message: ${e.message}")
            throw OpenWeatherApiException(
                apiMessage = errorBody?.message ?: "Error fetching current weather for coordinates ($latitude, $longitude)",
                httpStatusCode = e.response.status.value,
                apiErrorCode = errorBody?.cod
            )
        }
    }
    // --- END CURRENT WEATHER METHODS ---

    // --- FORECAST METHODS ---
    /**
     * Fetches the 5-day weather forecast for a specified city name.
     * @param cityName The name of the city.
     * @return ForecastResponse containing forecast details.
     * @throws OpenWeatherApiException for API errors.
     */
    suspend fun fetchWeatherForecast(cityName: String): ForecastResponse {
        val actualApiKey = BuildConfig.API_KEY
        val baseUrl = AppConstants.BASE_URL

        if (actualApiKey.isNullOrEmpty() || actualApiKey == "null") {
            Log.e("WeatherApiService", "API Key is missing or invalid in BuildConfig for forecast.")
            throw IllegalStateException("API Key is missing or invalid")
        }
        val urlString = "${baseUrl}forecast?q=$cityName&appid=$actualApiKey&units=metric"
        Log.d("WeatherApiService", "Requesting URL (forecast by city): $urlString")
        try {
            return client.get(urlString).body()
        } catch (e: ResponseException) {
            val errorBody = try { e.response.body<ApiErrorResponse>() } catch (_: Exception) { null }
            Log.e("WeatherApiService", "Forecast API Error: HTTP ${e.response.status.value}, Body: $errorBody, Message: ${e.message}")
            throw OpenWeatherApiException(
                apiMessage = errorBody?.message ?: "Error fetching forecast for $cityName",
                httpStatusCode = e.response.status.value,
                apiErrorCode = errorBody?.cod
            )
        }
    }

    /**
     * Fetches the 5-day weather forecast for specified geographical coordinates.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @return ForecastResponse containing forecast details.
     * @throws OpenWeatherApiException for API errors.
     */
    suspend fun fetchWeatherForecastByCoords(latitude: Double, longitude: Double): ForecastResponse {
        val actualApiKey = BuildConfig.API_KEY
        val baseUrl = AppConstants.BASE_URL

        if (actualApiKey.isNullOrEmpty() || actualApiKey == "null") {
            Log.e("WeatherApiService", "API Key is missing or invalid in BuildConfig for forecast by coords.")
            throw IllegalStateException("API Key is missing or invalid")
        }
        val urlString = "${baseUrl}forecast?lat=$latitude&lon=$longitude&appid=$actualApiKey&units=metric"
        Log.d("WeatherApiService", "Requesting URL (forecast by coords): $urlString")
        try {
            return client.get(urlString).body()
        } catch (e: ResponseException) {
            val errorBody = try { e.response.body<ApiErrorResponse>() } catch (_: Exception) { null }
            Log.e("WeatherApiService", "ForecastByCoords API Error: HTTP ${e.response.status.value}, Body: $errorBody, Message: ${e.message}")
            throw OpenWeatherApiException(
                apiMessage = errorBody?.message ?: "Error fetching forecast for coordinates ($latitude, $longitude)",
                httpStatusCode = e.response.status.value,
                apiErrorCode = errorBody?.cod
            )
        }
    }
    // --- END FORECAST METHODS ---
}