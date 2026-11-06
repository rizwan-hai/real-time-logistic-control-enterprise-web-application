package com.example.logistics.transport;

import java.util.Map;
import java.util.Set;

public final class TripStateMachine {
 private static final Map<String,Set<String>> TRANSITIONS=Map.of("PLANNED",Set.of("CANCELLED"),"DISPATCHED",Set.of("STARTED","CANCELLED"),"STARTED",Set.of("COMPLETED","CANCELLED"));
 private TripStateMachine(){}
 public static boolean allows(String from,String to){return TRANSITIONS.getOrDefault(from,Set.of()).contains(to);}
}
