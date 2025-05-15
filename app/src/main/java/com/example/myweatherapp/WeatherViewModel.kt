package com.example.myweatherapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ViewModel for MyWeatherApp.
 * Manages UI-related data, handles business logic, and communicates with the WeatherRepository.
 */
class WeatherViewModel(private val weatherRepository: WeatherRepository) : ViewModel() {

    // LiveData for current weather conditions.
    private val _weatherData = MutableLiveData<WeatherDataResponse?>()
    val weatherData: LiveData<WeatherDataResponse?> = _weatherData

    // LiveData for 5-day forecast, grouped by day with hourly details.
    private val _groupedForecastData = MutableLiveData<List<DailyForecast>?>()
    val groupedForecastData: LiveData<List<DailyForecast>?> = _groupedForecastData

    // LiveData for signaling errors to the UI.
    private val _errorState = MutableLiveData<ErrorState?>()
    val errorState: LiveData<ErrorState?> = _errorState

    // LiveData to indicate loading state (e.g., network activity).
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // --- CURRENT WEATHER FETCH ---

    /**
     * Fetches current weather for a given city name.
     * Updates LiveData for weather, loading state, and errors.
     */
    fun fetchWeather(cityName: String) {
        _isLoading.value = true
        _errorState.value = ErrorState.NoError // Clear previous errors
        _weatherData.value = null // Clear previous weather data
        _groupedForecastData.value = null // Clear previous forecast data
        viewModelScope.launch {
            val result = weatherRepository.getCurrentWeather(cityName)
            processWeatherResult(result)
        }
    }

    /**
     * Fetches current weather for given latitude and longitude.
     * Updates LiveData for weather, loading state, and errors.
     */
    fun fetchWeatherByCoordinates(latitude: Double, longitude: Double) {
        _isLoading.value = true
        _errorState.value = ErrorState.NoError
        _weatherData.value = null
        _groupedForecastData.value = null
        viewModelScope.launch {
            val result = weatherRepository.getCurrentWeatherByCoords(latitude, longitude)
            processWeatherResult(result)
        }
    }
    // --- END CURRENT WEATHER FETCH ---

    // --- FORECAST FETCH ---

    /**
     * Fetches 5-day weather forecast for a given city name.
     * Updates LiveData for forecast, loading state, and errors.
     */
    fun fetchForecast(cityName: String) {
        // Do not set _isLoading here as current weather fetch usually accompanies forecast fetch
        // and manages the primary loading indicator.
        _errorState.value = ErrorState.NoError
        viewModelScope.launch {
            val result = weatherRepository.getWeatherForecast(cityName)
            processForecastResult(result)
        }
    }

    /**
     * Fetches 5-day weather forecast for given latitude and longitude.
     * Updates LiveData for forecast, loading state, and errors.
     */
    fun fetchForecastByCoordinates(latitude: Double, longitude: Double) {
        _errorState.value = ErrorState.NoError
        viewModelScope.launch {
            val result = weatherRepository.getWeatherForecastByCoords(latitude, longitude)
            processForecastResult(result)
        }
    }
    // --- END FORECAST FETCH ---

    // --- RESULT PROCESSING HELPERS ---

    /**
     * Processes the Result from fetching current weather data.
     * Updates _weatherData or _errorState LiveData based on success or failure.
     * Sets _isLoading to false.
     */
    private fun processWeatherResult(result: Result<WeatherDataResponse>) {
        _isLoading.postValue(false) // Ensure loading is stopped regardless of outcome
        result.fold(
            onSuccess = { data ->
                if (data.cod == 200) { // HTTP 200 OK
                    _weatherData.postValue(data)
                    _errorState.postValue(ErrorState.NoError) // Clear any previous error
                } else {
                    // Handle cases where API returns non-200 but not as an exception (e.g. some API specific error code)
                    _errorState.postValue(ErrorState.ApiError(data.cod, data.errorMessage ?: "Unknown API error"))
                }
            },
            onFailure = { exception ->
                handleGeneralError(exception, "current weather")
            }
        )
    }

    /**
     * Processes the Result from fetching forecast data.
     * Updates _groupedForecastData or _errorState LiveData based on success or failure.
     */
    private fun processForecastResult(result: Result<ForecastResponse>) {
        result.fold(
            onSuccess = { data ->
                if (data.cod == "200" && data.items != null) { // HTTP 200 OK (string for forecast) and data exists
                    _groupedForecastData.postValue(groupForecastItemsByDay(data.items, data.city?.timezone))
                    // Assuming if forecast is successful, any previous general error can be cleared or specific forecast error handled.
                    // _errorState.postValue(ErrorState.NoError) // Avoid clearing a more important current weather error.
                } else {
                    // API returned non-"200" or items list is null
                    val message = data.items?.let { "Forecast data incomplete." } ?: "Failed to retrieve forecast data from API."
                    _errorState.postValue(ErrorState.ApiError(data.cod?.toIntOrNull(), message))
                    _groupedForecastData.postValue(emptyList()) // Clear forecast on error
                }
            },
            onFailure = { exception ->
                handleGeneralError(exception, "forecast")
                _groupedForecastData.postValue(emptyList()) // Clear forecast on error
            }
        )
    }

    /**
     * Groups a list of ForecastListItems into a list of DailyForecast objects.
     * Each DailyForecast contains hourly items for that day.
     * @param items Raw list of forecast items from the API.
     * @param cityTimezoneOffsetSeconds Offset from UTC for the city, used for correct day grouping.
     * @return A list of DailyForecast objects, limited to 5 days.
     */
    private fun groupForecastItemsByDay(items: List<ForecastListItem>, cityTimezoneOffsetSeconds: Int?): List<DailyForecast> {
        if (items.isEmpty()) return emptyList()

        val dailyForecastMap = linkedMapOf<String, DailyForecast>() // Use LinkedHashMap to maintain insertion order (days)
        val dayFormatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        
        // Adjust the SimpleDateFormat's timezone if the city's timezone offset is available.
        // This ensures that forecast items are grouped into days according to the city's local time,
        // not the device's default timezone.
        cityTimezoneOffsetSeconds?.let {
            val offsetHours = it / 3600
            val offsetMinutes = (Math.abs(it) % 3600) / 60
            val sign = if (offsetHours >= 0) "+" else "-"
            val gmtString = String.format("GMT%s%02d:%02d", sign, Math.abs(offsetHours), offsetMinutes)
            dayFormatter.timeZone = TimeZone.getTimeZone(gmtString)
        }

        items.forEach { item ->
            val date = Date(item.dt * 1000L) // API provides timestamp in seconds
            val dayKey = dayFormatter.format(date) // Format date to a string key (e.g., "Mon, May 20")

            val hourlyItem = HourlyForecastItem(
                dt = item.dt,
                main = item.main,
                weather = item.weather,
                wind = item.wind,
                dtTxt = item.dtTxt
            )

            // Get or create the DailyForecast object for this dayKey
            dailyForecastMap.getOrPut(dayKey) { 
                val calendar = Calendar.getInstance()
                // If city timezone is available, set calendar to GMT to correctly calculate start of day UTC
                // then convert dt (which is UTC) to the city's local day.
                // The dayFormatter already uses city's timezone for the dayKey.
                // For dateMillis (unique ID for the day), use UTC midnight of that day.
                cityTimezoneOffsetSeconds?.let { calendar.timeZone = TimeZone.getTimeZone("GMT") }
                calendar.timeInMillis = item.dt * 1000L
                
                // Normalize to midnight UTC for a consistent daily identifier if needed, 
                // but dayKey from city-local-time formatter is primary for grouping.
                // For DailyForecast.dateMillis, using the first item's raw dt might be simpler if dayKey is unique.
                // Let's use UTC midnight of the day for dateMillis as a stable ID.
                val currentItemCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                currentItemCalendar.timeInMillis = item.dt * 1000L
                currentItemCalendar.set(Calendar.HOUR_OF_DAY, 0)
                currentItemCalendar.set(Calendar.MINUTE, 0)
                currentItemCalendar.set(Calendar.SECOND, 0)
                currentItemCalendar.set(Calendar.MILLISECOND, 0)

                DailyForecast(dateMillis = currentItemCalendar.timeInMillis, dayName = dayKey)
            }.hourlyItems.add(hourlyItem)
        }
        return dailyForecastMap.values.toList().take(5) // Return forecast for up to 5 days
    }

    /**
     * Handles general errors from repository calls.
     * Maps different exception types to specific ErrorState objects for the UI.
     * @param exception The throwable caught.
     * @param context A string describing the operation context (e.g., "current weather", "forecast").
     */
    private fun handleGeneralError(exception: Throwable?, context: String = "weather") {
        Log.e("WeatherViewModel", "Error fetching $context: ${exception?.message}", exception)
        val specificErrorState = when (exception) {
            is OpenWeatherApiException -> {
                // Custom exception from our ApiService, contains parsed API error message.
                ErrorState.ApiError(exception.httpStatusCode, exception.message) // message is apiMessage
            }
            is java.net.UnknownHostException -> ErrorState.NetworkError("No internet connection or server unreachable.")
            is IllegalStateException -> {
                // Check for our specific API key missing message.
                if (exception.message?.contains("API Key is missing or invalid", ignoreCase = true) == true) {
                    ErrorState.ApiKeyConfigError()
                } else {
                    ErrorState.GenericError(exception.localizedMessage ?: "An unexpected state occurred.")
                }
            }
            is io.ktor.client.plugins.ClientRequestException -> {
                // General Ktor client-side error (4xx).
                ErrorState.ClientRequestError(exception.response.status.value, exception.message ?: "Client request error.")
            }
            is kotlinx.serialization.SerializationException -> {
                ErrorState.GenericError("Error parsing data: ${exception.localizedMessage}")
            }
            // Add more specific Ktor exceptions if needed (e.g., ServerResponseException for 5xx)
            else -> ErrorState.GenericError(exception?.localizedMessage ?: "An unknown error occurred.")
        }
        _errorState.postValue(specificErrorState)
    }
    // --- END RESULT PROCESSING HELPERS ---

    // Unused method removed as its functionality is handled by MainActivity's adapter callback.
    // fun updateBackgroundForHourlyForecast(hourlyItem: HourlyForecastItem, cityTimezoneOffsetSeconds: Int?) { ... }

    /**
     * Factory for creating WeatherViewModel instances.
     * Required for ViewModels with constructor parameters.
     */
    @Suppress("UNCHECKED_CAST")
    class WeatherViewModelFactory(private val repository: WeatherRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
                return WeatherViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}