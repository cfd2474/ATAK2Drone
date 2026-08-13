package com.example.atak2drone.controller

import android.content.Context
import com.example.atak2drone.builder.WpmlBuilder
import com.example.atak2drone.domain.interfaces.IKmlParser
import com.example.atak2drone.domain.interfaces.IKmzPackager
import com.example.atak2drone.domain.interfaces.IMissionStrategy
import com.example.atak2drone.domain.interfaces.IWpmlBuilder
import com.example.atak2drone.domain.model.MissionPlan
import com.example.atak2drone.domain.model.MissionType
import com.example.atak2drone.domain.model.SurveyConfig
import com.example.atak2drone.domain.strategy.MissionStrategyFactory
import com.example.atak2drone.model.CameraType
import com.example.atak2drone.packager.KmzPackager
import com.example.atak2drone.parser.KmlParser
import java.io.File
import java.io.InputStream

/**
 * Result bundle returned upon successful mission generation.
 */
data class MissionGenerationResult(
    val kmzFilePath: String,
    val plan: MissionPlan
)

/**
 * Dependency Inversion Principle (DIP):
 * High-level coordinator orchestrating KML parsing, mission path planning strategy,
 * WPML XML compilation, and KMZ compression.
 */
class MissionController(
    private val kmlParser: IKmlParser = KmlParser(),
    private val kmzPackager: IKmzPackager = KmzPackager()
) {

    /**
     * Executes the end-to-end mission generation pipeline.
     */
    fun createMission(
        context: Context,
        kmlInputStream: InputStream,
        config: SurveyConfig,
        missionType: MissionType = MissionType.VERTEX_PERIMETER,
        strategy: IMissionStrategy = MissionStrategyFactory.createStrategy(missionType),
        wpmlBuilder: IWpmlBuilder = WpmlBuilder(context)
    ): Result<MissionGenerationResult> {
        return runCatching {
            // 1. Parse polygon boundary (SRP)
            val polygon = kmlParser.parsePolygon(kmlInputStream)
            require(polygon.size >= 3) { "Polygon must contain at least 3 points." }

            // 2. Generate mission plan using pluggable strategy (OCP/LSP)
            val plan = strategy.generatePlan(polygon, config)

            // 3. Stage WPML bundle in temporary working directory
            val workingDir = File(context.cacheDir, "_wpml_stage_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                wpmlBuilder.buildWpmlBundle(plan, workingDir).getOrThrow()

                // 4. Output KMZ destination in internal filesDir
                val missionDir = File(context.filesDir, config.missionName).apply { mkdirs() }
                val outKmz = File(missionDir, "${config.missionName}.kmz")

                // 5. Package into final KMZ archive (SRP)
                kmzPackager.packageToKmz(workingDir, outKmz)

                if (!outKmz.exists() || outKmz.length() == 0L) {
                    throw IllegalStateException("Failed to produce valid KMZ file at ${outKmz.absolutePath}")
                }

                MissionGenerationResult(
                    kmzFilePath = outKmz.absolutePath,
                    plan = plan
                )
            } finally {
                workingDir.deleteRecursively()
            }
        }
    }

    companion object {
        private val defaultController = MissionController()

        /**
         * Convenience static adapter providing backwards-compatibility.
         */
        fun generateMission(
            context: Context,
            kmlInputStream: InputStream,
            missionName: String,
            altitudeFt: Double,
            cameraType: CameraType,
            missionType: MissionType = MissionType.VERTEX_PERIMETER,
            customAngleDegrees: Double? = null
        ): Result<String> {
            val altitudeMeters = altitudeFt * 0.3048
            val config = SurveyConfig(
                missionName = missionName,
                altitudeMeters = altitudeMeters,
                cameraType = cameraType,
                customAngleDegrees = customAngleDegrees
            )

            return defaultController.createMission(
                context = context,
                kmlInputStream = kmlInputStream,
                config = config,
                missionType = missionType
            ).map { it.kmzFilePath }
        }
    }
}