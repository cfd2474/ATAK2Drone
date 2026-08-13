package com.example.atak2drone.domain.model

import com.example.atak2drone.model.Coordinate

/**
 * Represents the type of flight path generated.
 */
enum class MissionType {
    VERTEX_PERIMETER,
    GRID_SURVEY
}

/**
 * Encapsulates a complete flight plan ready for WPML compilation.
 *
 * @property missionName The mission identifier.
 * @property missionType The type of mission (Vertex perimeter vs Lawnmower survey).
 * @property polygon The input polygon boundary points.
 * @property waypoints The ordered list of generated flight waypoints.
 * @property config The configuration used to generate this plan.
 * @property metrics The optimization and flight duration metrics (if applicable).
 */
data class MissionPlan(
    val missionName: String,
    val missionType: MissionType,
    val polygon: List<Coordinate>,
    val waypoints: List<Coordinate>,
    val config: SurveyConfig,
    val metrics: OptimizationMetrics? = null
)
