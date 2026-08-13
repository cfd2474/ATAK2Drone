package com.example.atak2drone.domain.strategy

import com.example.atak2drone.domain.interfaces.IMissionStrategy
import com.example.atak2drone.domain.model.MissionPlan
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.optimizer.GridSurveyGenerator
import com.example.atak2drone.model.Coordinate

/**
 * Generates an optimal coverage lawnmower grid survey flight path inside the ATAK polygon.
 */
class GridSurveyStrategy : IMissionStrategy {

    override val missionType: MissionType = MissionType.GRID_SURVEY

    override fun generatePlan(polygon: List<Coordinate>, config: SurveyConfig): MissionPlan {
        return GridSurveyGenerator.generateGridPlan(polygon, config)
    }
}
