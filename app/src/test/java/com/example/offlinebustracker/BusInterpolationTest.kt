package com.example.offlinebustracker

import com.example.offlinebustracker.data.BusStop
import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.data.TripStatus
import com.example.offlinebustracker.engine.BusInterpolationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BusInterpolationTest {

    private val demoRoute = Route(
        id = "test_route",
        name = "Test Route",
        stops = listOf(
            BusStop("stop_a", "Stop A", 12.9141, 74.8560, "17:55"),
            BusStop("stop_b", "Stop B", 12.9200, 74.8650, "18:10"),
            BusStop("stop_c", "Stop C", 12.9260, 74.8720, "18:20")
        )
    )

    @Test
    fun testBeforeDeparture() {
        val secondsAt1750 = (17 * 3600 + 50 * 60).toLong()
        val state = BusInterpolationEngine.calculateBusState(secondsAt1750, demoRoute)

        assertEquals(TripStatus.NOT_STARTED, state.status)
        assertEquals(12.9141, state.currentLat, 0.0001)
        assertEquals(74.8560, state.currentLng, 0.0001)
        assertEquals(0, state.overallProgressPercent)
    }

    @Test
    fun testInterpolationAt1802() {
        // 18:02: 7 minutes elapsed out of 15 min segment between A (17:55) and B (18:10)
        val secondsAt1802 = (18 * 3600 + 2 * 60).toLong()
        val state = BusInterpolationEngine.calculateBusState(secondsAt1802, demoRoute)

        assertEquals(TripStatus.IN_TRANSIT, state.status)
        assertEquals("Stop A", state.lastStopPassed?.name)
        assertEquals("Stop B", state.nextStop?.name)
        assertEquals(480L, state.etaToNextSeconds) // 18:10 - 18:02 = 8 minutes = 480s

        // Expected Lat: 12.9141 + (7/15) * (12.9200 - 12.9141) = ~12.91685
        assertEquals(12.91685, state.currentLat, 0.001)
        // Expected Lng: 74.8560 + (7/15) * (74.8650 - 74.8560) = ~74.8602
        assertEquals(74.8602, state.currentLng, 0.001)

        // Overall progress: (18:02 - 17:55) / (18:20 - 17:55) = 7 / 25 = 28%
        assertEquals(28, state.overallProgressPercent)
        // Segment progress: 7 / 15 = 46%
        assertEquals(46, state.segmentProgressPercent)
    }

    @Test
    fun testArrivalAtStopB() {
        val secondsAt1810 = (18 * 3600 + 10 * 60).toLong()
        val state = BusInterpolationEngine.calculateBusState(secondsAt1810, demoRoute)

        assertEquals(TripStatus.IN_TRANSIT, state.status)
        assertEquals("Stop B", state.lastStopPassed?.name)
        assertEquals("Stop C", state.nextStop?.name)
        assertEquals(12.9200, state.currentLat, 0.0001)
        assertEquals(74.8650, state.currentLng, 0.0001)
    }

    @Test
    fun testTripCompletedAfter1820() {
        val secondsAt1825 = (18 * 3600 + 25 * 60).toLong()
        val state = BusInterpolationEngine.calculateBusState(secondsAt1825, demoRoute)

        assertEquals(TripStatus.COMPLETED, state.status)
        assertEquals(12.9260, state.currentLat, 0.0001)
        assertEquals(74.8720, state.currentLng, 0.0001)
        assertEquals(100, state.overallProgressPercent)
    }
}
