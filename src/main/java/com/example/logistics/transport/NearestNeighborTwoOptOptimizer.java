package com.example.logistics.transport;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class NearestNeighborTwoOptOptimizer implements RouteOptimizer {
  @Override public List<Stop> optimize(List<Stop> input, Point origin) {
    List<Stop> remaining = new ArrayList<>(input);
    List<Stop> route = new ArrayList<>(); Point cursor = origin;
    while (!remaining.isEmpty()) {
      Point from = cursor;
      Stop next = remaining.stream().min(Comparator.<Stop>comparingDouble(s -> distance(from, s.point()))
          .thenComparingLong(s -> s.windowEndEpochSeconds() == 0 ? Long.MAX_VALUE : s.windowEndEpochSeconds())).orElseThrow();
      route.add(next); remaining.remove(next); cursor = next.point();
    }
    boolean changed = true;
    while (changed) { changed = false;
      for (int i=0; i<route.size()-1 && !changed; i++) for (int k=i+1; k<route.size(); k++) {
        Point a = i==0 ? origin : route.get(i-1).point(); Point b=route.get(i).point();
        Point c=route.get(k).point(); Point d=k+1<route.size()?route.get(k+1).point():null;
        double old=distance(a,b)+(d==null?0:distance(c,d));
        double replacement=distance(a,c)+(d==null?0:distance(b,d));
        if (replacement+1e-9<old) { Collections.reverse(route.subList(i,k+1)); changed=true; break; }
      }
    }
    return List.copyOf(route);
  }
  private double distance(Point a, Point b) { double x=Math.toRadians(b.longitude()-a.longitude())*Math.cos(Math.toRadians((a.latitude()+b.latitude())/2)); double y=Math.toRadians(b.latitude()-a.latitude()); return Math.hypot(x,y); }
}
