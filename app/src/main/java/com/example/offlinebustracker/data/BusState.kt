package com.example.offlinebustracker.data

/**
 * Encapsulates the current interpolated state of the bus at a specific timestamp.
 * [roadPolyline] is the full ordered list of road-following lat/lng points for the
 * active map polyline overlay (all segments concatenated).
 */
enum class TripStatus {
    NOT_STARTED,
    IN_TRANSIT,
    COMPLETED
}

data class BusState(
    val currentLat: Double,
    val currentLng: Double,
    val status: TripStatus,
    val statusText: String,
    val lastStopPassed: BusStop?,
    val nextStop: BusStop?,
    val etaToNextSeconds: Long,
    val overallProgressPercent: Int,
    val segmentProgressPercent: Int,
    val formattedCurrentTime: String,
    // Full road polyline for the active route (used by map view to draw road-following path)
    val roadPolyline: List<Waypoint> = emptyList()
)
