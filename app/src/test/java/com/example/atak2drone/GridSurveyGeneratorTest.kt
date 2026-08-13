package com.example.atak2drone

import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.optimizer.GridSurveyGenerator
import com.example.atak2drone.domain.strategy.GridSurveyStrategy
import com.example.atak2drone.domain.strategy.MissionStrategyFactory
import com.example.atak2drone.domain.strategy.VertexPathStrategy
import com.example.atak2drone.model.Coordinate
import org.junit.Assert.*
import org.junit.Test

class GridSurveyGeneratorTest {

    private val sampleAtakPolygon = listOf(
        Coordinate(37.7749, -122.4194),
        Coordinate(37.7790, -122.4194),
        Coordinate(37.7790, -122.4100),
        Coordinate(37.7749, -122.4100)
    )

    @Test
    fun testGridGenerationProducesWaypointsAndMetrics() {
        val config = SurveyConfig(
            missionName = "TestGrid",
            altitudeMeters = 60.96 // 200ft
        )

        val plan = GridSurveyGenerator.generateGridPlan(sampleAtakPolygon, config)

        assertEquals("TestGrid", plan.missionName)
        assertEquals(MissionType.GRID_SURVEY, plan.missionType)
        assertTrue("Waypoints must not be empty", plan.waypoints.isNotEmpty())
        assertNotNull(plan.metrics)
        assertTrue(plan.metrics!!.totalDistanceMeters > 0)
        assertTrue(plan.metrics!!.estimatedFlightDurationSeconds > 0)
    }

    @Test
    fun testStrategyFactoryAndVertexPath() {
        val config = SurveyConfig(
            missionName = "PerimeterMission",
            altitudeMeters = 121.92 // 400ft
        )

        val vertexStrategy = MissionStrategyFactory.createStrategy(MissionType.VERTEX_PERIMETER)
        assertTrue(vertexStrategy is VertexPathStrategy)

        val vertexPlan = vertexStrategy.generatePlan(sampleAtakPolygon, config)
        assertTrue(vertexPlan.waypoints.isNotEmpty())
        assertEquals(MissionType.VERTEX_PERIMETER, vertexPlan.missionType)

        val gridStrategy = MissionStrategyFactory.createStrategy(MissionType.GRID_SURVEY)
        assertTrue(gridStrategy is GridSurveyStrategy)
        val gridPlan = gridStrategy.generatePlan(sampleAtakPolygon, config)
        assertEquals(MissionType.GRID_SURVEY, gridPlan.missionType)
    }
}
