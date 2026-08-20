package com.example.atak2drone

import com.example.atak2drone.domain.datum.DatumConverter
import com.example.atak2drone.domain.model.SourceDatum
import com.example.atak2drone.model.Coordinate
import com.example.atak2drone.parser.KmlParser
import org.junit.Assert.*
import org.junit.Test

class DatumConverterTest {

    private val converter = DatumConverter()
    private val parser = KmlParser(converter)

    @Test
    fun testAutoDetectFromKmlText() {
        val wgs84Kml = "<kml><Document><name>Test</name></Document></kml>"
        val nad83Kml = "<kml><ExtendedData><SchemaData><SimpleData name=\"DATUM\">NAD83</SimpleData></SchemaData></ExtendedData></kml>"
        val nad27Kml = "<kml><ExtendedData><SimpleData name=\"DATUM\">NAD27</SimpleData></ExtendedData></kml>"
        val gcj02Kml = "<kml><ExtendedData><SimpleData name=\"DATUM\">GCJ-02</SimpleData></ExtendedData></kml>"

        assertEquals(SourceDatum.WGS84, converter.detectDatumFromKml(wgs84Kml))
        assertEquals(SourceDatum.NAD83, converter.detectDatumFromKml(nad83Kml))
        assertEquals(SourceDatum.NAD27, converter.detectDatumFromKml(nad27Kml))
        assertEquals(SourceDatum.GCJ02, converter.detectDatumFromKml(gcj02Kml))
    }

    @Test
    fun testNad83ToWgs84Conversion() {
        // San Diego test coordinate in NAD83
        val nad83Coord = Coordinate(latitude = 32.7157, longitude = -117.1611)
        val wgs84Coord = converter.convertToWgs84(nad83Coord, SourceDatum.NAD83)

        // Shift should be within realistic 1-2 meters (~0.00001 to 0.00002 degrees)
        assertFalse("Converted coordinate should not be identical to NAD83", nad83Coord == wgs84Coord)
        assertEquals(nad83Coord.latitude, wgs84Coord.latitude, 0.01)
        assertEquals(nad83Coord.longitude, wgs84Coord.longitude, 0.01)
    }

    @Test
    fun testNad27ToWgs84Conversion() {
        val nad27Coord = Coordinate(latitude = 32.7157, longitude = -117.1611)
        val wgs84Coord = converter.convertToWgs84(nad27Coord, SourceDatum.NAD27)

        assertFalse("Converted coordinate should not be identical to NAD27", nad27Coord == wgs84Coord)
        assertEquals(nad27Coord.latitude, wgs84Coord.latitude, 0.05)
        assertEquals(nad27Coord.longitude, wgs84Coord.longitude, 0.05)
    }

    @Test
    fun testGcj02ToWgs84Conversion() {
        // China coordinate in GCJ-02
        val gcjCoord = Coordinate(latitude = 39.9042, longitude = 116.4074)
        val wgs84Coord = converter.convertToWgs84(gcjCoord, SourceDatum.GCJ02)

        assertFalse("Converted coordinate should not be identical to GCJ-02", gcjCoord == wgs84Coord)
        assertEquals(gcjCoord.latitude, wgs84Coord.latitude, 0.01)
        assertEquals(gcjCoord.longitude, wgs84Coord.longitude, 0.01)
    }

    @Test
    fun testKmlParserAutomatedDatumConversion() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <ExtendedData>
                  <SimpleData name="DATUM">NAD83</SimpleData>
                </ExtendedData>
                <Placemark>
                  <Polygon>
                    <outerBoundaryIs>
                      <LinearRing>
                        <coordinates>
                          -117.1611,32.7157,0
                          -117.1600,32.7157,0
                          -117.1600,32.7165,0
                          -117.1611,32.7165,0
                        </coordinates>
                      </LinearRing>
                    </outerBoundaryIs>
                  </Polygon>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val parsedCoords = parser.parsePolygon(kmlContent.byteInputStream())
        assertEquals(4, parsedCoords.size)
        // Verify converted coords are valid WGS84
        assertTrue(parsedCoords[0].latitude in 32.0..33.0)
        assertTrue(parsedCoords[0].longitude in -118.0..-116.0)
    }
}
