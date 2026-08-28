package com.example.offlinebustracker

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.data.RouteRepository
import com.example.offlinebustracker.engine.BusInterpolationEngine
import com.example.offlinebustracker.engine.OsrmRouteFetcher
import com.example.offlinebustracker.location.UserLocationManager
import com.example.offlinebustracker.ui.AdminBusStudioDialog
import com.example.offlinebustracker.ui.MapLibreViewManager
import com.example.offlinebustracker.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var routeRepository: RouteRepository
    private lateinit var userLocationManager: UserLocationManager
    private lateinit var mapLibreManager: MapLibreViewManager

    private val allRoutes = mutableListOf<Route>()
    private lateinit var currentRoute: Route

    // Time simulation state
    private var isSimulatingTime = true
    private val simStartSeconds: Long = 17 * 3600    // 17:00 — start of slider range
    private var currentSimulatedSeconds: Long = 18 * 3600 + 2 * 60  // 18:02 default

    // Map / GPS state
    private var currentUserLocation: Location? = null
    private var isGpsActive = false
    
    // Security
    private var isAdminLoggedIn = false

    companion object {
        private const val PERM_REQ_LOCATION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeRepository     = RouteRepository(this)
        userLocationManager = UserLocationManager(this)

        // Load routes from disk / default
        allRoutes.addAll(routeRepository.loadAllRoutes())
        currentRoute = allRoutes.firstOrNull() ?: routeRepository.loadDefaultRoutes().first()

        // Setup MapLibre manager with the MapView that's already in the layout
        mapLibreManager = MapLibreViewManager(this, binding.osmMapView)

    
        setupFleetSearch()
        setupUiControls()

        // Fetch road paths from OSRM for all routes in background
        fetchAllRoadPathsThenStartLoop()
    }

    // ── OSRM fetch + animation ───────────────────────────────────────────────

    /**
     * Fetches real road waypoints from OSRM for every stop-pair in the route.
     * Runs on IO (inside a coroutine); updates the route in-memory and refreshes
     * the map overlay. After fetching (or on failure), starts the animation loop.
     */
    private fun fetchAllRoadPathsThenStartLoop() {
        // Start animation immediately with straight lines
        startAnimationLoop()
        
        lifecycleScope.launch {
            binding.tvStatusHeader.text = "⏳ Loading Fleet Road Paths…"
            for (i in allRoutes.indices) {
                try {
                    val enriched = OsrmRouteFetcher.enrichRouteWithRoadPaths(allRoutes[i])
                    allRoutes[i] = enriched
                    if (currentRoute.id == enriched.id) {
                        currentRoute = enriched
                        mapLibreManager.setupRoute(currentRoute)
                    }
                    kotlinx.coroutines.delay(500)
                } catch (e: Exception) {
                    // Keep straight lines
                }
            }
            binding.tvStatusHeader.text = "Fleet Connected"
        }
    }

    /** Ticks once per second; repositions the bus marker on every tick. */
    private fun startAnimationLoop() {
        lifecycleScope.launch {
            while (true) {
                updateBusState()
                delay(1000L)
            }
        }
    }

    // ── Fleet search ────────────────────────────────────────────────────────

    private fun setupFleetSearch() {
        val names = allRoutes.map { "${it.name} to ${it.destination}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        binding.actvRouteSearch.setAdapter(adapter)

        binding.actvRouteSearch.setOnItemClickListener { _, _, position, _ ->
            val selection = adapter.getItem(position)
            val selectedRoute = allRoutes.find { "${it.name} to ${it.destination}" == selection }
            if (selectedRoute != null) {
                currentRoute = selectedRoute
                
                val origin = currentRoute.stops.firstOrNull()?.name ?: "Unknown"
                val dest = currentRoute.destination
                binding.tvRoutePathDisplay.text = "📍 $origin  ➔  🏁 $dest"
                
                mapLibreManager.setupRoute(currentRoute)
            }
        }
        
        // Initial setup
        val origin = currentRoute.stops.firstOrNull()?.name ?: "Unknown"
        val dest = currentRoute.destination
        binding.tvRoutePathDisplay.text = "📍 $origin  ➔  🏁 $dest"
        binding.actvRouteSearch.setText("${currentRoute.name} to ${currentRoute.destination}", false)
    }

    // ── UI controls ──────────────────────────────────────────────────────────

    private fun setupUiControls() {
        // Admin portal
        binding.btnAdminPortal.setOnClickListener {
            if (isAdminLoggedIn) {
                showAdminMenu()
            } else {
                showAdminLoginDialog()
            }
        }

        // GPS FAB
        binding.fabToggleGps.setOnClickListener {
            if (!isGpsActive) checkLocationPermissionAndStart()
            else {
                isGpsActive = false
                userLocationManager.stopLocationUpdates()
                currentUserLocation = null
                mapLibreManager.disableLocationComponent()
                Toast.makeText(this, "GPS Disabled", Toast.LENGTH_SHORT).show()
                updateBusState()
            }
        }

        // Bottom Scrubber logic
        binding.cbLiveTime.setOnCheckedChangeListener { _, isChecked ->
            isSimulatingTime = !isChecked
            if (isChecked) {
                updateBusState()
            }
        }
        
        binding.seekBarBusPath.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && currentRoute.stops.isNotEmpty()) {
                    isSimulatingTime = true
                    binding.cbLiveTime.isChecked = false
                    
                    val startSec = currentRoute.stops.first().getTimeInSeconds()
                    val endSec = currentRoute.stops.last().getTimeInSeconds()
                    val totalDuration = endSec - startSec
                    
                    currentSimulatedSeconds = startSec + (progress / 1000.0 * totalDuration).toLong()
                    updateBusState()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    // ── Route Builder & Studio ───────────────────────────────────────────────

    private val builderStops = mutableListOf<com.example.offlinebustracker.data.BusStop>()

    private fun showAdminLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_login, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val etPinInput = dialogView.findViewById<android.widget.EditText>(R.id.etPinInput)

        dialogView.findViewById<View>(R.id.btnCancelLogin).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnSubmitPin).setOnClickListener {
            val pin = etPinInput.text.toString()
            if (pin == "1234") {
                isAdminLoggedIn = true
                binding.btnAdminPortal.text = "🛠️ Route Studio"
                Toast.makeText(this, "Admin Access Granted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showAdminMenu()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                etPinInput.text.clear()
            }
        }
        dialog.show()
    }

    private fun showAdminMenu() {
        AlertDialog.Builder(this)
            .setTitle("⚙️ Route Studio")
            .setItems(arrayOf(
                "➕ Route Builder (Tap on Map)",
                "📝 Manual Entry (Studio Dialog)",
                "✏️ Edit Active Route (${currentRoute.name})",
                "🗑️ Delete Active Route"
            )) { _, which ->
                when (which) {
                    0 -> startBuilderMode()
                    1 -> AdminBusStudioDialog(this, routeRepository, null) { newRoute ->
                            allRoutes.add(newRoute)
                            setupFleetSearch()
                        }.show()
                    2 -> AdminBusStudioDialog(this, routeRepository, currentRoute) { updated ->
                            val idx = allRoutes.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) allRoutes[idx] = updated
                            currentRoute = updated
                            setupFleetSearch()
                            mapLibreManager.setupRoute(currentRoute)
                        }.show()
                    3 -> {
                        routeRepository.deleteRoute(currentRoute.id)
                        allRoutes.removeIf { it.id == currentRoute.id }
                        setupFleetSearch()
                        currentRoute = allRoutes.firstOrNull() ?: routeRepository.loadDefaultRoutes().first()
                        mapLibreManager.setupRoute(currentRoute)
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startBuilderMode() {
        binding.osmMapView.visibility = View.VISIBLE
        
        binding.panelBuilderMode.visibility = View.VISIBLE
        binding.fabToggleGps.visibility = View.GONE
        
        builderStops.clear()
        updateBuilderPanelText()
        
        mapLibreManager.startBuilderMode()
        mapLibreManager.onMapTapListener = { latLng ->
            val stopNum = builderStops.size + 1
            val defaultHour = 8 + (stopNum * 15) / 60
            val defaultMin = (stopNum * 15) % 60
            val defaultTime = String.format(java.util.Locale.US, "%02d:%02d", defaultHour, defaultMin)
            
            showAddStopDialog(latLng, stopNum, defaultTime)
        }
        
        mapLibreManager.onMapMarkerClickListener = { marker ->
            val stopId = marker.snippet
            val stopIndex = builderStops.indexOfFirst { it.id == stopId }
            if (stopIndex != -1) {
                showEditStopDialog(stopIndex)
            }
        }
        
        binding.btnBuilderCancel.setOnClickListener {
            stopBuilderMode()
        }
        
        binding.btnBuilderFinish.setOnClickListener {
            if (builderStops.size < 2) {
                Toast.makeText(this, "Need at least 2 stops!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showFinishRouteDialog()
        }
    }

    private fun showAddStopDialog(latLng: org.maplibre.android.geometry.LatLng, stopNum: Int, defaultTime: String) {
        val dialogView = layoutInflater.inflate(R.layout.layout_dialog_add_stop, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val etStopName = dialogView.findViewById<android.widget.EditText>(R.id.etStopName)
        val etStopTime = dialogView.findViewById<android.widget.EditText>(R.id.etStopTime)
        
        etStopName.setText("Stop $stopNum")
        com.example.offlinebustracker.utils.TimePickerHelper.attachTimePicker(this, etStopTime, defaultTime)
        
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btnSaveStop).setOnClickListener {
            val name = etStopName.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Stop $stopNum"
            val time = (etStopTime.tag as? String) ?: etStopTime.text.toString().trim().takeIf { it.isNotEmpty() } ?: defaultTime
            
            builderStops.add(com.example.offlinebustracker.data.BusStop(
                "stop_${System.currentTimeMillis()}_$stopNum", 
                name, 
                latLng.latitude, 
                latLng.longitude, 
                time
            ))
            updateBuilderPanelText()
            mapLibreManager.drawBuilderPreview(builderStops)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showEditStopDialog(stopIndex: Int) {
        val stop = builderStops[stopIndex]
        val dialogView = layoutInflater.inflate(R.layout.layout_dialog_edit_stop, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val etStopName = dialogView.findViewById<android.widget.EditText>(R.id.etStopName)
        val etStopTime = dialogView.findViewById<android.widget.EditText>(R.id.etStopTime)
        
        etStopName.setText(stop.name)
        com.example.offlinebustracker.utils.TimePickerHelper.attachTimePicker(this, etStopTime, stop.scheduledTime)
        
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btnDeleteStop).setOnClickListener {
            builderStops.removeAt(stopIndex)
            updateBuilderPanelText()
            mapLibreManager.drawBuilderPreview(builderStops)
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btnSaveStop).setOnClickListener {
            val newName = etStopName.text.toString().trim().takeIf { it.isNotEmpty() } ?: stop.name
            val newTime = (etStopTime.tag as? String) ?: etStopTime.text.toString().trim().takeIf { it.isNotEmpty() } ?: stop.scheduledTime
            
            builderStops[stopIndex] = stop.copy(name = newName, scheduledTime = newTime)
            updateBuilderPanelText()
            mapLibreManager.drawBuilderPreview(builderStops)
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updateBuilderPanelText() {
        if (builderStops.isEmpty()) {
            binding.tvBuilderTitle.text = "📍 Tap on map to add stops"
        } else {
            val summary = builderStops.joinToString(" ➔ ") { "${it.name} (${it.scheduledTime})" }
            binding.tvBuilderTitle.text = "📍 Route: $summary"
        }
    }

    private fun showFinishRouteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.layout_dialog_finish_route, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val etRouteName = dialogView.findViewById<android.widget.EditText>(R.id.etRouteName)
        val etBusNumber = dialogView.findViewById<android.widget.EditText>(R.id.etBusNumber)
        val etDestination = dialogView.findViewById<android.widget.EditText>(R.id.etDestination)
        
        dialogView.findViewById<View>(R.id.btnCancelRoute).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btnSaveRoute).setOnClickListener {
            val routeName = etRouteName.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Custom Route"
            val busNumber = etBusNumber.text.toString().trim().takeIf { it.isNotEmpty() } ?: "BUS-NEW"
            val dest = etDestination.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Destination"
            
            val routeId = "route_${System.currentTimeMillis()}"
            val newRoute = Route(
                id = routeId,
                name = routeName,
                busNumber = busNumber,
                destination = dest,
                stops = builderStops.toList()
            )
            
            routeRepository.addOrUpdateRoute(newRoute)
            allRoutes.add(newRoute)
            setupFleetSearch()
            
            stopBuilderMode()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun stopBuilderMode() {
        binding.panelBuilderMode.visibility = View.GONE
        binding.fabToggleGps.visibility = View.VISIBLE
        mapLibreManager.stopBuilderMode()
        mapLibreManager.setupRoute(currentRoute)
    }

    // ── GPS ──────────────────────────────────────────────────────────────────

    private fun checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERM_REQ_LOCATION
            )
        } else {
            startGpsUpdates()
        }
    }

    private fun startGpsUpdates() {
        isGpsActive = true
        userLocationManager.startLocationUpdates { location ->
            currentUserLocation = location
            mapLibreManager.updateUserGps(location)
            updateBusState()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ_LOCATION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            mapLibreManager.enableLocationComponent()
            startGpsUpdates()
        }
    }

    // ── Core bus state update ─────────────────────────────────────────────────

    private fun getTimeInSeconds(): Long = if (isSimulatingTime) {
        currentSimulatedSeconds
    } else {
        val cal = Calendar.getInstance()
        (cal.get(Calendar.HOUR_OF_DAY) * 3600
                + cal.get(Calendar.MINUTE) * 60
                + cal.get(Calendar.SECOND)).toLong()
    }

    /**
     * Called every second by [startAnimationLoop] and on any interaction.
     *
     * 1. Computes bus state via [BusInterpolationEngine] — which now walks
     *    road waypoints proportionally if OSRM data is present.
     * 2. Pushes the new bus marker position to MapLibre.
     * 3. Refreshes all status text fields.
     */
    private fun updateBusState() {
        val timeSec  = getTimeInSeconds()
        
        // MapLibre fleet update
        val fleetStates = allRoutes.map { route ->
            Pair(route, BusInterpolationEngine.calculateBusState(timeSec, route))
        }
        mapLibreManager.updateAllBuses(fleetStates)

        val busState = fleetStates.find { it.first.id == currentRoute.id }?.second ?: return

        // ── Status panel ─────────────────────────────────────────────────────────
        binding.tvCurrentClockTime.text =
            if (isSimulatingTime) "⏳ Simulated: ${busState.formattedCurrentTime}"
            else "🟢 Live: ${busState.formattedCurrentTime}"

        binding.tvDestinationBadge.text = "🎯 ${currentRoute.destination}"
        binding.tvStatusHeader.text     = busState.statusText

        binding.tvLastPassedStop.text = busState.lastStopPassed
            ?.let { "📍 ${it.name}  (${it.getFormatted12HourTime()})" }
            ?: "Departs: ${currentRoute.stops.firstOrNull()?.getFormatted12HourTime() ?: "--:--"}"

        binding.tvNextStopEta.text = busState.nextStop
            ?.let { "⏩ ${it.name}  in ${BusInterpolationEngine.formatSecondsToCountdown(busState.etaToNextSeconds)}" }
            ?: "🏁 Trip Completed"

        if (!isSimulatingTime) {
            binding.seekBarBusPath.progress = busState.overallProgressPercent * 10
        }
        binding.tvProgressPercentage.text = "${busState.overallProgressPercent}%"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        binding.osmMapView.onResume()
        updateBusState()
    }

    override fun onPause() {
        super.onPause()
        binding.osmMapView.onPause()
    }
}