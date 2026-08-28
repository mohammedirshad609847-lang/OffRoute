package com.example.offlinebustracker.data

/**
 * Represents a scheduled bus stop on the route.
 * @param id Unique identifier for the stop
 * @param name Display name of the stop
 * @param latitude Latitude coordinate
 * @param longitude Longitude coordinate
 * @param scheduledTime Time string in HH:mm format (24-hour clock, e.g. "17:55")
 */
data class BusStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val scheduledTime: String
) {
    /**
     * Converts the scheduledTime string "HH:mm" into total seconds from midnight.
     * Example: "17:55" -> (17 * 3600) + (55 * 60) = 64500 seconds.
     */
    fun getTimeInSeconds(): Long {
        val parts = scheduledTime.split(":")
        if (parts.size != 2) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        return hours * 3600 + minutes * 60
    }

    /**
     * Returns the time in 12-hour format (e.g. "05:55 PM").
     */
    fun getFormatted12HourTime(): String {
        val parts = scheduledTime.split(":")
        if (parts.size != 2) return scheduledTime
        var h = parts[0].toIntOrNull() ?: return scheduledTime
        val mStr = parts[1]
        val amPm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0) h = 12
        return String.format(java.util.Locale.US, "%02d:%s %s", h, mStr, amPm)
    }
}
