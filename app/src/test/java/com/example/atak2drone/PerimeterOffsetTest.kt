package com.example.atak2drone

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.strategy.VertexPathStrategy
import com.example.atak2drone.model.Coordinate
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PerimeterOffsetTest {

    @Test
    fun testPolygonOutwardOffsetExpansion() {
        // 100m x 100m square: Area = 10,000 m^2
        val square = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(100.0, 100.0),
            Point2D(0.0, 100.0)
        )

        // Expand outwards by 10 meters: Each side becomes 120m -> Area = 14,400 m^2
        val expanded = GeometryUtils.offsetPolygon(square, 10.0)
        assertNotNull(expanded)
        assertEquals(4, expanded!!.size)

        val expandedArea = abs(GeometryUtils.polygonSignedArea(expanded))
        assertEquals(14400.0, expandedArea, 1.0)
    }

    @Test
    fun testPolygonInwardOffsetContraction() {
        // 100m x 100m square: Area = 10,000 m^2
        val square = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(100.0, 100.0),
            Point2D(0.0, 100.0)
        )

        // Contract inwards by 10 meters: Each side becomes 80m -> Area = 6,400 m^2
        val contracted = GeometryUtils.offsetPolygon(square, -10.0)
        assertNotNull(contracted)
        assertEquals(4, contracted!!.size)

        val contractedArea = abs(GeometryUtils.polygonSignedArea(contracted))
        assertEquals(6400.0, contractedArea, 1.0)
    }

    @Test
    fun testVertexPathStrategyGeneratesMultiRingCorridor() {
        val sampleAtakPolygon = listOf(
            Coordinate(37.7749, -122.4194),
            Coordinate(37.7790, -122.4194),
            Coordinate(37.7790, -122.4100),
            Coordinate(37.7749, -122.4100)
        )

        val config = SurveyConfig(
            missionName = "CorridorTest",
            altitudeMeters = 60.96, // 200ft
            perimeterInteriorOffsetFt = 100.0, // 100 ft interior
            perimeterExteriorOffsetFt = 50.0   // 50 ft exterior
        )

        val strategy = VertexPathStrategy()
        val plan = strategy.generatePlan(sampleAtakPolygon, config)

        assertEquals("CorridorTest", plan.missionName)
        assertEquals(MissionType.VERTEX_PERIMETER, plan.missionType)

        // Waypoints must contain multiple chained rings (more waypoints than just single 4-vertex boundary)
        assertTrue(
            "Multi-ring corridor must generate more waypoints than a single perimeter (${plan.waypoints.size} > 4)",
            plan.waypoints.size > 4
        )

        assertNotNull(plan.metrics)
        assertTrue(plan.metrics!!.totalDistanceMeters > 0)
        assertTrue(plan.metrics!!.estimatedFlightDurationSeconds > 0)
        assertTrue("Corridor must have at least 2 concentric passes", plan.metrics!!.numberOfTransects >= 2)
    }
}
