package com.example.logistics.transport;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class OperationalValidatorTest {
 private final OperationalValidator validator=new OperationalValidator();
 @Test void reportsCapacityCompatibilityAndHoursViolations(){
  var load=new OperationalValidator.Load(12,4,3,"REFRIGERATED",true,true,Set.of("C","HAZ"));
  var vehicle=new OperationalValidator.Vehicle(10,3,2,Set.of("VAN"),false,false);
  var driver=new OperationalValidator.Driver(Set.of("B"),false,Duration.ofHours(2),Duration.ofHours(3));
  var errors=validator.validate(load,vehicle,driver,Duration.ofHours(4),Duration.ofHours(5));
  assertEquals(Set.of("WEIGHT_CAPACITY_EXCEEDED","VOLUME_CAPACITY_EXCEEDED","PALLET_CAPACITY_EXCEEDED","VEHICLE_TYPE_INCOMPATIBLE","HAZARDOUS_GOODS_UNSUPPORTED","REFRIGERATION_REQUIRED","LICENSE_CLASS_INCOMPATIBLE","DRIVER_UNAVAILABLE","DRIVING_HOURS_EXCEEDED","WORKING_HOURS_EXCEEDED"),Set.copyOf(errors));
 }
}
