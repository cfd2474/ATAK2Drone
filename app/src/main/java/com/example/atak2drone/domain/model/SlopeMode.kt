package com.example.atak2drone.domain.model

/**
 * Modes for slope-corrected ground distance calculations.
 */
enum class SlopeMode {
    /** Standard 2D planar map offset (slope = 0%, factor = 1.0). */
    OFF,

    /** Dynamic tangent slope calculated locally at each perimeter edge via open-source DEM elevation API. */
    AUTO_DEM_OPEN_SOURCE
}
