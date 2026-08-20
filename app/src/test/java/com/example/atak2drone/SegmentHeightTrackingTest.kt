package com.example.atak2drone

import com.example.atak2drone.builder.WpmlBuilder
import com.example.atak2drone.domain.elevation.ElevationCache
import com.example.atak2drone.domain.elevation.OpenElevationProvider
import com.example.atak2drone.domain.model.SlopeMode
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.strategy.VertexPathStrategy
import com.example.atak2drone.model.Coordinate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit test suite verifying segment-maximum terrain height tracking
 * and 3D waypoint altitude assignment.
 */
class SegmentHeightTrackingTest {

    @Test
    fun test3DCoordinateInstantiation() {
        val coord2D = Coordinate(32.715, -117.161)
        assertNull(coord2D.altitudeMeters)

        val coord3D = Coordinate(32.715, -117.161, 75.5)
        assertEquals(75.5, coord3D.altitudeMeters!!, 1e-3)
    }

    @Test
    fun testSegment3DAltitudesFlatTerrain() = runBlocking {
        val cache = ElevationCache()
        val square = listOf(
            Coordinate(32.71500, -117.16100),
            Coordinate(32.71518, -117.16100),
            Coordinate(32.71518, -117.16078),
            Coordinate(32.71500, -117.16078)
        )

        // Pre-fill cache with flat 50m ground elevation for all vertices and midpoints
        val origin = com.example.atak2drone.domain.geometry.GeometryUtils.computeCentroid(square)
        val cartesian = square.map { com.example.atak2drone.domain.geometry.GeometryUtils.projectToLocalCartesian(it, origin) }
        square.forEach { cache.put(it, 50.0) }
        for (i in square.indices) {
            val p1 = cartesian[i]
            val p2 = cartesian[(i + 1) % square.size]
            val mid = com.example.atak2drone.domain.geometry.Point2D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0)
            cache.put(com.example.atak2drone.domain.geometry.GeometryUtils.projectToGeographic(mid, origin), 50.0)
        }

        val provider = OpenElevationProvider(cache)

        // For flat terrain, all 3D waypoint altitudes should equal target altitude (60.96m / 200ft)
        val result3D = provider.computeSegment3DAltitudes(square, targetAltitudeMeters = 60.96)
        assertEquals(square.size, result3D.size)
        result3D.forEach { coord ->
            assertNotNull(coord.altitudeMeters)
            assertEquals(60.96, coord.altitudeMeters!!, 1e-2)
        }
    }

    @Test
    fun test3DAltitudesPropagatedInVertexPathStrategy() {
        val square3D = listOf(
            Coordinate(32.71500, -117.16100, 60.96),
            Coordinate(32.71518, -117.16100, 75.96), // 15m higher hill
            Coordinate(32.71518, -117.16078, 75.96),
            Coordinate(32.71500, -117.16078, 60.96)
        )

        val config = SurveyConfig(
            missionName = "Test3DStrategy",
            altitudeMeters = 60.96,
            slopeMode = SlopeMode.AUTO_DEM_OPEN_SOURCE,
            edgeSlopeFactors = listOf(10.0, 20.0, 10.0, 10.0)
        )

        val strategy = VertexPathStrategy()
        val plan = strategy.generatePlan(square3D, config)

        assertNotNull(plan)
        assertTrue(plan.waypoints.isNotEmpty())
        // Verify 3D altitudes are preserved on waypoints
        val has3DWaypoints = plan.waypoints.any { it.altitudeMeters != null && it.altitudeMeters!! > 60.96 }
        assertTrue(has3DWaypoints)
    }
}
