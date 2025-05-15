package com.example.myweatherapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myweatherapp.databinding.ItemHourlyForecastBinding
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Adapter for the nested RecyclerView within each DailyForecast item.
 * Displays individual hourly forecast details (time, icon, condition, temperature).
 * Handles dynamic theme updates for its items (day/night text colors).
 */
class HourlyForecastAdapter(
    // Callback invoked when an hourly item is clicked.
    private val onItemClicked: (HourlyForecastItem, cityTimezoneOffset: Int?) -> Unit,
    // The timezone offset of the city, for displaying time correctly.
    private val cityTimezoneOffset: Int?,
    // Initial theme state (day/night) passed from the parent DailyForecastAdapter.
    private var isDayTime: Boolean 
) : ListAdapter<HourlyForecastItem, HourlyForecastAdapter.HourlyViewHolder>(HourlyDiffCallback()) {

    /**
     * Updates the theme for the adapter and its items.
     * @param newIsDayTime True if day theme, false if night theme.
     * Notifies the adapter to rebind all visible items if the theme has changed.
     */
    fun updateTheme(newIsDayTime: Boolean) {
        val oldIsDayTime = isDayTime
        isDayTime = newIsDayTime
        if (oldIsDayTime != newIsDayTime) {
            notifyDataSetChanged() // Rebind all visible items to apply new theme colors.
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyViewHolder {
        val binding = ItemHourlyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HourlyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HourlyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for individual hourly forecast items.
     * Manages the display of time, Lottie animation, weather condition, and temperature.
     */
    inner class HourlyViewHolder(private val binding: ItemHourlyForecastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        /**
         * Binds an HourlyForecastItem data object to the ViewHolder views.
         * @param item The hourly forecast data item.
         */
        fun bind(item: HourlyForecastItem) {
            val context = itemView.context
            // Determine text colors based on the current theme (day/night).
            val primaryColor = ContextCompat.getColor(context, if (isDayTime) R.color.text_color_day else R.color.text_color_night)
            val secondaryColor = ContextCompat.getColor(context, if (isDayTime) R.color.text_color_secondary_day else R.color.text_color_secondary_night)

            // Temperature display (both Celsius and Fahrenheit).
            item.main.temp?.let {
                val celsiusInt = it.roundToInt()
                val fahrenheitInt = (it * 9/5 + 32).roundToInt()
                binding.hourlyTempTextView.text = "${celsiusInt}°C / ${fahrenheitInt}°F"
            } ?: run {
                binding.hourlyTempTextView.text = "--°C / --°F"
            }
            binding.hourlyTempTextView.setTextColor(primaryColor)

            // Weather condition text.
            binding.hourlyConditionTextView.text =
                item.weather.firstOrNull()?.description?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "N/A"
            binding.hourlyConditionTextView.setTextColor(secondaryColor)
            
            // Format and display the time for the forecast item.
            // Uses the city's specific timezone offset if available for accurate local time.
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            cityTimezoneOffset?.let {
                val offsetHours = it / 3600
                val offsetMinutes = (Math.abs(it) % 3600) / 60
                val sign = if (offsetHours >= 0) "+" else "-"
                val gmtString = String.format("GMT%s%02d:%02d", sign, Math.abs(offsetHours), offsetMinutes)
                sdf.timeZone = TimeZone.getTimeZone(gmtString)
            } ?: sdf.setTimeZone(TimeZone.getDefault()) // Fallback to device default timezone.
            binding.hourlyTimeTextView.text = sdf.format(Date(item.dt * 1000L))
            binding.hourlyTimeTextView.setTextColor(secondaryColor) // Time is also considered secondary information here.

            // Load and play Lottie animation for the weather icon.
            AppConstants.getLottieAnimationForIcon(item.weather.firstOrNull()?.icon)?.let {
                binding.hourlyLottieView.setAnimation(it)
                binding.hourlyLottieView.playAnimation()
                binding.hourlyLottieView.visibility = View.VISIBLE
            } ?: run {
                binding.hourlyLottieView.visibility = View.GONE
            }

            // Set click listener to invoke the callback passed from MainActivity.
            itemView.setOnClickListener {
                onItemClicked(item, cityTimezoneOffset)
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for efficiently updating the list of hourly forecast items.
     */
    class HourlyDiffCallback : DiffUtil.ItemCallback<HourlyForecastItem>() {
        override fun areItemsTheSame(oldItem: HourlyForecastItem, newItem: HourlyForecastItem): Boolean {
            // Items are the same if their timestamp (dt) matches.
            return oldItem.dt == newItem.dt
        }

        override fun areContentsTheSame(oldItem: HourlyForecastItem, newItem: HourlyForecastItem): Boolean {
            // Content is the same if all fields match (relies on HourlyForecastItem being a data class).
            return oldItem == newItem
        }
    }
} 