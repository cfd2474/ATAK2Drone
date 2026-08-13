package com.example.atak2drone.domain.interfaces

import com.example.atak2drone.domain.model.MissionPlan
import java.io.File

/**
 * Interface Segregation Principle (ISP) & Dependency Inversion Principle (DIP):
 * Abstraction for constructing WPML compliant mission XML bundles.
 */
interface IWpmlBuilder {
    /**
     * Compiles a [MissionPlan] into WPML XML files (`template.kml`, `waylines.wpml`) inside target directory.
     *
     * @param plan The mission plan containing waypoints and parameters.
     * @param targetDirectory Directory where WPML directory structure is built.
     * @return Result containing the root directory or error.
     */
    fun buildWpmlBundle(plan: MissionPlan, targetDirectory: File): Result<File>
}
