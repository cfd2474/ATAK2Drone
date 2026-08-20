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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Implementation of [IElevationProvider] querying open-source elevation APIs
 * (Open-Meteo DEM API, Open-Topo-Data API, USGS 3DEP API, and Open-Elevation REST API) with local caching.
 */
class OpenElevationProvider(
    private val cache: ElevationCache = ElevationCache()
) : IElevationProvider {

    companion object {
        private const val OPEN_METEO_URL = "https://api.open-meteo.com/v1/elevation"
        private const val OPEN_TOPO_DATA_URL = "https://api.opentopodata.org/v1/srtm30m"
        private const val USGS_ELEVATION_URL = "https://epqs.nationalmap.gov/v1/json"
        private const val OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup"
        private val USER_AGENT = "ATAK2Drone/${com.example.atak2drone.BuildConfig.VERSION_NAME} (Android Mobile)"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 8000
        private const val TAG = "OpenElevationProvider"
    }

    private fun logDebug(msg: String) {
        try {
            android.util.Log.d(TAG, msg)
        } catch (_: Exception) {
            println("[$TAG] $msg")
        }
    }

    private fun logWarn(msg: String) {
        try {
            android.util.Log.w(TAG, msg)
        } catch (_: Exception) {
            println("[$TAG WARN] $msg")
        }
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

        logDebug("Querying elevation for ${missingCoords.size} uncached coordinates...")

        // Process missing coordinates in chunks of 30 to respect provider limits
        val chunkSize = 30
        val fetchedElevations = mutableListOf<Double>()

        for (chunkStart in missingCoords.indices step chunkSize) {
            val chunk = missingCoords.subList(chunkStart, minOf(chunkStart + chunkSize, missingCoords.size))
            val chunkElevations = fetchBatchFromOpenMeteo(chunk)
                ?: fetchBatchFromOpenTopoData(chunk)
                ?: fetchFallbackFromUsgs(chunk)
                ?: fetchBatchFromOpenElevation(chunk)
                ?: throw IOException("Elevation APIs unreachable (check internet connection).")
            fetchedElevations.addAll(chunkElevations)
        }

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

    /**
     * Computes segment-maximum ground terrain heights for a polygon and evaluates 3D flight altitudes
     * per waypoint relative to the target flight altitude [targetAltitudeMeters].
     *
     * For every segment S_i = (P_i -> P_{i+1}), evaluates ground elevation at P_i, midpoint M_i, and P_{i+1}.
     * Sets segment altitude to targetAltitudeMeters + max(0, Z_max_segment - Z_poly_min).
     * Assigns waypoint altitude H_waypoint(P_i) = max(H_segment(S_{i-1}), H_segment(S_i)).
     */
    suspend fun computeSegment3DAltitudes(
        polygon: List<Coordinate>,
        targetAltitudeMeters: Double
    ): List<Coordinate> = withContext(Dispatchers.IO) {
        if (polygon.size < 3) return@withContext polygon

        val n = polygon.size
        val origin = GeometryUtils.computeCentroid(polygon)
        val cartesian = polygon.map { GeometryUtils.projectToLocalCartesian(it, origin) }

        val queryPoints = mutableListOf<Coordinate>()
        // 1. Add all vertices
        queryPoints.addAll(polygon)

        // 2. Add all segment midpoints
        for (i in 0 until n) {
            val p1 = cartesian[i]
            val p2 = cartesian[(i + 1) % n]
            val mid = Point2D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0)
            queryPoints.add(GeometryUtils.projectToGeographic(mid, origin))
        }

        val elevations = getElevations(queryPoints)
        if (elevations.size != 2 * n) {
            return@withContext polygon.map { it.copy(altitudeMeters = targetAltitudeMeters) }
        }

        val vertexElevations = elevations.subList(0, n)
        val midpointElevations = elevations.subList(n, 2 * n)

        val minGroundElev = elevations.minOrNull() ?: 0.0

        // Calculate segment flight heights
        val segmentHeights = DoubleArray(n)
        for (i in 0 until n) {
            val zStart = vertexElevations[i]
            val zMid = midpointElevations[i]
            val zEnd = vertexElevations[(i + 1) % n]
            val maxSegmentGround = maxOf(zStart, zMid, zEnd)

            val relativeRise = maxOf(0.0, maxSegmentGround - minGroundElev)
            segmentHeights[i] = targetAltitudeMeters + relativeRise
        }

        // Assign waypoint altitudes based on adjacent segment maximums
        val result3D = mutableListOf<Coordinate>()
        for (i in 0 until n) {
            val prevSegHeight = segmentHeights[(i - 1 + n) % n]
            val curSegHeight = segmentHeights[i]
            val wpHeight = maxOf(prevSegHeight, curSegHeight)
            result3D.add(polygon[i].copy(altitudeMeters = wpHeight))
        }

        result3D
    }

    /**
     * Provider #1: Open-Meteo DEM Elevation API (Global 90m/30m, fast & free batch GET)
     */
    private fun fetchBatchFromOpenMeteo(coords: List<Coordinate>): List<Double>? {
        return try {
            val lats = coords.joinToString(",") { String.format(Locale.US, "%.6f", it.latitude) }
            val lons = coords.joinToString(",") { String.format(Locale.US, "%.6f", it.longitude) }
            val url = URL("$OPEN_METEO_URL?latitude=$lats&longitude=$lons")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val elevArray = root.optJSONArray("elevation") ?: return null
                val elevations = mutableListOf<Double>()
                for (i in 0 until elevArray.length()) {
                    val valDouble = elevArray.optDouble(i, 0.0)
                    elevations.add(if (valDouble.isNaN()) 0.0 else valDouble)
                }
                logDebug("Fetched ${elevations.size} elevations from Open-Meteo DEM")
                if (elevations.size == coords.size) elevations else null
            } else {
                logWarn("Open-Meteo returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            logWarn("Open-Meteo query failed: ${e.message}")
            null
        }
    }

    /**
     * Provider #2: Open-Topo-Data API (SRTM 30m dataset)
     */
    private fun fetchBatchFromOpenTopoData(coords: List<Coordinate>): List<Double>? {
        return try {
            val locParam = coords.joinToString("|") {
                String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude)
            }
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
                logDebug("Fetched ${elevations.size} elevations from Open-Topo-Data")
                if (elevations.size == coords.size) elevations else null
            } else {
                logWarn("Open-Topo-Data returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            logWarn("Open-Topo-Data query failed: ${e.message}")
            null
        }
    }

    /**
     * Provider #3: USGS 3DEP National Map Elevation API (v1 json structure)
     */
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
                    val valStr = json.optString("value", "")
                    val value = valStr.toDoubleOrNull()
                        ?: json.optJSONObject("USGS_Elevation_Point_Query_Service")
                            ?.optJSONObject("Elevation_Query")
                            ?.optDouble("Elevation", 0.0)
                        ?: 0.0
                    elevations.add(if (value == -1000000.0 || value.isNaN()) 0.0 else value)
                } else {
                    elevations.add(0.0)
                }
            }
            logDebug("Fetched ${elevations.size} elevations from USGS 3DEP")
            if (elevations.size == coords.size) elevations else null
        } catch (e: Exception) {
            logWarn("USGS 3DEP query failed: ${e.message}")
            null
        }
    }

    /**
     * Provider #4: Open-Elevation REST API
     */
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
                logDebug("Fetched ${elevations.size} elevations from Open-Elevation")
                if (elevations.size == coords.size) elevations else null
            } else {
                logWarn("Open-Elevation returned HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            logWarn("Open-Elevation query failed: ${e.message}")
            null
        }
    }
}

