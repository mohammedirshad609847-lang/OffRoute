package com.example.offlinebustracker

import com.example.offlinebustracker.data.BusStop
import com.example.offlinebustracker.data.Route
import com.example.offlinebustracker.engine.OsrmRouteFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class OsrmTest {
    @Test
    fun testOsrmFetch() = runBlocking {
        val stops = listOf(
            BusStop("stop1", "Stop 1", 12.866, 74.8436, "07:00"),
            BusStop("stop2", "Stop 2", 12.895, 74.83, "07:08")
        )
        val route = Route("route1", "Test Route", "BUS-1", "Dest", stops)
        
        val enriched = OsrmRouteFetcher.enrichRouteWithRoadPaths(route)
        println("SegmentPaths size: ${enriched.segmentPaths.size}")
        if (enriched.segmentPaths.isNotEmpty()) {
            println("Road points size: ${enriched.segmentPaths[0].roadPoints.size}")
            assertTrue(enriched.segmentPaths[0].roadPoints.isNotEmpty())
        } else {
            fail("Failed to fetch road paths")
        }
    }
}
