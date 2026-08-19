package com.example.atak2drone.domain.elevation

import com.example.atak2drone.domain.geometry.GeometryUtils
import com.example.atak2drone.domain.geometry.Point2D
import com.example.atak2drone.model.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Implementation of [IElevationProvider] querying open-source elevation APIs
 * (Open-Elevation REST API, Open-Topo-Data API, and USGS 3DEP API) with local caching.
 */
class OpenElevationProvider(
    private val cache: ElevationCache = ElevationCache()
) : IElevationProvider {

    companion object {
        private const val OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup"
        private const val OPEN_TOPO_DATA_URL = "https://api.opentopodata.org/v1/srtm30m"
        private const val USGS_ELEVATION_URL = "https://epqs.nationalmap.gov/v1/json"
        private const val USER_AGENT = "ATAK2Drone/2.1.0 (Android Mobile)"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 8000
    }

    override suspend fun getElevations(coordinates: List<Coordinate>): List<Double> = withContext(Dispatchers.IO) {
        if (coordinates.isEmpty()) return@withContext emptyList()

        val results = MutableList(coordinates.size) { 0.0 }
        val missingIndices = mutableListOf<Int>()
        val missingCoords = mutableListOf<Coordinate>()

        for (i in coordinates.indices) {
            val cached = cache.get(coordinates[i])
            if (cached != null) {
                results[i] = cached
            } else {
                missingIndices.add(i)
                missingCoords.add(coordinates[i])
            }
        }

        if (missingCoords.isEmpty()) {
            return@withContext results
        }

        // Query missing coordinates across providers (Open-Elevation -> Open-Topo-Data -> USGS)
        val fetchedElevations = fetchBatchFromOpenElevation(missingCoords)
            ?: fetchBatchFromOpenTopoData(missingCoords)
            ?: fetchFallbackFromUsgs(missingCoords)
            ?: throw IOException("Elevation APIs unreachable (check internet connection).")

        for (k in missingCoords.indices) {
            val idx = missingIndices[k]
            val elev = fetchedElevations[k]
            results[idx] = elev
            cache.put(missingCoords[k], elev)
        }

        results
    }

    override suspend fun getTransverseSlopes(
        coordinates: List<Coordinate>,
        normalAzimuthsDeg: List<Double>,
        sampleDistanceMeters: Double
    ): List<Double> = withContext(Dispatchers.IO) {
        require(coordinates.size == normalAzimuthsDeg.size) {
            "Coordinates and normal azimuths must have matching size."
        }
        if (coordinates.isEmpty()) return@withContext emptyList()

        val sampledPoints = mutableListOf<Coordinate>()
        for (i in coordinates.indices) {
            val p0 = coordinates[i]
            val azRad = Math.toRadians(normalAzimuthsDeg[i])

            val origin = p0
            val offsetCartesian = Point2D(
                x = sampleDistanceMeters * sin(azRad),
                y = sampleDistanceMeters * cos(azRad)
            )
            val pOffset = GeometryUtils.projectToGeographic(offsetCartesian, origin)

            sampledPoints.add(p0)
            sampledPoints.add(pOffset)
        }

        val elevations = getElevations(sampledPoints)

        val slopes = mutableListOf<Double>()
        for (i in coordinates.indices) {
            val z0 = elevations[2 * i]
            val zOffset = elevations[2 * i + 1]
            val deltaZ = abs(zOffset - z0)

            val slopeRatio = deltaZ / sampleDistanceMeters
            val slopePercent = slopeRatio * 100.0
            slopes.add(slopePercent)
        }

        slopes
    }

    private fun fetchBatchFromOpenElevation(coords: List<Coordinate>): List<Double>? {
        return try {
            val jsonLocations = JSONObject()
            val locationsArray = org.json.JSONArray()
            for (c in coords) {
                val loc = JSONObject()
                loc.put("latitude", c.latitude)
                loc.put("longitude", c.longitude)
                locationsArray.put(loc)
            }
            jsonLocations.put("locations", locationsArray)

            val url = URL(OPEN_ELEVATION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(jsonLocations.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val resultsArr = root.getJSONArray("results")
                val elevations = mutableListOf<Double>()
                for (i in 0 until resultsArr.length()) {
                    val obj = resultsArr.getJSONObject(i)
                    elevations.add(obj.optDouble("elevation", 0.0))
                }
                elevations
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchBatchFromOpenTopoData(coords: List<Coordinate>): List<Double>? {
        return try {
            val locParam = coords.joinToString("|") { "${it.latitude},${it.longitude}" }
            val url = URL("$OPEN_TOPO_DATA_URL?locations=$locParam")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val resultsArr = root.getJSONArray("results")
                val elevations = mutableListOf<Double>()
                for (i in 0 until resultsArr.length()) {
                    val obj = resultsArr.getJSONObject(i)
                    elevations.add(obj.optDouble("elevation", 0.0))
                }
                elevations
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFallbackFromUsgs(coords: List<Coordinate>): List<Double>? {
        return try {
            val elevations = mutableListOf<Double>()
            for (c in coords) {
                val urlString = "$USGS_ELEVATION_URL?x=${c.longitude}&y=${c.latitude}&units=Meters"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val value = json.optJSONObject("USGS_Elevation_Point_Query_Service")
                        ?.optJSONObject("Elevation_Query")
                        ?.optDouble("Elevation", 0.0) ?: 0.0
                    elevations.add(if (value == -1000000.0) 0.0 else value)
                } else {
                    elevations.add(0.0)
                }
            }
            elevations
        } catch (_: Exception) {
            null
        }
    }
}
