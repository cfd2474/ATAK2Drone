package com.example.atak2drone.controller

import android.content.Context
import com.example.atak2drone.model.CameraType
import com.example.atak2drone.utils.KmlUtils
import com.example.atak2drone.utils.WpmlGenerator
import java.io.File
import java.io.InputStream

object MissionController {

    fun generateMission(
        context: Context,
        kmlInputStream: InputStream,
        missionName: String,
        altitudeFt: Double,
        cameraType: CameraType
    ): Result<String> {
        try {
            // 1) Persist the stream to a temp file
            val tmp = File.createTempFile("atak2drone_mission_", ".kml", context.cacheDir)
            tmp.outputStream().use { out -> kmlInputStream.copyTo(out) }

            // 2) Parse polygon FROM FILE (matches your KmlUtils)
            val polygonAnyList: List<*> = tmp.inputStream().use { ins ->
                KmlUtils.parseSinglePolygonFromKml(ins)
            }
            if (polygonAnyList.size < 3) {
                tmp.delete()
                return Result.failure(IllegalArgumentException("Polygon must have at least 3 points."))
            }

            // 3) Waypoints/polygon can be the same list; generator reads lat/lon by reflection
            @Suppress("UNCHECKED_CAST")
            val polygonList: List<Any> = polygonAnyList.map { it as Any }

            // 4) Convert ft -> m
            val altitudeMeters = altitudeFt * 0.3048

            // 5) Output dir (final KMZ will be written as parent/<mission>.kmz)
            val outputDir = File(context.filesDir, missionName).apply { mkdirs() }

            // 6) Use YOUR template-based generator (kept exactly as you posted)
            val kmz: File? = WpmlGenerator.generateFromTemplateKmz(
                context = context,
                missionName = missionName,
                polygon = polygonList,
                altitudeMeters = altitudeMeters,
                cameraType = cameraType,
                outDir = outputDir
            )

            // 7) Cleanup temp
            tmp.delete()

            return if (kmz?.exists() == true) {
                Result.success(kmz.absolutePath)
            } else {
                Result.failure(IllegalStateException("Failed to generate WPML KMZ file."))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}