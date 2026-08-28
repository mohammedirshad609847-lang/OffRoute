package com.example.offlinebustracker.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader

class RouteRepository(private val context: Context) {

    private val gson = Gson()
    private val routesFile = File(context.filesDir, "app_routes.json")

    /**
     * Loads all saved routes from local file storage, fallback to default assets JSON.
     */
    fun loadAllRoutes(): List<Route> {
        return if (routesFile.exists()) {
            try {
                FileReader(routesFile).use { reader ->
                    val type = object : TypeToken<List<Route>>() {}.type
                    val list: List<Route>? = gson.fromJson(reader, type)
                    if (!list.isNullOrEmpty()) {
                        list.map { r ->
                            r.copy(
                                segmentPaths = (r.segmentPaths as Any?) as? List<SegmentPath> ?: emptyList(),
                                waypoints = (r.waypoints as Any?) as? List<Waypoint> ?: emptyList()
                            )
                        }
                    } else loadDefaultRoutes()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loadDefaultRoutes()
            }
        } else {
            val defaultRoutes = loadDefaultRoutes()
            saveAllRoutes(defaultRoutes)
            defaultRoutes
        }
    }

    /**
     * Saves list of routes to local app storage.
     */
    fun saveAllRoutes(routes: List<Route>) {
        try {
            FileWriter(routesFile).use { writer ->
                gson.toJson(routes, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addOrUpdateRoute(newRoute: Route) {
        val currentList = loadAllRoutes().toMutableList()
        val index = currentList.indexOfFirst { it.id == newRoute.id }
        if (index >= 0) {
            currentList[index] = newRoute
        } else {
            currentList.add(newRoute)
        }
        saveAllRoutes(currentList)
    }

    fun deleteRoute(routeId: String) {
        val currentList = loadAllRoutes().filterNot { it.id == routeId }
        if (currentList.isNotEmpty()) {
            saveAllRoutes(currentList)
        }
    }

    /**
     * Loads the default demo routes from assets JSON file.
     */
    fun loadDefaultRoutes(): List<Route> {
        return try {
            val inputStream = context.assets.open("demo_route.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<Route>>() {}.type
            val list: List<Route> = gson.fromJson(reader, type)
            list.map { route -> 
                route.copy(
                    segmentPaths = (route.segmentPaths as Any?) as? List<SegmentPath> ?: emptyList(),
                    waypoints = (route.waypoints as Any?) as? List<Waypoint> ?: emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(getFallbackRoute())
        }
    }

    fun getFallbackRoute(): Route {
        val stops = listOf(
            BusStop("stop_moodbidri", "Moodbidri Bus Stand", 13.0650, 74.9912, "17:00"),
            BusStop("stop_badagaulipady", "Badagaulipady", 12.9850, 74.9500, "17:20"),
            BusStop("stop_kaikamba", "Kaikamba", 12.9644, 74.9333, "17:30"),
            BusStop("stop_vamanjoor", "Vamanjoor", 12.9231, 74.8988, "17:45"),
            BusStop("stop_mangalore", "Mangaluru (State Bank)", 12.8700, 74.8436, "18:15")
        )
        val waypoints = listOf(
            Waypoint(13.0650, 74.9912), // Moodbidri
            Waypoint(13.0200, 74.9700), // Highway curve
            Waypoint(12.9850, 74.9500), // Badagaulipady
            Waypoint(12.9644, 74.9333), // Kaikamba
            Waypoint(12.9450, 74.9150), // Gurupura River bridge
            Waypoint(12.9231, 74.8988), // Vamanjoor
            Waypoint(12.8900, 74.8600), // City approach
            Waypoint(12.8700, 74.8436)  // Mangaluru
        )
        return Route(
            id = "route_nh169",
            name = "NH169 Express",
            busNumber = "BUS-4DM25",
            destination = "Mangaluru",
            stops = stops,
            waypoints = waypoints
        )
    }
}
