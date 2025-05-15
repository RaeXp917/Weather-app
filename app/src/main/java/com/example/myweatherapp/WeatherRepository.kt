package com.example.myweatherapp

import android.util.Log

/**
 * Repository for weather data.
 * This class abstracts the data source (WeatherApiService) from the ViewModel.
 * It catches exceptions from the ApiService and wraps results (success or failure) in Kotlin's Result type.
 */
class WeatherRepository(private val weatherApiService: WeatherApiService) {

    // --- CURRENT WEATHER REPOSITORY METHODS ---

    /**
     * Gets current weather data for a city.
     * @param cityName The name of the city.
     * @return Result<WeatherDataResponse> holding either the weather data or an exception.
     */
    suspend fun getCurrentWeather(cityName: String): Result<WeatherDataResponse> {
        return try {
            // Call the API service to fetch current weather.
            val response = weatherApiService.fetchCurrentWeather(cityName)
            Log.i("WeatherRepository", "Successfully fetched current weather for $cityName from API service.")
            Result.success(response)
        } catch (e: Exception) {
            // Log the error and return a failure Result containing the exception.
            // This allows the ViewModel to handle specific error types.
            Log.e("WeatherRepository", "Error fetching current weather for $cityName: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets current weather data by geographical coordinates.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @return Result<WeatherDataResponse> holding either the weather data or an exception.
     */
    suspend fun getCurrentWeatherByCoords(latitude: Double, longitude: Double): Result<WeatherDataResponse> {
        return try {
            val response = weatherApiService.fetchCurrentWeatherByCoords(latitude, longitude)
            Log.i("WeatherRepository", "Successfully fetched current weather for coords ($latitude, $longitude) from API service.")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error fetching current weather for coords ($latitude, $longitude): ${e.message}", e)
            Result.failure(e)
        }
    }
    // --- END CURRENT WEATHER REPOSITORY METHODS ---

    // --- FORECAST REPOSITORY METHODS ---

    /**
     * Gets 5-day weather forecast for a city.
     * @param cityName The name of the city.
     * @return Result<ForecastResponse> holding either the forecast data or an exception.
     */
    suspend fun getWeatherForecast(cityName: String): Result<ForecastResponse> {
        return try {
            val response = weatherApiService.fetchWeatherForecast(cityName)
            Log.i("WeatherRepository", "Successfully fetched forecast for $cityName from API service.")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error fetching forecast for $cityName: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets 5-day weather forecast by geographical coordinates.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @return Result<ForecastResponse> holding either the forecast data or an exception.
     */
    suspend fun getWeatherForecastByCoords(latitude: Double, longitude: Double): Result<ForecastResponse> {
        return try {
            val response = weatherApiService.fetchWeatherForecastByCoords(latitude, longitude)
            Log.i("WeatherRepository", "Successfully fetched forecast for coords ($latitude, $longitude) from API service.")
            Result.success(response)
        } catch (e: Exception) {
            Log.e("WeatherRepository", "Error fetching forecast for coords ($latitude, $longitude): ${e.message}", e)
            Result.failure(e)
        }
    }
    // --- END FORECAST REPOSITORY METHODS ---
}