package com.example.myweatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myweatherapp.databinding.ActivityMainBinding
import com.example.myweatherapp.ErrorState // Explicitly import
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import coil3.load // Primary Coil 3 import
import coil3.request.ImageRequest // For building placeholder/error requests
import coil3.request.crossfade
import coil3.asImage // Import for Drawable.asImage()
import androidx.core.content.ContextCompat // To get drawable
import android.content.res.ColorStateList // Added for button styling
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt // Import for rounding
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow // For Math.pow

/**
 * Main activity of the MyWeatherApp.
 * Handles UI interactions, displays weather and forecast data, manages permissions,
 * and listens to device sensors (pressure for altitude).
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    // ViewModel, Repository, and API Service setup
    private val weatherApiService = WeatherApiService()
    private val weatherRepository = WeatherRepository(weatherApiService)
    private val viewModelFactory = WeatherViewModel.WeatherViewModelFactory(weatherRepository)
    private val weatherViewModel: WeatherViewModel by viewModels { viewModelFactory }

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var dailyForecastAdapter: DailyForecastAdapter
    
    // Stores the timezone offset of the currently displayed city for accurate time conversions.
    private var currentCityTimezoneOffset: Int? = null
    // Tracks if the current theme is day or night for dynamic UI styling.
    private var isDayTime: Boolean = true 
    // Stores the name of the last city successfully fetched, used for swipe-to-refresh.
    private var lastFetchedCity: String? = "Greece" // Default city on first launch.

    // --- Sensor related variables for Altitude ---
    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private val seaLevelPressure = 1013.25f // Standard sea-level pressure in hPa for altitude calculation.

    // ActivityResultLauncher for handling location permission requests.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                getCurrentLocationAndFetchWeather()
            } else {
                // User denied permission; display a message.
                binding.errorTextView.text = "Location permission denied."
                binding.errorTextView.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables drawing behind system bars for a modern look.
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setupRecyclerView()
        setupUIListeners()
        setupObservers()
        setupSwipeToRefresh()
        setupSensorServices() // Initialize and check for pressure sensor.

        // Initial data fetch on app start.
        // Fetches weather for the last known city or the default city.
        lastFetchedCity?.let {
            weatherViewModel.fetchWeather(it)
            weatherViewModel.fetchForecast(it)
        } 

        // Adjust padding to accommodate system bars (status bar, navigation bar).
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Sets up the RecyclerView for displaying daily forecasts.
     * Initializes the DailyForecastAdapter and provides a callback for hourly item clicks.
     */
    private fun setupRecyclerView() {
        dailyForecastAdapter = DailyForecastAdapter ({
            // When an hourly item is clicked, update the main background to reflect that item's time.
            hourlyItem, cityTimezoneOffset ->
            updateBackgroundForTime(hourlyItem.dt, cityTimezoneOffset ?: currentCityTimezoneOffset)
        }, currentCityTimezoneOffset) 

        binding.forecastRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = dailyForecastAdapter
        }
    }

    /**
     * Updates the main background gradient (day/night) and text colors based on a given timestamp and timezone.
     * @param timestampSeconds The epoch timestamp (in seconds) for the time to represent.
     * @param timezoneOffsetSeconds The timezone offset (in seconds) for the location.
     */
    private fun updateBackgroundForTime(timestampSeconds: Long, timezoneOffsetSeconds: Int?) {
        val calendar = Calendar.getInstance()
        // Adjust calendar to the city's local time using the provided timezone offset.
        timezoneOffsetSeconds?.let {
            val offsetHours = it / 3600
            val offsetMinutes = (Math.abs(it) % 3600) / 60
            val sign = if (offsetHours >= 0) "+" else "-"
            val gmtString = String.format("GMT%s%02d:%02d", sign, Math.abs(offsetHours), offsetMinutes)
            calendar.timeZone = TimeZone.getTimeZone(gmtString)
        } ?: run {
            calendar.timeZone = TimeZone.getDefault() // Fallback to device default if no offset.
        }
        calendar.timeInMillis = timestampSeconds * 1000L
        
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        // Determine if it's day or night based on the hour.
        if (hourOfDay >= 6 && hourOfDay < 18) { // 6 AM to 5:59 PM is considered day.
            binding.main.setBackgroundResource(R.drawable.gradient_day)
            isDayTime = true
        } else {
            binding.main.setBackgroundResource(R.drawable.gradient_night)
            isDayTime = false
        }
        updateTextColorsForTheme() // Apply corresponding text colors.
    }

    /**
     * Updates text colors of various UI elements based on the current day/night theme (isDayTime flag).
     * Also updates button styles (stroke, text, icon color) and EditText colors.
     * Propagates the theme change to the forecast RecyclerView adapter.
     */
    private fun updateTextColorsForTheme() {
        val primaryColor = ContextCompat.getColor(this, if (isDayTime) R.color.text_color_day else R.color.text_color_night)
        val secondaryColor = ContextCompat.getColor(this, if (isDayTime) R.color.text_color_secondary_day else R.color.text_color_secondary_night)

        // Update main weather information TextViews.
        binding.cityTextView.setTextColor(primaryColor)
        binding.temperatureTextView.setTextColor(primaryColor)
        binding.altitudeTextView.setTextColor(secondaryColor) 
        binding.conditionTextView.setTextColor(secondaryColor)
        binding.windTextView.setTextColor(secondaryColor)
        binding.sunriseTextView.setTextColor(secondaryColor)
        binding.sunsetTextView.setTextColor(secondaryColor)
        binding.lastUpdatedTextView.setTextColor(secondaryColor)

        // Update button colors (stroke, text, icon).
        val buttonStrokeColorRes = if (isDayTime) R.color.button_stroke_day else R.color.button_stroke_night
        val buttonTextColorRes = if (isDayTime) R.color.button_text_day else R.color.button_text_night
        val buttonIconColorRes = if (isDayTime) R.color.button_icon_day else R.color.button_icon_night

        val buttonStrokeColor = ContextCompat.getColor(this, buttonStrokeColorRes)
        val buttonTextColor = ContextCompat.getColor(this, buttonTextColorRes)
        val buttonIconColor = ContextCompat.getColor(this, buttonIconColorRes)

        binding.searchWeatherButton.strokeColor = ColorStateList.valueOf(buttonStrokeColor)
        binding.searchWeatherButton.setTextColor(buttonTextColor)

        binding.myLocationButton.strokeColor = ColorStateList.valueOf(buttonStrokeColor)
        binding.myLocationButton.setTextColor(buttonTextColor)
        binding.myLocationButton.iconTint = ColorStateList.valueOf(buttonIconColor)
        
        // Update EditText text and hint colors for better contrast.
        val editTextTextColor = ContextCompat.getColor(this, if (isDayTime) R.color.text_color_day else R.color.text_color_night)
        val editTextHintColor = ContextCompat.getColor(this, if (isDayTime) R.color.text_color_secondary_day else R.color.text_color_secondary_night)
        binding.cityInputEditText.setTextColor(editTextTextColor)
        binding.cityInputEditText.setHintTextColor(editTextHintColor)

        // Update theme for items in the forecast RecyclerView.
        if (::dailyForecastAdapter.isInitialized) {
            dailyForecastAdapter.updateTheme(isDayTime)
        }
    }

    /**
     * Sets up listeners for UI elements like buttons.
     */
    private fun setupUIListeners() {
        binding.searchWeatherButton.setOnClickListener {
            binding.errorTextView.visibility = View.GONE // Clear previous error before new search.
            val cityName = binding.cityInputEditText.text.toString().trim()
            if (cityName.isNotEmpty()) {
                lastFetchedCity = cityName // Store city for potential refresh.
                weatherViewModel.fetchWeather(cityName)
                weatherViewModel.fetchForecast(cityName)
            } else {
                binding.errorTextView.text = "Please enter a city name."
                binding.errorTextView.visibility = View.VISIBLE
            }
        }
        binding.myLocationButton.setOnClickListener {
            binding.errorTextView.visibility = View.GONE // Clear previous error.
            lastFetchedCity = null // Clear stored city name as we are using location now.
            checkLocationPermissionAndFetch() // Request permission if needed, then fetch weather.
        }
    }

    /**
     * Checks for location permission and initiates fetching weather data if permission is granted.
     * Launches the permission request flow if permission is not granted.
     */
    private fun checkLocationPermissionAndFetch() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted.
                getCurrentLocationAndFetchWeather()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                // Explain to the user why permission is needed, then request again.
                // (Currently, just re-requesting directly via launcher).
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            else -> {
                // Permission has not been asked yet or was denied with "Don't ask again".
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    /**
     * Fetches the current device location using FusedLocationProviderClient and then triggers weather data fetch.
     * Requires ACCESS_COARSE_LOCATION permission to be granted.
     */
    private fun getCurrentLocationAndFetchWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This check is a safeguard; permission should ideally be confirmed before calling this.
            binding.errorTextView.text = "Location permission required to use this feature."
            binding.errorTextView.visibility = View.VISIBLE
            return
        }
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingIndicator.playAnimation()
        binding.errorTextView.visibility = View.GONE // Clear error before fetching.
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    weatherViewModel.fetchWeatherByCoordinates(location.latitude, location.longitude)
                    weatherViewModel.fetchForecastByCoordinates(location.latitude, location.longitude)
                    binding.cityInputEditText.setText("") // Clear city input field.
                    lastFetchedCity = null // Using coordinates, not city name for refresh context.
                } else {
                    binding.loadingIndicator.visibility = View.GONE
                    binding.loadingIndicator.cancelAnimation()
                    binding.errorTextView.text = "Could not retrieve current location. Please try searching by city name."
                    binding.errorTextView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                binding.loadingIndicator.visibility = View.GONE
                binding.loadingIndicator.cancelAnimation()
                binding.errorTextView.text = "Failed to get location: ${e.localizedMessage}"
                binding.errorTextView.visibility = View.VISIBLE
            }
    }

    /**
     * Sets up observers for LiveData from the WeatherViewModel.
     * Updates the UI when weather data, forecast data, error states, or loading states change.
     */
    private fun setupObservers() {
        // Observer for current weather data.
        weatherViewModel.weatherData.observe(this, Observer { weatherResponse ->
            binding.loadingIndicator.visibility = View.GONE // Hide loading indicator once data (or null) arrives.
            binding.loadingIndicator.cancelAnimation()
            if (weatherResponse != null && weatherResponse.cod == 200) {
                // Successfully fetched weather data.
                binding.errorTextView.visibility = View.GONE // Clear any previous errors.
                binding.cityTextView.text = weatherResponse.name ?: "N/A"

                // Format and display temperature in Celsius and Fahrenheit.
                weatherResponse.main?.temp?.let { celsius ->
                    val celsiusInt = celsius.roundToInt()
                    val fahrenheitInt = (celsius * 9/5 + 32).roundToInt()
                    binding.temperatureTextView.text = "${celsiusInt}°C / ${fahrenheitInt}°F"
                } ?: run {
                    binding.temperatureTextView.text = "--°C / --°F"
                }

                val condition = weatherResponse.weather?.firstOrNull()
                binding.conditionTextView.text = condition?.description?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "N/A"
                
                // Display Lottie animation for current weather condition.
                AppConstants.getLottieAnimationForIcon(condition?.icon)?.let {
                    binding.currentWeatherLottieView.setAnimation(it)
                    binding.currentWeatherLottieView.playAnimation()
                    binding.currentWeatherLottieView.visibility = View.VISIBLE
                } ?: run { binding.currentWeatherLottieView.visibility = View.GONE }

                currentCityTimezoneOffset = weatherResponse.timezone
                dailyForecastAdapter.updateCityTimezoneOffset(currentCityTimezoneOffset) // Update adapter with new timezone.

                // Update background and text colors based on current time at the fetched city's location.
                updateBackgroundForTime(System.currentTimeMillis() / 1000, currentCityTimezoneOffset)
                
                // Display sunrise, sunset, wind, and last updated time.
                binding.sunriseTextView.text = "Sunrise: ${AppConstants.formatUnixTimestampToTime(weatherResponse.sys?.sunrise, currentCityTimezoneOffset)}"
                binding.sunsetTextView.text = "Sunset: ${AppConstants.formatUnixTimestampToTime(weatherResponse.sys?.sunset, currentCityTimezoneOffset)}"
                binding.windTextView.text = AppConstants.formatWind(weatherResponse.wind?.speed, weatherResponse.wind?.deg)
                val lastUpdatedSdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                binding.lastUpdatedTextView.text = "Last updated: ${lastUpdatedSdf.format(Date())}" // Uses device local time for "last updated".
            } else {
                // Weather data is null or API returned an error code (e.g. 401, 404 handled via OpenWeatherApiException -> ErrorState.ApiError).
                // The errorState observer will handle displaying the specific error message.
                // Here, we just ensure the UI is cleared of any stale weather data.
                Log.d("MainActivity", "WeatherData observer: Response is null or not cod 200. Clearing UI.")
                clearWeatherUI()
            }
        })

        // Observer for grouped forecast data.
        weatherViewModel.groupedForecastData.observe(this, Observer { groupedData ->
            if (groupedData != null) {
                dailyForecastAdapter.submitList(groupedData)
            } else {
                // If forecast data is null (e.g., after an error), clear the list.
                dailyForecastAdapter.submitList(emptyList())
            }
        })

        // Observer for error states from the ViewModel.
        weatherViewModel.errorState.observe(this, Observer { errorState ->
            if (errorState != null && errorState !is ErrorState.NoError) {
                binding.loadingIndicator.visibility = View.GONE // Ensure loading is hidden on error.
                val message = when (errorState) {
                    is ErrorState.ApiKeyConfigError -> errorState.message
                    is ErrorState.NetworkError -> errorState.message
                    is ErrorState.ApiError -> errorState.displayMessage // User-friendly message from API or ViewModel.
                    is ErrorState.ClientRequestError -> errorState.displayMessage
                    is ErrorState.GenericError -> errorState.displayMessage
                    is ErrorState.NoError -> null // Should not happen in this block.
                }
                if (message != null) {
                    binding.errorTextView.text = message
                    binding.errorTextView.visibility = View.VISIBLE
                }
            } else if (weatherViewModel.weatherData.value?.cod != 200 && errorState is ErrorState.NoError) {
                // This case handles if an error was cleared but weatherData is still not valid.
                // It prevents hiding an error if weatherData is still bad but errorState was reset.
                // However, with current flow, errors should persist in errorState until explicitly NoError on success.
                // Generally, errorTextView visibility should be primarily driven by non-NoError states.
            } else {
                 // If ErrorState is NoError and weatherData is presumably good (or about to be).
                 binding.errorTextView.visibility = View.GONE
            }
        })

        // Observer for loading state.
        weatherViewModel.isLoading.observe(this, Observer { isLoading ->
            if (isLoading) {
                // Show loading indicator only if SwipeRefreshLayout is not already refreshing.
                if (!binding.swipeRefreshLayout.isRefreshing) {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.loadingIndicator.playAnimation()
                }
                // Disable input fields and buttons during loading.
                binding.searchWeatherButton.isEnabled = false
                binding.cityInputEditText.isEnabled = false
                binding.myLocationButton.isEnabled = false
            } else {
                binding.loadingIndicator.visibility = View.GONE
                binding.loadingIndicator.cancelAnimation()
                binding.swipeRefreshLayout.isRefreshing = false // Stop swipe-to-refresh animation.
                // Re-enable input fields and buttons.
                binding.searchWeatherButton.isEnabled = true
                binding.cityInputEditText.isEnabled = true
                binding.myLocationButton.isEnabled = true
            }
        })
    }

    /**
     * Sets up the SwipeRefreshLayout for pull-to-refresh functionality.
     */
    private fun setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.errorTextView.visibility = View.GONE // Hide error before refresh.
            if (lastFetchedCity != null) {
                // Refresh using the last successfully searched city name.
                weatherViewModel.fetchWeather(lastFetchedCity!!)
                weatherViewModel.fetchForecast(lastFetchedCity!!)
            } else {
                // If no city was searched (e.g., app started with location or cleared search),
                // refresh by trying to get current location again.
                checkLocationPermissionAndFetch() 
            }
            // isLoading LiveData observer will set swipeRefreshLayout.isRefreshing = false when done.
        }
    }
    
    /**
     * Initializes sensor-related services, specifically for the pressure sensor (altimeter).
     */
    private fun setupSensorServices() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            // Device does not have a pressure sensor.
            binding.altitudeTextView.text = "Altitude: N/A (No sensor)"
            binding.altitudeTextView.visibility = View.VISIBLE 
        } else {
            // Sensor found; it will be registered in onResume.
            // Set initial text, actual readings will update in onSensorChanged.
            binding.altitudeTextView.visibility = View.VISIBLE 
            binding.altitudeTextView.text = "Altitude: Reading..."
        }
    }

    /**
     * Clears all weather-related UI elements, typically after an error or before new data load.
     * Resets text fields to placeholders and hides dynamic elements.
     */
    private fun clearWeatherUI() {
        binding.cityTextView.text = "---"
        binding.temperatureTextView.text = "--°C / --°F"
        binding.altitudeTextView.text = if (pressureSensor != null) "Altitude: Reading..." else "Altitude: N/A"
        binding.conditionTextView.text = "N/A"
        binding.currentWeatherLottieView.visibility = View.GONE
        binding.currentWeatherLottieView.cancelAnimation()
        binding.sunriseTextView.text = "Sunrise: N/A"
        binding.sunsetTextView.text = "Sunset: N/A"
        binding.windTextView.text = "WIND:"
        binding.lastUpdatedTextView.text = "Last updated: N/A"
        
        // Default to day theme visuals when clearing UI.
        isDayTime = true 
        binding.main.setBackgroundResource(R.drawable.gradient_day)
        updateTextColorsForTheme() // Apply day theme colors.
        
        dailyForecastAdapter.submitList(emptyList()) // Clear forecast list.
    }

    // --- SensorEventListener methods for Altitude --- 
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for this implementation, but required by SensorEventListener.
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val currentPressureHpa = event.values[0]
            // Calculate altitude using the barometric formula.
            // Altitude (meters) = 44330.0 * (1.0 - (P / P0)^(1/5.255))
            // P0 = Standard sea-level pressure (1013.25 hPa).
            // P = Current atmospheric pressure from sensor (in hPa).
            val altitude = 44330.0 * (1.0 - (currentPressureHpa / seaLevelPressure).toDouble().pow(1.0 / 5.255))
            binding.altitudeTextView.text = String.format(Locale.getDefault(), "Altitude: %.0f m", altitude)
            binding.altitudeTextView.visibility = View.VISIBLE // Ensure it's visible when readings come.
        }
    }

    // --- Activity Lifecycle methods for Sensor Management ---
    override fun onResume() {
        super.onResume()
        // Register pressure sensor listener if sensor is available.
        pressureSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensor listener to save battery when activity is not active.
        sensorManager.unregisterListener(this)
    }
}