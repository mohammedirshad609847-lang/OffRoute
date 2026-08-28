package com.example.offlinebustracker.engine

import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.data.SegmentPath
import com.example.offlinebustracker.data.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.delay

object OsrmRouteFetcher {
    private const val OSRM_BASE = "https://router.project-osrm.org/route/v1/driving/"

    suspend fun enrichRouteWithRoadPaths(route: Route): Route = withContext(Dispatchers.IO) {
        val stops = route.stops
        if (stops.size < 2) return@withContext route

        // 1. Build coordinate string for the entire route
        val coordString = stops.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "${OSRM_BASE}${coordString}?overview=false&geometries=geojson&steps=true"

        val segPaths = mutableListOf<SegmentPath>()

        try {
            val urlObj = URL(url)
            val connection = urlObj.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "OfflineBusTracker/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                println("OSRM Error: HTTP ${connection.responseCode}")
                return@withContext route
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val routesArray = root.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) return@withContext route

            val legsArray = routesArray.getJSONObject(0).optJSONArray("legs")
            if (legsArray == null || legsArray.length() != stops.size - 1) return@withContext route

            // 2. Parse each leg into a SegmentPath
            for (i in 0 until legsArray.length()) {
                val leg = legsArray.getJSONObject(i)
                val steps = leg.optJSONArray("steps") ?: continue
                
                val roadPoints = mutableListOf<Waypoint>()
                
                for (j in 0 until steps.length()) {
                    val step = steps.getJSONObject(j)
                    val geometry = step.optJSONObject("geometry") ?: continue
                    val coords = geometry.optJSONArray("coordinates") ?: continue
                    
                    for (k in 0 until coords.length()) {
                        val pair = coords.getJSONArray(k)
                        val lng = pair.getDouble(0)
                        val lat = pair.getDouble(1)
                        // Avoid consecutive duplicate points (common between steps)
                        if (roadPoints.isEmpty() || roadPoints.last().latitude != lat || roadPoints.last().longitude != lng) {
                            roadPoints.add(Waypoint(lat, lng))
                        }
                    }
                }

                segPaths.add(
                    SegmentPath(
                        fromStopId = stops[i].id,
                        toStopId = stops[i + 1].id,
                        roadPoints = roadPoints
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Silent fallback to straight lines
        }

        if (segPaths.size == stops.size - 1) {
            return@withContext route.copy(segmentPaths = segPaths)
        }
        
        return@withContext route
    }
}
