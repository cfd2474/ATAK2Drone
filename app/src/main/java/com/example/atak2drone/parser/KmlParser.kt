package com.example.atak2drone.parser

import com.example.atak2drone.domain.interfaces.IKmlParser
import com.example.atak2drone.model.Coordinate
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Single Responsibility Principle (SRP):
 * Responsible strictly for parsing KML files into structured polygon vertices and
 * formatting boundary polygons into KML format.
 */
class KmlParser : IKmlParser {

    override fun parsePolygon(kmlInputStream: InputStream): List<Coordinate> {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(kmlInputStream).apply {
            documentElement.normalize()
        }

        // 1. Search for Polygon coordinates (highest priority)
        val polygonList = doc.getElementsByTagNameNS("*", "Polygon")
        if (polygonList.length > 0) {
            val polygonEl = polygonList.item(0) as Element
            val coordsList = polygonEl.getElementsByTagNameNS("*", "coordinates")
            if (coordsList.length > 0) {
                val text = coordsList.item(0).textContent.trim()
                if (text.isNotEmpty()) {
                    val ring = parseCoordinateTextToLatLng(text)
                    if (ring.size >= 3) return ring
                }
            }
        }

        // 2. Search for LinearRing coordinates
        val linearRings = doc.getElementsByTagNameNS("*", "LinearRing")
        if (linearRings.length > 0) {
            val ringEl = linearRings.item(0) as Element
            val coordsList = ringEl.getElementsByTagNameNS("*", "coordinates")
            if (coordsList.length > 0) {
                val text = coordsList.item(0).textContent.trim()
                if (text.isNotEmpty()) {
                    val ring = parseCoordinateTextToLatLng(text)
                    if (ring.size >= 3) return ring
                }
            }
        }

        // 3. Detect LineString (not a closed polygon)
        val lineStrings = doc.getElementsByTagNameNS("*", "LineString")
        if (lineStrings.length > 0) {
            throw IllegalArgumentException(
                "KML contains a LineString but no closed Polygon/LinearRing. Draw a Polygon in ATAK for mapping."
            )
        }

        // 4. Fallback search for any coordinates tag
        val anyCoords = doc.getElementsByTagNameNS("*", "coordinates")
        if (anyCoords.length > 0) {
            val text = anyCoords.item(0).textContent.trim()
            if (text.isNotEmpty()) {
                val ring = parseCoordinateTextToLatLng(text)
                if (ring.size >= 3) return ring
            }
        }

        throw IllegalArgumentException("No usable geometry found in KML (Polygon/LinearRing/LineString not present).")
    }

    override fun buildMinimalPolygonKml(
        polygon: List<Coordinate>,
        name: String,
        description: String?
    ): String {
        require(polygon.size >= 3) { "Polygon must have at least 3 points." }

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

    private fun almostSame(a: Coordinate, b: Coordinate, eps: Double = 1e-9): Boolean =
        abs(a.latitude - b.latitude) < eps && abs(a.longitude - b.longitude) < eps

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
