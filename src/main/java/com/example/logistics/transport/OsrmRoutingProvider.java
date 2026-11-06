package com.example.logistics.transport;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
@ConditionalOnProperty(name="transport.routing.provider", havingValue="osrm")
public class OsrmRoutingProvider implements RoutingProvider {
 private final RestClient client;
 public OsrmRoutingProvider(@Value("${transport.routing.osrm-base-url}") String baseUrl){this.client=RestClient.builder().baseUrl(baseUrl).build();}
 @Override public Route route(List<RouteOptimizer.Point> points){if(points.size()<2)return new Route(0,0,List.copyOf(points),"OSRM");String coordinates=String.join(";",points.stream().map(p->p.longitude()+","+p.latitude()).toList());JsonNode root=client.get().uri("/route/v1/driving/{coordinates}?overview=full&geometries=geojson",coordinates).retrieve().body(JsonNode.class);JsonNode route=root.path("routes").path(0);if(route.isMissingNode())throw new IllegalStateException("OSRM returned no route");List<RouteOptimizer.Point> geometry=new ArrayList<>();for(JsonNode c:route.path("geometry").path("coordinates"))geometry.add(new RouteOptimizer.Point(c.get(1).asDouble(),c.get(0).asDouble()));return new Route(route.path("distance").asDouble(),route.path("duration").asLong(),List.copyOf(geometry),"OSRM");}
}
