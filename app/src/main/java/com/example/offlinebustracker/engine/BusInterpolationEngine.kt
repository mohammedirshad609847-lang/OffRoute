package com.example.offlinebustracker.engine

import com.example.offlinebustracker.data.BusState
import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.data.SegmentPath
import com.example.offlinebustracker.data.TripStatus
import com.example.offlinebustracker.data.Waypoint
import java.util.Locale
import kotlin.math.sqrt

object BusInterpolationEngine {

    /**
     * Calculates the real-time position and trip status of a bus given a specific timestamp
     * and route schedule.
     *
     * ROAD-FOLLOWING INTERPOLATION:
     * If the route has [SegmentPath] entries with road waypoints for the current segment,
     * the bus position is interpolated along those road points — not a straight line.
     *
     * The algorithm:
     *   1. Determine which segment the bus is in (between stop[i] and stop[i+1]).
     *   2. Compute fraction = elapsed_in_segment / segment_duration  (0.0 → 1.0)
     *   3. Walk the road waypoints of that segment, summing Euclidean distances,
     *      and find the point at total_road_length * fraction.
     *
     * This means: if the bus is 47% through the 15-minute A→B window, it will sit at
     * the point that is 47% of the way along the actual road path from A to B.
     *
     * @param currentTimeSeconds Current time in total seconds from midnight
     * @param route The target bus route
     * @return [BusState] with interpolated lat/lng (road-following), ETA, progress, polyline
     */
    fun calculateBusState(currentTimeSeconds: Long, route: Route): BusState {
        val stops = route.stops
        if (stops.isEmpty()) {
            return BusState(
                currentLat = 0.0, currentLng = 0.0,
                status = TripStatus.NOT_STARTED,
                statusText = "No stops in route",
                lastStopPassed = null, nextStop = null,
                etaToNextSeconds = 0, overallProgressPercent = 0,
                segmentProgressPercent = 0,
                formattedCurrentTime = formatSecondsToHHMMSS(currentTimeSeconds)
            )
        }

        val firstStop = stops.first()
        val lastStop = stops.last()
        val startTimeSec = firstStop.getTimeInSeconds()
        val endTimeSec = lastStop.getTimeInSeconds()
        val formattedTime = formatSecondsToHHMMSS(currentTimeSeconds)

        // Build the full road polyline from all segment paths (for map drawing)
        val fullPolyline = buildFullPolyline(route)

        // ── Case 1: Before departure ────────────────────────────────────────────
        if (currentTimeSeconds < startTimeSec) {
            return BusState(
                currentLat = firstStop.latitude, currentLng = firstStop.longitude,
                status = TripStatus.NOT_STARTED,
                statusText = "Trip not started (Departs at ${firstStop.getFormatted12HourTime()})",
                lastStopPassed = null, nextStop = firstStop,
                etaToNextSeconds = startTimeSec - currentTimeSeconds,
                overallProgressPercent = 0, segmentProgressPercent = 0,
                formattedCurrentTime = formattedTime, roadPolyline = fullPolyline
            )
        }

        // ── Case 2: After arrival ────────────────────────────────────────────────
        if (currentTimeSeconds >= endTimeSec) {
            return BusState(
                currentLat = lastStop.latitude, currentLng = lastStop.longitude,
                status = TripStatus.COMPLETED,
                statusText = "Trip completed (Arrived at ${lastStop.getFormatted12HourTime()})",
                lastStopPassed = lastStop, nextStop = null,
                etaToNextSeconds = 0, overallProgressPercent = 100,
                segmentProgressPercent = 100,
                formattedCurrentTime = formattedTime, roadPolyline = fullPolyline
            )
        }

        // ── Case 3: In transit — find enclosing segment ──────────────────────────
        var segmentIndex = 0
        var prevStop = firstStop
        var nextStop = lastStop

        for (i in 0 until stops.size - 1) {
            val t1 = stops[i].getTimeInSeconds()
            val t2 = stops[i + 1].getTimeInSeconds()
            if (currentTimeSeconds in t1 until t2) {
                prevStop = stops[i]
                nextStop = stops[i + 1]
                segmentIndex = i
                break
            }
        }

        val segStartSec = prevStop.getTimeInSeconds()
        val segEndSec   = nextStop.getTimeInSeconds()
        val segDuration = (segEndSec - segStartSec).coerceAtLeast(1)
        val elapsed     = currentTimeSeconds - segStartSec
        // fraction in [0.0, 1.0]: how far along this segment we are by time
        val fraction    = (elapsed.toDouble() / segDuration.toDouble()).coerceIn(0.0, 1.0)

        // ── Road-following position ───────────────────────────────────────────────
        // Find the SegmentPath for stop[segmentIndex] → stop[segmentIndex+1]
        val segPath = route.segmentPaths?.firstOrNull {
            it.fromStopId == prevStop.id && it.toStopId == nextStop.id
        }

        val (interpolatedLat, interpolatedLng) = if (segPath != null && segPath.roadPoints.size >= 2) {
            // Walk road waypoints proportionally
            interpolateAlongRoadPoints(segPath.roadPoints, fraction)
        } else {
            // Fallback: straight-line lerp (original behaviour, no road data)
            val lat = prevStop.latitude  + fraction * (nextStop.latitude  - prevStop.latitude)
            val lng = prevStop.longitude + fraction * (nextStop.longitude - prevStop.longitude)
            Pair(lat, lng)
        }

        // Overall progress across the whole trip
        val totalDuration = (endTimeSec - startTimeSec).coerceAtLeast(1)
        val totalElapsed  = currentTimeSeconds - startTimeSec
        val overallPct    = ((totalElapsed.toDouble() / totalDuration) * 100).toInt().coerceIn(0, 100)
        val segmentPct    = (fraction * 100).toInt().coerceIn(0, 100)

        return BusState(
            currentLat = interpolatedLat,
            currentLng = interpolatedLng,
            status = TripStatus.IN_TRANSIT,
            statusText = "En route to ${nextStop.name}",
            lastStopPassed = prevStop,
            nextStop = nextStop,
            etaToNextSeconds = segEndSec - currentTimeSeconds,
            overallProgressPercent = overallPct,
            segmentProgressPercent = segmentPct,
            formattedCurrentTime = formattedTime,
            roadPolyline = fullPolyline
        )
    }

    /**
     * Walks a list of road waypoints and returns the lat/lng at [fraction] (0→1)
     * of the total Euclidean arc length.
     *
     * Example: fraction=0.47 returns the point 47% of the way along the road path.
     */
    private fun interpolateAlongRoadPoints(pts: List<Waypoint>, fraction: Double): Pair<Double, Double> {
        if (pts.size == 1) return Pair(pts[0].latitude, pts[0].longitude)

        // 1. Compute segment lengths and total length
        val segLengths = mutableListOf<Double>()
        var totalLen = 0.0
        for (i in 0 until pts.size - 1) {
            val d = euclideanDist(pts[i], pts[i + 1])
            segLengths.add(d)
            totalLen += d
        }
        if (totalLen == 0.0) return Pair(pts.first().latitude, pts.first().longitude)

        // 2. Walk until we reach fraction * totalLen
        val target = fraction * totalLen
        var accumulated = 0.0
        for (i in 0 until segLengths.size) {
            val segLen = segLengths[i]
            if (accumulated + segLen >= target) {
                // Bus is somewhere inside segment i → i+1
                val localFrac = if (segLen == 0.0) 0.0 else (target - accumulated) / segLen
                val lat = pts[i].latitude  + localFrac * (pts[i + 1].latitude  - pts[i].latitude)
                val lng = pts[i].longitude + localFrac * (pts[i + 1].longitude - pts[i].longitude)
                return Pair(lat, lng)
            }
            accumulated += segLen
        }
        // Past end → last point
        return Pair(pts.last().latitude, pts.last().longitude)
    }

    /** Simple Euclidean distance proxy (fine for small coordinate differences) */
    private fun euclideanDist(a: Waypoint, b: Waypoint): Double {
        val dlat = a.latitude  - b.latitude
        val dlng = a.longitude - b.longitude
        return sqrt(dlat * dlat + dlng * dlng)
    }

    /**
     * Assembles the complete road polyline for ALL segments in sequence.
     * Used by the map view to draw the full route as a road-following path.
     * If no road data exists for a segment, a straight line between the two
     * stop coordinates is used instead.
     */
    fun buildFullPolyline(route: Route): List<Waypoint> {
        val stops = route.stops
        if (stops.isEmpty()) return emptyList()
        if (stops.size == 1) return listOf(Waypoint(stops[0].latitude, stops[0].longitude))

        val points = mutableListOf<Waypoint>()
        for (i in 0 until stops.size - 1) {
            val from = stops[i]
            val to   = stops[i + 1]
            val seg  = route.segmentPaths?.firstOrNull { it.fromStopId == from.id && it.toStopId == to.id }
            if (seg != null && seg.roadPoints.isNotEmpty()) {
                // Add road waypoints (skip last point — it will be the start of the next segment)
                seg.roadPoints.dropLast(1).forEach { points.add(it) }
            } else {
                // Straight-line fallback
                points.add(Waypoint(from.latitude, from.longitude))
            }
        }
        // Add the very last stop
        points.add(Waypoint(stops.last().latitude, stops.last().longitude))
        return points
    }

    fun formatSecondsToHHMMSS(seconds: Long): String {
        var h = (seconds / 3600) % 24
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val amPm = if (h >= 12) "PM" else "AM"
        if (h > 12) h -= 12
        if (h == 0L) h = 12
        return String.format(Locale.US, "%02d:%02d:%02d %s", h, m, s, amPm)
    }

    fun formatSecondsToCountdown(seconds: Long): String {
        if (seconds <= 0) return "Arrived"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
