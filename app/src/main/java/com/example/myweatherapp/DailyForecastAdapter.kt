package com.example.myweatherapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myweatherapp.databinding.ItemDailyForecastBinding
import androidx.core.content.ContextCompat

/**
 * Adapter for the main RecyclerView in MainActivity, displaying a list of daily forecasts.
 * Each item in this list (a day) can be expanded to show an inner RecyclerView of hourly forecasts.
 * Handles dynamic theme updates for its items (day/night text and icon colors).
 */
class DailyForecastAdapter(
    // Callback invoked when an hourly forecast item within a daily item is clicked.
    private val onHourlyItemClicked: (HourlyForecastItem, cityTimezoneOffset: Int?) -> Unit,
    // The timezone offset of the currently displayed city, used for hourly items.
    private var cityTimezoneOffset: Int? 
) : ListAdapter<DailyForecast, DailyForecastAdapter.DailyViewHolder>(DailyDiffCallback()) {

    // Tracks the current theme (day or night) to style items accordingly.
    private var isDayTime: Boolean = true

    /**
     * Updates the city's timezone offset. This can be used by child hourly adapters if needed.
     */
    fun updateCityTimezoneOffset(offset: Int?) {
        cityTimezoneOffset = offset
        // If timezone directly affected daily item display (it doesn't currently),
        // we might notify item changes here.
    }

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailyViewHolder {
        val binding = ItemDailyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Pass the current dayTime state to the ViewHolder, which in turn passes it to the HourlyForecastAdapter.
        return DailyViewHolder(binding, onHourlyItemClicked, cityTimezoneOffset, isDayTime)
    }

    override fun onBindViewHolder(holder: DailyViewHolder, position: Int) {
        // Bind data to the ViewHolder, also passing the current dayTime state for styling.
        holder.bind(getItem(position), isDayTime)
    }

    /**
     * ViewHolder for individual daily forecast items.
     * Manages the display of the day's date and the nested RecyclerView for hourly forecasts.
     */
    inner class DailyViewHolder(
        private val binding: ItemDailyForecastBinding,
        private val onHourlyItemClickedCallback: (HourlyForecastItem, cityTimezoneOffset: Int?) -> Unit,
        private val cityTimezoneOffsetForHourly: Int?,
        private var isViewHolderDayTime: Boolean // Initial dayTime state from parent adapter.
    ) : RecyclerView.ViewHolder(binding.root) {

        // Each daily item has its own HourlyForecastAdapter for its nested list.
        private val hourlyAdapter = HourlyForecastAdapter(onHourlyItemClickedCallback, cityTimezoneOffsetForHourly, isViewHolderDayTime)

        init {
            binding.hourlyForecastRecyclerView.layoutManager = LinearLayoutManager(binding.root.context)
            binding.hourlyForecastRecyclerView.adapter = hourlyAdapter
        }

        /**
         * Binds a DailyForecast data object to the ViewHolder views.
         * @param dailyForecast The data for the day.
         * @param currentIsDayTime The current theme state (day/night) for styling.
         */
        fun bind(dailyForecast: DailyForecast, currentIsDayTime: Boolean) {
            // Update the theme for the nested hourly adapter.
            this.isViewHolderDayTime = currentIsDayTime 
            hourlyAdapter.updateTheme(currentIsDayTime)

            binding.dailyDateTextView.text = dailyForecast.dayName
            // Set text color based on the current theme (day/night).
            val textColor = ContextCompat.getColor(itemView.context, if (currentIsDayTime) R.color.text_color_day else R.color.text_color_night)
            binding.dailyDateTextView.setTextColor(textColor)

            // Update expand/collapse icon color based on theme.
            val iconColor = ContextCompat.getColor(itemView.context, if (currentIsDayTime) R.color.button_icon_day else R.color.button_icon_night)
            binding.expandIconImageView.setColorFilter(iconColor)

            hourlyAdapter.submitList(dailyForecast.hourlyItems) // Populate the nested RecyclerView.

            // Manage visibility and icon for the expandable hourly forecast section.
            binding.hourlyForecastRecyclerView.visibility = if (dailyForecast.isExpanded) View.VISIBLE else View.GONE
            binding.expandIconImageView.setImageResource(
                if (dailyForecast.isExpanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
            )
            // Ensure icon color is applied after changing the source drawable.
            binding.expandIconImageView.setColorFilter(iconColor) 

            // Toggle expansion state on item click.
            itemView.setOnClickListener {
                dailyForecast.isExpanded = !dailyForecast.isExpanded
                notifyItemChanged(adapterPosition) // Efficiently update only this item.
            }
        }
    }

    /**
     * DiffUtil.ItemCallback for calculating the difference between two non-null items in a list.
     * Used by ListAdapter to efficiently update the RecyclerView.
     */
    class DailyDiffCallback : DiffUtil.ItemCallback<DailyForecast>() {
        override fun areItemsTheSame(oldItem: DailyForecast, newItem: DailyForecast): Boolean {
            // Days are considered the same if their identifying dateMillis match.
            return oldItem.dateMillis == newItem.dateMillis
        }

        override fun areContentsTheSame(oldItem: DailyForecast, newItem: DailyForecast): Boolean {
            // Content is the same if all fields (including isExpanded) match. Relies on DailyForecast being a data class.
            return oldItem == newItem 
        }
    }
} 