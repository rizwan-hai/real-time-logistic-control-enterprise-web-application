package com.example.logistics.transport;

import java.util.*;

/** Heuristic route ordering. Results are practical improvements, not globally optimal routes. */
public interface RouteOptimizer {
  List<Stop> optimize(List<Stop> stops, Point origin);
  record Point(double latitude, double longitude) {}
  record Stop(String id, Point point, long windowStartEpochSeconds, long windowEndEpochSeconds, double weight, double volume, int pallets, boolean required) {}
}
