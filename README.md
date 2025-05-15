# MyWeatherApp

## Overview

**MyWeatherApp** is a feature-rich Android weather application built with Kotlin. It provides accurate current weather and a 5-day forecast for any city you search or your current GPS location. It also shows altitude based on the device’s barometric sensor and uses engaging Lottie animations for weather conditions.

---

## Features

- Current weather: city name, temperature (°C/°F), weather description, wind speed with directional arrow, sunrise & sunset, and last updated time.
- Expandable 5-day forecast with hourly details.
- Dynamic UI theme that switches between day and night based on the current time.
- Location-based weather fetching using GPS.
- Altitude display from the device's pressure sensor.
- Swipe-to-refresh to update weather data.
- Robust error handling for invalid inputs, network issues, and missing API keys.
- Uses OpenWeatherMap API with Kotlin Coroutines and Ktor for networking.
- Lottie animations to visualize weather instead of static icons.

---

## Preview

### Screenshots

<!-- Replace the image URLs below with your actual screenshots -->
![Main Screen](path/to/screenshot1.png)  
*Main weather display with current weather details*

![5-Day Forecast](path/to/screenshot2.png)  
*Expandable 5-day forecast showing hourly details*

---

### GIFs

<!-- Replace the GIF URLs below with your actual GIFs -->
![Weather Animation](path/to/animation1.gif)  
*Lottie animation for rainy weather*

---

## How to Install

Download the latest APK from the [Releases](link-to-releases-page) section and install it on your Android device.

---

## Technologies Used

- Kotlin
- Android Architecture Components (ViewModel, LiveData)
- Ktor (Networking)
- kotlinx.serialization (JSON parsing)
- Coil 3 (Lottie animation loading)
- FusedLocationProviderClient (Location services)
- SensorManager (Pressure sensor)
- OpenWeatherMap API

---
