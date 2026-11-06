package com.example.logistics.transport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TripStateMachineTest {
 @Test void allowsOnlyDeclaredLifecycleTransitions(){
  assertTrue(TripStateMachine.allows("PLANNED","CANCELLED"));
  assertTrue(TripStateMachine.allows("DISPATCHED","STARTED"));
  assertTrue(TripStateMachine.allows("STARTED","COMPLETED"));
  assertFalse(TripStateMachine.allows("PLANNED","STARTED"));
  assertFalse(TripStateMachine.allows("COMPLETED","CANCELLED"));
 }
}
