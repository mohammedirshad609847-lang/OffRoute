package com.example.offlinebustracker.ui

import android.content.Context
import android.graphics.Color
import android.location.Location
import com.example.offlinebustracker.R
import com.example.offlinebustracker.data.BusState
import com.example.offlinebustracker.data.Route
import org.maplibre.android.annotations.Marker
import android.annotation.SuppressLint
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback

class MapLibreViewManager(
    private val context: Context,
    private val mapView: MapView
) : OnMapReadyCallback, MapLibreMap.OnMapClickListener, MapLibreMap.OnMarkerClickListener {

    private var map: MapLibreMap? = null
    private val busMarkers = mutableMapOf<String, Marker>()
    private val stopMarkers = mutableListOf<Marker>()
    private var routePolyline: Polyline? = null
    private var hasZoomedToUser = false

    // --- Builder Mode Variables ---
    var isBuilderMode = false
    var onMapTapListener: ((LatLng) -> Unit)? = null
    var onMapMarkerClickListener: ((Marker) -> Unit)? = null
    private val tempBuilderPoints = mutableListOf<LatLng>()

    companion object {
        const val STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
        const val STYLE_LIGHT = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
    }

    init {
        mapView.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(mapLibreMap: MapLibreMap) {
        this.map = mapLibreMap

        mapLibreMap.setStyle(STYLE_DARK) { style ->
            mapLibreMap.uiSettings.isCompassEnabled = true
            mapLibreMap.uiSettings.isZoomGesturesEnabled = true

            val locationComponent = mapLibreMap.locationComponent
            val activationOptions = LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(false)
                .build()

            locationComponent.activateLocationComponent(activationOptions)
            
            // Only enable if we already have permission, otherwise wait for MainActivity to enable it
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locationComponent.isLocationComponentEnabled = true
                locationComponent.renderMode = RenderMode.COMPASS
            }
        }

        // Attach the click listener to the map
        mapLibreMap.addOnMapClickListener(this)
        mapLibreMap.setOnMarkerClickListener(this)
    }

    @SuppressLint("MissingPermission")
    fun enableLocationComponent() {
        map?.style?.let {
            val locationComponent = map?.locationComponent
            if (locationComponent?.isLocationComponentActivated == true) {
                locationComponent.isLocationComponentEnabled = true
                locationComponent.renderMode = RenderMode.COMPASS
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disableLocationComponent() {
        map?.style?.let {
            val locationComponent = map?.locationComponent
            if (locationComponent?.isLocationComponentActivated == true) {
                locationComponent.isLocationComponentEnabled = false
            }
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        if (isBuilderMode) {
            onMapTapListener?.invoke(point)
            return true
        }
        return false
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (isBuilderMode) {
            onMapMarkerClickListener?.invoke(marker)
            return true // Consume click in builder mode
        }
        return false // Default behavior (info window)
    }

    fun drawRoadLine(roadPoints: List<LatLng>) {
        if (roadPoints.size > 1) {
            map?.addPolyline(PolylineOptions().addAll(roadPoints).color(Color.parseColor("#F27921")).width(5f))
        }
    }

    fun drawBuilderPreview(stops: List<com.example.offlinebustracker.data.BusStop>) {
        map?.clear()
        stopMarkers.clear()
        val polylineOptions = PolylineOptions().color(Color.parseColor("#F27921")).width(4f)
        
        stops.forEach { stop ->
            val pos = LatLng(stop.latitude, stop.longitude)
            polylineOptions.add(pos)
            val stopMarker = map?.addMarker(
                MarkerOptions().position(pos).title(stop.name).snippet(stop.id)
            )
            if (stopMarker != null) stopMarkers.add(stopMarker)
        }
        if (stops.size > 1) {
            map?.addPolyline(polylineOptions)
        }
    }

    fun startBuilderMode() {
        isBuilderMode = true
        tempBuilderPoints.clear()
        map?.clear()
        stopMarkers.clear()
    }

    fun stopBuilderMode() {
        isBuilderMode = false
        tempBuilderPoints.clear()
    }

    fun setMapStyle(styleIndex: Int) {
        val styleUrl = when (styleIndex) {
            0 -> STYLE_DARK
            1 -> STYLE_LIGHT
            else -> STYLE_DARK
        }
        map?.setStyle(styleUrl)
    }

    fun setupRoute(route: Route) {
        val currentMap = map ?: return

        currentMap.clear()
        stopMarkers.clear()

        val latLngs = ArrayList<LatLng>()

        val polylineOptions = PolylineOptions()
            .color(Color.parseColor("#F27921"))
            .width(5f)

        // Only draw polyline and stops for the selected route
        if (route.stops.isNotEmpty()) {
            for (stop in route.stops) {
                val position = LatLng(stop.latitude, stop.longitude)
                latLngs.add(position)

                val stopMarker = currentMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(stop.name)
                        .snippet("Scheduled: ${stop.getFormatted12HourTime()}")
                )
                stopMarkers.add(stopMarker)
            }
            
            // Draw actual road path if available (via OSRM), else falls back to straight lines
            val fullRoadPath = com.example.offlinebustracker.engine.BusInterpolationEngine.buildFullPolyline(route)
            for (wp in fullRoadPath) {
                polylineOptions.add(LatLng(wp.latitude, wp.longitude))
            }
            
            routePolyline = currentMap.addPolyline(polylineOptions)
        }

        if (latLngs.isNotEmpty()) {
            val centerIndex = latLngs.size / 2
            currentMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLngs[centerIndex], 14.0),
                1000
            )
        }

        // We no longer add a single busMarker in setupRoute, 
        // they are managed globally by updateAllBuses()
    }

    fun updateAllBuses(busStates: List<Pair<Route, BusState>>) {
        val currentMap = map ?: return

        val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_bus)
        val bitmap = android.graphics.Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val busIcon = org.maplibre.android.annotations.IconFactory.getInstance(context).fromBitmap(bitmap)

        for ((route, state) in busStates) {
            val latLng = LatLng(state.currentLat, state.currentLng)
            
            // Generate informative pop-up text
            val destText = "Heading to ${route.destination}"
            val nextStopText = state.nextStop?.let { "Next: ${it.name} (${state.etaToNextSeconds/60}m)" } ?: "Trip Completed"
            val snippetText = "$destText\n$nextStopText"

            if (busMarkers.containsKey(route.id)) {
                // Update existing marker
                val marker = busMarkers[route.id]!!
                marker.position = latLng
                marker.snippet = snippetText
                currentMap.updateMarker(marker)
            } else {
                // Create new marker
                val marker = currentMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(route.name)
                        .snippet(snippetText)
                        .icon(busIcon)
                )
                busMarkers[route.id] = marker
            }
        }
    }

    fun updateUserGps(location: Location?) {
        if (location != null) {
            val userLatLng = LatLng(location.latitude, location.longitude)

            // Feed your location directly into the glowing dot component
            map?.locationComponent?.forceLocationUpdate(location)

            if (!hasZoomedToUser) {
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0),
                    1500
                )
                hasZoomedToUser = true
            }
        }
    }

    fun forceCenterUser(location: Location?) {
        if (location != null) {
            val userLatLng = LatLng(location.latitude, location.longitude)
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0),
                1000
            )
        }
    }
}