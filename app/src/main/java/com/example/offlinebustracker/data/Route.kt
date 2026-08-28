package com.example.offlinebustracker.data

data class Waypoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * A road-following path segment between two consecutive stops.
 * [roadPoints] contains the ordered lat/lng waypoints along the actual road
 * from stop[i] to stop[i+1], fetched from OSRM or manually defined.
 * If empty, interpolation falls back to a straight line between the two stops.
 */
data class SegmentPath(
    val fromStopId: String,
    val toStopId: String,
    val roadPoints: List<Waypoint> = emptyList()
)

/**
 * Represents a complete bus route with bus metadata, destination, ordered stops,
 * and per-segment road-following paths for smooth realistic animation.
 */
data class Route(
    val id: String,
    val name: String,
    val busNumber: String = "BUS-101",
    val destination: String = "North Terminal Depot",
    val stops: List<BusStop>,
    // Legacy flat waypoints kept for JSON compat, prefer segmentPaths
    val waypoints: List<Waypoint> = emptyList(),
    // Per-segment road paths (stop[i] → stop[i+1])
    val segmentPaths: List<SegmentPath> = emptyList()
)
