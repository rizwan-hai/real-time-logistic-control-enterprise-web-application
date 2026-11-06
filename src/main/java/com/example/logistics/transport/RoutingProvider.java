package com.example.logistics.transport;

import java.util.List;

/** Adapter boundary for OSRM, Google Directions, and the development fallback. */
public interface RoutingProvider {
  Route route(List<RouteOptimizer.Point> points);
  record Route(double distanceMeters, long durationSeconds, List<RouteOptimizer.Point> geometry, String provider) {}
}
