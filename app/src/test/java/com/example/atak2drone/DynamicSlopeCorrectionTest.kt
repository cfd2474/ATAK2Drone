package com.example.atak2drone

import com.example.atak2drone.domain.elevation.ElevationCache
import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.domain.model.SlopeMode
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.strategy.VertexPathStrategy
import com.example.atak2drone.model.Coordinate
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Unit test suite verifying Slope-Corrected Ground Distance mathematics,
 * per-edge variable polygon offset geometry, elevation caching, and strategy ring generation.
 */
class DynamicSlopeCorrectionTest {

    @Test
    fun testSlopeFactorMath() {
        // Flat 0% slope -> cos(arctan(0)) = 1.0
        val factor0 = cos(atan(0.0 / 100.0))
        assertEquals(1.0, factor0, 1e-4)

        // 100% slope (45 deg) -> cos(45 deg) = 1 / sqrt(2) approx 0.7071
        val factor100 = cos(atan(100.0 / 100.0))
        assertEquals(1.0 / sqrt(2.0), factor100, 1e-4)

        // 100 ft target ground distance on 100% slope -> ~70.71 ft horizontal map offset
        val horizontalOffset = 100.0 * factor100
        assertEquals(70.7106, horizontalOffset, 1e-3)
    }

    @Test
    fun testElevationCache() {
        val cache = ElevationCache()
        val c1 = Coordinate(32.71571, -117.16108)

        assertNull(cache.get(c1))

        cache.put(c1, 154.5)
        assertEquals(154.5, cache.get(c1)!!, 1e-4)

        // Test coordinate quantization tolerance within ~1m
        val c1SlightlyShifted = Coordinate(32.71571001, -117.16108001)
        assertEquals(154.5, cache.get(c1SlightlyShifted)!!, 1e-4)
    }

    @Test
    fun testVariablePolygonOffsetGeometry() {
        // Simple 100m x 100m square polygon
        val square = listOf(
            Point2D(0.0, 0.0),
            Point2D(100.0, 0.0),
            Point2D(100.0, 100.0),
            Point2D(0.0, 100.0)
        )

        // Variable offsets for the 4 edges: 10m outset on edges 0 & 2, 20m outset on edges 1 & 3
        val variableOffsets = listOf(10.0, 20.0, 10.0, 20.0)
        val outsetSquare = GeometryUtils.offsetPolygonVariable(square, variableOffsets)

        assertNotNull(outsetSquare)
        assertEquals(4, outsetSquare!!.size)

        // Original area = 10,000 m^2. Outset polygon should have strictly larger area.
        val origArea = GeometryUtils.polygonSignedArea(square)
        val outsetArea = GeometryUtils.polygonSignedArea(outsetSquare)
        assertTrue(outsetArea > origArea)
    }

    @Test
    fun testVertexPathStrategyWithAutoDemSlopes() {
        val polygon = listOf(
            Coordinate(32.715, -117.161),
            Coordinate(32.716, -117.161),
            Coordinate(32.716, -117.160),
            Coordinate(32.715, -117.160)
        )

        val edgeSlopes = listOf(20.0, 50.0, 80.0, 100.0) // Variable terrain slopes along 4 edges

        val config = SurveyConfig(
            missionName = "AutoDemTest",
            altitudeMeters = 60.96,
            perimeterInteriorOffsetFt = 100.0,
            perimeterExteriorOffsetFt = 50.0,
            slopeMode = SlopeMode.AUTO_DEM_OPEN_SOURCE,
            edgeSlopeFactors = edgeSlopes
        )

        val strategy = VertexPathStrategy()
        val plan = strategy.generatePlan(polygon, config)

        assertNotNull(plan)
        assertTrue(plan.waypoints.isNotEmpty())
        assertNotNull(plan.metrics)
        assertEquals(SlopeMode.AUTO_DEM_OPEN_SOURCE, plan.metrics!!.slopeMode)
        assertEquals(20.0, plan.metrics!!.minSlopePercent, 1e-3)
        assertEquals(100.0, plan.metrics!!.maxSlopePercent, 1e-3)
        assertEquals(62.5, plan.metrics!!.avgSlopePercent, 1e-3)
    }

    @Test
    fun testFallbackToFlat2DWhenSlopeModeIsOff() {
        val polygon = listOf(
            Coordinate(32.715, -117.161),
            Coordinate(32.716, -117.161),
            Coordinate(32.716, -117.160),
            Coordinate(32.715, -117.160)
        )

        val config = SurveyConfig(
            missionName = "FallbackTest",
            altitudeMeters = 60.96,
            perimeterInteriorOffsetFt = 100.0,
            perimeterExteriorOffsetFt = 50.0,
            slopeMode = SlopeMode.OFF,
            edgeSlopeFactors = null
        )

        val strategy = VertexPathStrategy()
        val plan = strategy.generatePlan(polygon, config)

        assertNotNull(plan)
        assertTrue(plan.waypoints.isNotEmpty())
        assertEquals(SlopeMode.OFF, plan.metrics!!.slopeMode)
    }
}
