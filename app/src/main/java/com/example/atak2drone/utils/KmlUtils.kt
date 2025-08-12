package com.example.atak2drone.utils

import com.example.atak2drone.model.Coordinate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.lang.StringBuilder
import kotlin.math.abs

object KmlUtils {

    /**
     * Single-pass parser:
     * - Scans once, namespace-aware
     * - Captures the first <coordinates> found under (in order of preference):
     *     1) <Polygon> (preferably its outer boundary, but any coordinates within Polygon are accepted)
     *     2) <LinearRing>
     *     3) <LineString>  (not valid for mapping; we detect and throw later)
     *
     * Returns a polygon ring as List<Coordinate> (lat/lon). Throws if none found, or only a LineString is present.
     */
    fun parseSinglePolygonFromKml(kmlInputStream: InputStream): List<Coordinate> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(kmlInputStream.reader()) }

        var event = parser.eventType

        // Track our current scope
        var inPolygon = false
        var inLinearRing = false
        var inLineString = false
        var scopeDepth = 0

        // First-found buckets
        var polygonCoords: String? = null
        var linearRingCoords: String? = null
        var lineStringCoords: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Polygon" -> {
                            inPolygon = true
                            scopeDepth = 1
                        }
                        "LinearRing" -> {
                            inLinearRing = true
                            scopeDepth = 1
                        }
                        "LineString" -> {
                            inLineString = true
                            scopeDepth = 1
                        }
                        "coordinates" -> {
                            // Grab text only if we are inside a scope, and we don't already have a higher-priority capture
                            val text = parser.nextText().trim()
                            if (text.isNotEmpty()) {
                                when {
                                    inPolygon && polygonCoords == null -> polygonCoords = text
                                    inLinearRing && linearRingCoords == null -> linearRingCoords = text
                                    inLineString && lineStringCoords == null -> lineStringCoords = text
                                }
                                // nextText() positions after END_TAG of coordinates; adjust depth below
                                if (inPolygon || inLinearRing || inLineString) {
                                    scopeDepth--
                                }
                            }
                        }
                        else -> {
                            if (inPolygon || inLinearRing || inLineString) scopeDepth++
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "Polygon" -> {
                            if (inPolygon) {
                                scopeDepth--
                                if (scopeDepth <= 0) inPolygon = false
                            }
                        }
                        "LinearRing" -> {
                            if (inLinearRing) {
                                scopeDepth--
                                if (scopeDepth <= 0) inLinearRing = false
                            }
                        }
                        "LineString" -> {
                            if (inLineString) {
                                scopeDepth--
                                if (scopeDepth <= 0) inLineString = false
                            }
                        }
                        else -> {
                            if (inPolygon || inLinearRing || inLineString) scopeDepth--
                        }
                    }
                }
            }
            event = parser.next()
        }

        // Decide winner by priority
        val chosen = polygonCoords ?: linearRingCoords ?: lineStringCoords
        if (chosen == null) {
            throw IllegalArgumentException("No usable geometry found in KML (Polygon/LinearRing/LineString not present).")
        }
        if (polygonCoords == null && linearRingCoords == null && lineStringCoords != null) {
            // Only a path, not an area – for 2D mapping we need a polygon.
            throw IllegalArgumentException(
                "KML contains a LineString but no Polygon/LinearRing. Draw a Polygon in ATAK for mapping."
            )
        }

        val ring = parseCoordinateTextToLatLng(chosen)
        validatePolygonOrThrow(ring)
        return ring
    }

    /**
     * Build a minimal DJI-friendly KML containing a single Placemark Polygon (outerBoundaryIs/LinearRing).
     * Use this as template.kml content for WPML packages.
     */
    fun buildMinimalPolygonKml(polygon: List<Coordinate>, name: String, description: String? = null): String {
        if (polygon.size < 3) throw IllegalArgumentException("Polygon must have at least 3 points.")

        val kmlNs = "http://www.opengis.net/kml/2.2"
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="$kmlNs">""").append('\n')
        sb.append("  <Document>\n")
        sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
        if (!description.isNullOrBlank()) {
            sb.append("    <description>").append(escapeXml(description)).append("</description>\n")
        }
        sb.append("    <Placemark>\n")
        sb.append("      <name>").append(escapeXml(name)).append("</name>\n")
        sb.append("      <Polygon>\n")
        sb.append("        <outerBoundaryIs>\n")
        sb.append("          <LinearRing>\n")
        sb.append("            <coordinates>\n")
        polygon.forEach { c ->
            // KML order: lon,lat[,alt]
            sb.append("              ")
                .append(c.longitude).append(",")
                .append(c.latitude).append(",0\n")
        }
        sb.append("            </coordinates>\n")
        sb.append("          </LinearRing>\n")
        sb.append("        </outerBoundaryIs>\n")
        sb.append("      </Polygon>\n")
        sb.append("    </Placemark>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    // -------------------- helpers --------------------

    /** Convert a KML <coordinates> string to a list of Coordinate(lat, lon). */
    private fun parseCoordinateTextToLatLng(coordText: String): List<Coordinate> {
        val pts = coordText
            .split(Regex("\\s+"))
            .asSequence()
            .mapNotNull { token ->
                if (token.isBlank()) return@mapNotNull null
                val parts = token.split(',')
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    if (lat != null && lon != null) Coordinate(latitude = lat, longitude = lon) else null
                } else null
            }
            .toList()

        if (pts.size >= 2 && almostSame(pts.first(), pts.last())) {
            return pts.dropLast(1)
        }
        return pts
    }

    private fun validatePolygonOrThrow(ring: List<Coordinate>) {
        if (ring.size < 3) {
            throw IllegalArgumentException("Polygon must have at least 3 unique points.")
        }
    }

    private fun almostSame(a: Coordinate, b: Coordinate, eps: Double = 1e-9): Boolean =
        abs(a.latitude - b.latitude) < eps && abs(a.longitude - b.longitude) < eps

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
