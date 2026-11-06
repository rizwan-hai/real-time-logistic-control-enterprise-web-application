package com.example.logistics.tracking;

import com.fasterxml.jackson.databind.JsonNode;

public final class GeofenceGeometry {
    private GeofenceGeometry() {}

    public static void validate(String kind, JsonNode shape) {
        if ("CIRCLE".equals(kind)) {
            JsonNode center = shape == null ? null : shape.get("center");
            if (center == null || !coordinate(center.get("lat"), -90, 90)
                    || !coordinate(center.get("lon"), -180, 180)) {
                throw new IllegalArgumentException("Circle center must contain valid lat and lon coordinates");
            }
            JsonNode radius = shape.get("radiusM");
            if (radius == null || !radius.isNumber() || !Double.isFinite(radius.asDouble())
                    || radius.asDouble() <= 0 || radius.asDouble() > 100_000) {
                throw new IllegalArgumentException("Circle radiusM must be between 0 and 100000 meters");
            }
            return;
        }
        if ("POLYGON".equals(kind)) {
            JsonNode points = shape == null ? null : shape.get("points");
            if (points == null || !points.isArray() || points.size() < 3 || points.size() > 1000) {
                throw new IllegalArgumentException("Polygon requires between 3 and 1000 points");
            }
            for (JsonNode point : points) {
                if (!coordinate(point.get("lat"), -90, 90) || !coordinate(point.get("lon"), -180, 180)) {
                    throw new IllegalArgumentException("Every polygon point must contain valid lat and lon coordinates");
                }
            }
            return;
        }
        throw new IllegalArgumentException("Geofence kind must be CIRCLE or POLYGON");
    }

    public static boolean contains(String kind, JsonNode shape, double latitude, double longitude) {
        validate(kind, shape);
        if ("CIRCLE".equals(kind)) {
            JsonNode center = shape.get("center");
            return distanceMeters(latitude, longitude, center.get("lat").asDouble(), center.get("lon").asDouble())
                    <= shape.get("radiusM").asDouble();
        }
        JsonNode points = shape.get("points");
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double yi = points.get(i).get("lat").asDouble();
            double xi = points.get(i).get("lon").asDouble();
            double yj = points.get(j).get("lat").asDouble();
            double xj = points.get(j).get("lon").asDouble();
            if ((yi > latitude) != (yj > latitude)
                    && longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean coordinate(JsonNode value, double minimum, double maximum) {
        return value != null && value.isNumber() && Double.isFinite(value.asDouble())
                && value.asDouble() >= minimum && value.asDouble() <= maximum;
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double x = Math.toRadians(lat2 - lat1);
        double y = Math.toRadians(lon2 - lon1);
        double h = Math.sin(x / 2) * Math.sin(x / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(y / 2) * Math.sin(y / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
