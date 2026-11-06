package com.example.logistics.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeofenceGeometryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void detectsCircleAndPolygonMembership() throws Exception {
        var circle = json.readTree("{\"center\":{\"lat\":40.0,\"lon\":-74.0},\"radiusM\":500}");
        var polygon = json.readTree("{\"points\":[{\"lat\":0,\"lon\":0},{\"lat\":0,\"lon\":2},{\"lat\":2,\"lon\":2},{\"lat\":2,\"lon\":0}]}");

        assertTrue(GeofenceGeometry.contains("CIRCLE", circle, 40.0, -74.0));
        assertFalse(GeofenceGeometry.contains("CIRCLE", circle, 41.0, -74.0));
        assertTrue(GeofenceGeometry.contains("POLYGON", polygon, 1.0, 1.0));
        assertFalse(GeofenceGeometry.contains("POLYGON", polygon, 3.0, 1.0));
    }

    @Test
    void rejectsMalformedGeometryAndOutOfBoundsCoordinates() throws Exception {
        var invalidCircle = json.readTree("{\"center\":{\"lat\":91,\"lon\":0},\"radiusM\":10}");
        var invalidPolygon = json.readTree("{\"points\":[{\"lat\":0,\"lon\":0},{\"lat\":0,\"lon\":1},{\"lat\":2,\"lon\":181}]}");

        assertThrows(IllegalArgumentException.class, () -> GeofenceGeometry.validate("CIRCLE", invalidCircle));
        assertThrows(IllegalArgumentException.class, () -> GeofenceGeometry.validate("POLYGON", invalidPolygon));
    }
}
