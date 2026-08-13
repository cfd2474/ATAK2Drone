package com.example.atak2drone.domain.strategy

import com.example.atak2drone.domain.interfaces.IMissionStrategy
import com.example.atak2drone.domain.model.MissionType

/**
 * Factory for creating [IMissionStrategy] instances.
 */
object MissionStrategyFactory {

    fun createStrategy(missionType: MissionType): IMissionStrategy {
        return when (missionType) {
            MissionType.VERTEX_PERIMETER -> VertexPathStrategy()
            MissionType.GRID_SURVEY -> GridSurveyStrategy()
        }
    }
}
