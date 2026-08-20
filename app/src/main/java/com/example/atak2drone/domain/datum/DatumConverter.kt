package com.example.atak2drone.domain.datum

import com.example.atak2drone.domain.interfaces.IDatumConverter
import com.example.atak2drone.domain.model.SourceDatum
import com.example.atak2drone.model.Coordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Single Responsibility Principle (SRP):
 * 100% automated datum detection and transformation into standard WGS 84 (EPSG:4326).
 */
class DatumConverter : IDatumConverter {

    companion object {
        // WGS84 Ellipsoid constants
        private const val WGS84_A = 6378137.0
        private const val WGS84_F = 1.0 / 298.257223563

        // GRS80 / NAD83 Ellipsoid constants
        private const val GRS80_A = 6378137.0
        private const val GRS80_F = 1.0 / 298.257222101
        // NAD83 -> WGS84 Helmert Translation Parameters (meters)
        private const val NAD83_DX = 0.9956
        private const val NAD83_DY = -1.9013
        private const val NAD83_DZ = -0.5215

        // NAD27 / Clarke 1866 Ellipsoid constants
        private const val CLARKE1866_A = 6378206.4
        private const val CLARKE1866_F = 1.0 / 294.9786982
        // NAD27 -> WGS84 Molodensky Translation Parameters (CONUS average)
        private const val NAD27_DX = -8.0
        private const val NAD27_DY = 160.0
        private const val NAD27_DZ = 176.0

        // GCJ-02 (China Mars) constants
        private const val GCJ_A = 6378245.0
        private const val GCJ_EE = 0.00669342162296594323
    }

    override fun detectDatumFromKml(kmlContent: String): SourceDatum {
        val upperText = kmlContent.uppercase()

        return when {
            upperText.contains("NAD83") || upperText.contains("EPSG:4269") || upperText.contains("EPSG:26910") -> SourceDatum.NAD83
            upperText.contains("NAD27") || upperText.contains("EPSG:4267") || upperText.contains("CLARKE 1866") -> SourceDatum.NAD27
            upperText.contains("GCJ02") || upperText.contains("GCJ-02") || upperText.contains("MARS COORDINATE") -> SourceDatum.GCJ02
            upperText.contains("ETRS89") || upperText.contains("EPSG:4258") -> SourceDatum.ETRS89
            else -> SourceDatum.WGS84
        }
    }

    override fun convertToWgs84(coord: Coordinate, sourceDatum: SourceDatum): Coordinate {
        return when (sourceDatum) {
            SourceDatum.WGS84, SourceDatum.ETRS89 -> coord
            SourceDatum.AUTO_DETECT -> coord
            SourceDatum.NAD83 -> convertNad83ToWgs84(coord)
            SourceDatum.NAD27 -> convertNad27ToWgs84(coord)
            SourceDatum.GCJ02 -> convertGcj02ToWgs84(coord)
        }
    }

    override fun convertPolygonToWgs84(polygon: List<Coordinate>, sourceDatum: SourceDatum): List<Coordinate> {
        if (sourceDatum == SourceDatum.WGS84 || sourceDatum == SourceDatum.ETRS89) {
            return polygon
        }
        return polygon.map { convertToWgs84(it, sourceDatum) }
    }

    // ---------- NAD83 -> WGS84 Helmert Transformation ----------

    private fun convertNad83ToWgs84(coord: Coordinate): Coordinate {
        val latRad = Math.toRadians(coord.latitude)
        val lonRad = Math.toRadians(coord.longitude)

        // Convert Geodetic (lat, lon, h=0) to Geocentric Cartesian (X, Y, Z) on GRS80
        val e2 = 2.0 * GRS80_F - GRS80_F * GRS80_F
        val n = GRS80_A / sqrt(1.0 - e2 * sin(latRad) * sin(latRad))

        val x0 = n * cos(latRad) * cos(lonRad)
        val y0 = n * cos(latRad) * sin(lonRad)
        val z0 = n * (1.0 - e2) * sin(latRad)

        // Apply Helmert translation offset
        val x1 = x0 + NAD83_DX
        val y1 = y0 + NAD83_DY
        val z1 = z0 + NAD83_DZ

        // Convert back from Cartesian (X, Y, Z) to Geodetic (lat, lon) on WGS84
        val wgsE2 = 2.0 * WGS84_F - WGS84_F * WGS84_F
        val p = sqrt(x1 * x1 + y1 * y1)

        var newLatRad = Math.atan2(z1, p * (1.0 - wgsE2))
        for (i in 0..4) {
            val sinL = sin(newLatRad)
            val nWgs = WGS84_A / sqrt(1.0 - wgsE2 * sinL * sinL)
            newLatRad = Math.atan2(z1 + wgsE2 * nWgs * sinL, p)
        }

        val newLonRad = Math.atan2(y1, x1)
        return Coordinate(
            latitude = Math.toDegrees(newLatRad),
            longitude = Math.toDegrees(newLonRad)
        )
    }

    // ---------- NAD27 -> WGS84 Molodensky Transformation ----------

    private fun convertNad27ToWgs84(coord: Coordinate): Coordinate {
        val phi = Math.toRadians(coord.latitude)
        val lam = Math.toRadians(coord.longitude)

        val a = CLARKE1866_A
        val f = CLARKE1866_F
        val da = WGS84_A - CLARKE1866_A
        val df = WGS84_F - CLARKE1866_F

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val sinLam = sin(lam)
        val cosLam = cos(lam)
        val sin2Phi = sin(2.0 * phi)

        val e2 = 2.0 * f - f * f
        val rm = a * (1.0 - e2) / Math.pow(1.0 - e2 * sinPhi * sinPhi, 1.5)
        val rn = a / sqrt(1.0 - e2 * sinPhi * sinPhi)

        val dPhiRad = (-NAD27_DX * sinPhi * cosLam - NAD27_DY * sinPhi * sinLam + NAD27_DZ * cosPhi +
                (a * df + f * da) * sin2Phi) / rm

        val dLamRad = (-NAD27_DX * sinLam + NAD27_DY * cosLam) / (rn * cosPhi)

        return Coordinate(
            latitude = coord.latitude + Math.toDegrees(dPhiRad),
            longitude = coord.longitude + Math.toDegrees(dLamRad)
        )
    }

    // ---------- GCJ-02 -> WGS84 Inverse Transformation ----------

    private fun convertGcj02ToWgs84(coord: Coordinate): Coordinate {
        val (dLat, dLon) = transformGcjOffset(coord.latitude, coord.longitude)
        return Coordinate(
            latitude = coord.latitude - dLat,
            longitude = coord.longitude - dLon
        )
    }

    private fun transformGcjOffset(lat: Double, lon: Double): Pair<Double, Double> {
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)

        val radLat = Math.toRadians(lat)
        var magic = sin(radLat)
        magic = 1.0 - GCJ_EE * magic * magic
        val sqrtMagic = sqrt(magic)

        dLat = (dLat * 180.0) / ((GCJ_A * (1.0 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * Math.PI)

        return Pair(dLat, dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
