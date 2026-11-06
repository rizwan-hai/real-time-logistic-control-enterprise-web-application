package com.example.logistics.transport;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
@ConditionalOnProperty(name="transport.routing.provider", havingValue="google-directions")
public class GoogleDirectionsRoutingProvider implements RoutingProvider {
 private final RestClient client; private final String key;
 public GoogleDirectionsRoutingProvider(@Value("${transport.routing.google-base-url:https://maps.googleapis.com/maps/api}") String baseUrl,@Value("${transport.routing.google-api-key}") String key){this.client=RestClient.builder().baseUrl(baseUrl).build();this.key=key;}
 @Override public Route route(List<RouteOptimizer.Point> points){if(points.size()<2)return new Route(0,0,List.copyOf(points),"GOOGLE_DIRECTIONS");var first=points.getFirst();var last=points.getLast();String origin=first.latitude()+","+first.longitude(),destination=last.latitude()+","+last.longitude();String waypoints=points.size()>2?String.join("|",points.subList(1,points.size()-1).stream().map(p->p.latitude()+","+p.longitude()).toList()):null;JsonNode root=client.get().uri(uri->{var b=uri.path("/directions/json").queryParam("origin",origin).queryParam("destination",destination).queryParam("key",key);if(waypoints!=null)b.queryParam("waypoints",waypoints);return b.build();}).retrieve().body(JsonNode.class);if(!"OK".equals(root.path("status").asText()))throw new IllegalStateException("Google Directions failed: "+root.path("status").asText());JsonNode legs=root.path("routes").path(0).path("legs");double meters=0;long seconds=0;for(JsonNode leg:legs){meters+=leg.path("distance").path("value").asDouble();seconds+=leg.path("duration").path("value").asLong();}return new Route(meters,seconds,List.copyOf(points),"GOOGLE_DIRECTIONS");}
}
