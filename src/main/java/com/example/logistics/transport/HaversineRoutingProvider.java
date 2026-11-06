package com.example.logistics.transport;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;

@Component
@ConditionalOnProperty(name="transport.routing.provider", havingValue="haversine", matchIfMissing=true)
public class HaversineRoutingProvider implements RoutingProvider {
  @Override public Route route(List<RouteOptimizer.Point> points) {
    double meters=0; for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);double p=Math.toRadians(b.latitude()-a.latitude()),l=Math.toRadians(b.longitude()-a.longitude());double h=Math.sin(p/2)*Math.sin(p/2)+Math.cos(Math.toRadians(a.latitude()))*Math.cos(Math.toRadians(b.latitude()))*Math.sin(l/2)*Math.sin(l/2);meters+=6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    return new Route(meters,(long)(meters/11.11),List.copyOf(points),"HAVERSINE_ESTIMATE");
  }
}
