package com.example.logistics.transport;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

/** Pure validation policy; callers supply current vehicle, driver, and load snapshots. */
@Component
public class OperationalValidator {
 public List<String> validate(Load load, Vehicle vehicle, Driver driver, Duration plannedDrive, Duration plannedWork) {
  List<String> errors=new ArrayList<>();
  if(load.weight()>vehicle.weightCapacity())errors.add("WEIGHT_CAPACITY_EXCEEDED");
  if(load.volume()>vehicle.volumeCapacity())errors.add("VOLUME_CAPACITY_EXCEEDED");
  if(load.pallets()>vehicle.palletCapacity())errors.add("PALLET_CAPACITY_EXCEEDED");
  if(!vehicle.types().contains(load.requiredVehicleType()))errors.add("VEHICLE_TYPE_INCOMPATIBLE");
  if(load.hazardous()&&!vehicle.hazardousCertified())errors.add("HAZARDOUS_GOODS_UNSUPPORTED");
  if(load.refrigerated()&&!vehicle.refrigerated())errors.add("REFRIGERATION_REQUIRED");
  if(!driver.licenseClasses().containsAll(load.requiredLicenseClasses()))errors.add("LICENSE_CLASS_INCOMPATIBLE");
  if(!driver.available())errors.add("DRIVER_UNAVAILABLE");
  if(plannedDrive.compareTo(driver.remainingDrivingHours())>0)errors.add("DRIVING_HOURS_EXCEEDED");
  if(plannedWork.compareTo(driver.remainingWorkingHours())>0)errors.add("WORKING_HOURS_EXCEEDED");
  return List.copyOf(errors);
 }
 public record Load(double weight,double volume,int pallets,String requiredVehicleType,boolean hazardous,boolean refrigerated,Set<String> requiredLicenseClasses){}
 public record Vehicle(double weightCapacity,double volumeCapacity,int palletCapacity,Set<String> types,boolean hazardousCertified,boolean refrigerated){}
 public record Driver(Set<String> licenseClasses,boolean available,Duration remainingDrivingHours,Duration remainingWorkingHours){}
}
