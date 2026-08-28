package com.example.offlinebustracker.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.Toast
import com.example.offlinebustracker.data.BusStop
import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.data.RouteRepository
import com.example.offlinebustracker.databinding.DialogAdminStudioBinding
import com.example.offlinebustracker.databinding.ItemStopRowBinding
import java.util.UUID

class AdminBusStudioDialog(
    context: Context,
    private val routeRepository: RouteRepository,
    private val existingRoute: Route? = null,
    private val onSaved: (Route) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogAdminStudioBinding
    private val stopRows = mutableListOf<ItemStopRowBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAdminStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        binding.btnCancelStudio.setOnClickListener { dismiss() }

        // Populate existing route details or defaults
        if (existingRoute != null) {
            binding.etBusName.setText(existingRoute.name)
            binding.etBusDestination.setText(existingRoute.destination)
            existingRoute.stops.forEach { addStopRow(it.name, it.scheduledTime, it.latitude, it.longitude) }
        } else {
            binding.etBusName.setText("City Express Line 202")
            binding.etBusDestination.setText("East Airport Depot")
            addStopRow("Stop 1 (Origin)", "08:00", 12.9141, 74.8560)
            addStopRow("Stop 2 (Midpoint)", "08:15", 12.9200, 74.8650)
            addStopRow("Stop 3 (Destination)", "08:30", 12.9260, 74.8720)
        }

        binding.btnAddStopRow.setOnClickListener {
            addStopRow("New Stop", "08:45", 12.9300, 74.8780)
        }

        binding.btnSaveRoute.setOnClickListener {
            val name = binding.etBusName.text.toString().trim()
            val dest = binding.etBusDestination.text.toString().trim()

            if (name.isEmpty() || dest.isEmpty()) {
                Toast.makeText(context, "Please enter Bus Name and Destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stops = mutableListOf<BusStop>()
            for ((index, row) in stopRows.withIndex()) {
                val sName = row.etStopName.text.toString().trim()
                val sTime = (row.etStopTime.tag as? String) ?: row.etStopTime.text.toString().trim()
                val sLat = row.etStopLat.text.toString().toDoubleOrNull() ?: 12.9141
                val sLng = row.etStopLng.text.toString().toDoubleOrNull() ?: 74.8560

                if (sName.isEmpty() || sTime.isEmpty()) {
                    Toast.makeText(context, "Stop #${index + 1} details incomplete", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                stops.add(BusStop("stop_$index", sName, sLat, sLng, sTime))
            }

            if (stops.size < 2) {
                Toast.makeText(context, "Route must contain at least 2 stops", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val routeId = existingRoute?.id ?: "route_${System.currentTimeMillis()}"
            val busNumber = name.split(" ").lastOrNull() ?: "BUS-202"

            val newRoute = Route(
                id = routeId,
                name = name,
                busNumber = busNumber,
                destination = dest,
                stops = stops
            )

            routeRepository.addOrUpdateRoute(newRoute)
            Toast.makeText(context, "Bus Schedule Saved Successfully!", Toast.LENGTH_SHORT).show()
            onSaved(newRoute)
            dismiss()
        }
    }

    private fun addStopRow(name: String, time: String, lat: Double, lng: Double) {
        val inflater = LayoutInflater.from(context)
        val rowBinding = ItemStopRowBinding.inflate(inflater, binding.containerStops, false)

        rowBinding.etStopName.setText(name)
        com.example.offlinebustracker.utils.TimePickerHelper.attachTimePicker(context, rowBinding.etStopTime, time)
        rowBinding.etStopLat.setText(lat.toString())
        rowBinding.etStopLng.setText(lng.toString())

        rowBinding.btnDeleteStop.setOnClickListener {
            binding.containerStops.removeView(rowBinding.root)
            stopRows.remove(rowBinding)
        }

        stopRows.add(rowBinding)
        binding.containerStops.addView(rowBinding.root)
    }
}
