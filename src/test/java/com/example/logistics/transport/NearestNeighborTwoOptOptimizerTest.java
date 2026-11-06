package com.example.logistics.transport;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NearestNeighborTwoOptOptimizerTest {
 private final NearestNeighborTwoOptOptimizer optimizer=new NearestNeighborTwoOptOptimizer();
 @Test void ordersRouteAndReturnsEveryStopExactlyOnce(){
  var origin=new RouteOptimizer.Point(0,0);
  var stops=List.of(stop("a",0,2),stop("b",1,0),stop("c",0,1),stop("d",2,2));
  var result=optimizer.optimize(stops,origin);
  assertEquals(4,result.size());assertEquals(4,result.stream().map(RouteOptimizer.Stop::id).distinct().count());
  assertEquals(stops.stream().map(RouteOptimizer.Stop::id).sorted().toList(),result.stream().map(RouteOptimizer.Stop::id).sorted().toList());
 }
 @Test void emptyRouteIsValid(){assertTrue(optimizer.optimize(List.of(),new RouteOptimizer.Point(0,0)).isEmpty());}
 private RouteOptimizer.Stop stop(String id,double lat,double lon){return new RouteOptimizer.Stop(id,new RouteOptimizer.Point(lat,lon),0,0,0,0,0,true);}
}
