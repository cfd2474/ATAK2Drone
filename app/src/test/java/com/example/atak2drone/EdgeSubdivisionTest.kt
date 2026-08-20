package com.example.atak2drone

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.model.SlopeMode
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.strategy.VertexPathStrategy
import com.example.atak2drone.model.Coordinate
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite verifying Two-Stage Adaptive Edge Subdivision:
 * Stage 1: Spatial baseline 100ft (30.48m) subdivision for standard slopes (<50%)
 * Stage 2: High-density 30ft (9.144m) refinement for steep slopes (>=50%)
 */
class EdgeSubdivisionTest {

    @Test
    fun testShortPolygonEdgesRemainUnchanged() {
        // Square with 20m edges (<30.48m baseline threshold)
        val square = listOf(
            Coordinate(32.71500, -117.16100),
            Coordinate(32.71518, -117.16100),
            Coordinate(32.71518, -117.16078),
            Coordinate(32.71500, -117.16078)
        )

        val subdivided = GeometryUtils.subdividePolygonEdges(square, maxSegmentLengthMeters = 30.48)
        assertEquals(4, subdivided.size)
    }

    @Test
    fun testLongEdgeStage1Baseline100ftSubdivision() {
        // Polygon with one 200m edge and three 20m edges
        val polygon = listOf(
            Coordinate(32.71500, -117.16100),
            Coordinate(32.71680, -117.16100), // ~200m north
            Coordinate(32.71680, -117.16080),
            Coordinate(32.71500, -117.16080)
        )

        val baseSubdivided = GeometryUtils.subdividePolygonEdges(polygon, maxSegmentLengthMeters = 30.48)

        // 200m edge gets split into 7 sub-segments of <=30.48m each (adding 6 vertices).
        // Total vertices = 4 + 6 = 10.
        assertTrue(baseSubdivided.size >= 10)
    }

    @Test
    fun testStage2SteepSlope30ftRefinement() {
        val polygon = listOf(
            Coordinate(32.715, -117.161),
            Coordinate(32.716, -117.161),
            Coordinate(32.716, -117.160),
            Coordinate(32.715, -117.160)
        )

        val baseSubdivided = GeometryUtils.subdividePolygonEdges(polygon, maxSegmentLengthMeters = 30.48)

        // Simulate slopes: Edge 0 has 10% slope (flat), Edge 1 has 60% slope (STEEP >= 50%)
        val slopes = MutableList(baseSubdivided.size) { 10.0 }
        slopes[1] = 60.0 // Steep slope >= 50%

        val refined = GeometryUtils.refineHighSlopeSegments(
            polygon = baseSubdivided,
            slopes = slopes,
            steepSlopeThreshold = 50.0,
            fineMaxSegmentLength = 9.144 // 30 ft
        )

        // Steep edge should be further subdivided into high-density 30ft (9.144m) segments
        assertTrue(refined.size > baseSubdivided.size)
    }

    @Test
    fun testTwoStageAdaptiveStrategyPlanGeneration() {
        val polygon = listOf(
            Coordinate(32.715, -117.161),
            Coordinate(32.716, -117.161),
            Coordinate(32.716, -117.160),
            Coordinate(32.715, -117.160)
        )

        val baseSubdivided = GeometryUtils.subdividePolygonEdges(polygon, maxSegmentLengthMeters = 30.48)
        val slopes = MutableList(baseSubdivided.size) { 15.0 }
        slopes[0] = 55.0 // Steep slope zone

        val refinedPolygon = GeometryUtils.refineHighSlopeSegments(
            polygon = baseSubdivided,
            slopes = slopes,
            steepSlopeThreshold = 50.0,
            fineMaxSegmentLength = 9.144
        )

        val refinedSlopes = MutableList(refinedPolygon.size) { 15.0 }
        refinedSlopes[0] = 55.0
        refinedSlopes[1] = 55.0

        val config = SurveyConfig(
            missionName = "AdaptiveSubdivisionTest",
            altitudeMeters = 60.96,
            perimeterInteriorOffsetFt = 100.0,
            perimeterExteriorOffsetFt = 50.0,
            slopeMode = SlopeMode.AUTO_DEM_OPEN_SOURCE,
            edgeSlopeFactors = refinedSlopes
        )

        val strategy = VertexPathStrategy()
        val plan = strategy.generatePlan(refinedPolygon, config)

        assertNotNull(plan)
        assertTrue(plan.waypoints.isNotEmpty())
        assertNotNull(plan.metrics)
        assertEquals(SlopeMode.AUTO_DEM_OPEN_SOURCE, plan.metrics!!.slopeMode)
    }
}
