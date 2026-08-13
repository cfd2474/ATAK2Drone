package com.example.atak2drone

import com.example.atak2drone.model.Coordinate
import com.example.atak2drone.parser.KmlParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class KmlParserTest {

    private val kmlParser = KmlParser()

    @Test
    fun testParseValidPolygonKml() {
        val kmlXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Test Survey Area</name>
                <Placemark>
                  <name>Survey Polygon</name>
                  <Polygon>
                    <outerBoundaryIs>
                      <LinearRing>
                        <coordinates>
                          -122.4194,37.7749,0
                          -122.4100,37.7749,0
                          -122.4100,37.7790,0
                          -122.4194,37.7790,0
                          -122.4194,37.7749,0
                        </coordinates>
                      </LinearRing>
                    </outerBoundaryIs>
                  </Polygon>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val coords = kmlParser.parsePolygon(ByteArrayInputStream(kmlXml.toByteArray()))
        assertEquals(4, coords.size)
        assertEquals(37.7749, coords[0].latitude, 1e-6)
        assertEquals(-122.4194, coords[0].longitude, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectLineStringWithoutPolygon() {
        val lineStringXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <LineString>
                    <coordinates>
                      -122.4194,37.7749,0
                      -122.4100,37.7749,0
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        kmlParser.parsePolygon(ByteArrayInputStream(lineStringXml.toByteArray()))
    }

    @Test
    fun testBuildMinimalPolygonKmlRoundTrip() {
        val originalCoords = listOf(
            Coordinate(37.7749, -122.4194),
            Coordinate(37.7749, -122.4100),
            Coordinate(37.7790, -122.4100),
            Coordinate(37.7790, -122.4194)
        )

        val kml = kmlParser.buildMinimalPolygonKml(originalCoords, "RoundTripArea")
        assertTrue(kml.contains("<coordinates>"))
        assertTrue(kml.contains("-122.4194,37.7749,0"))

        val parsed = kmlParser.parsePolygon(ByteArrayInputStream(kml.toByteArray()))
        assertEquals(4, parsed.size)
        assertEquals(originalCoords[0].latitude, parsed[0].latitude, 1e-6)
        assertEquals(originalCoords[0].longitude, parsed[0].longitude, 1e-6)
    }
}
