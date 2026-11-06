package com.example.logistics.tracking;

import java.time.Duration;
import java.time.Instant;

public final class GpsRules {
    private GpsRules(){}
    public static void validate(Instant recordedAt,double latitude,double longitude,Double speedKph,Double heading,Instant now){
        if(recordedAt==null||Math.abs(Duration.between(recordedAt,now).getSeconds())>600)throw new IllegalArgumentException("Position timestamp is outside the 10 minute acceptance window");
        if(!Double.isFinite(latitude)||latitude < -90||latitude>90||!Double.isFinite(longitude)||longitude < -180||longitude>180)throw new IllegalArgumentException("Coordinates are outside valid bounds");
        if(speedKph!=null&&(!Double.isFinite(speedKph)||speedKph<0||speedKph>220))throw new IllegalArgumentException("Speed exceeds vehicle sanity limit");
        if(heading!=null&&(!Double.isFinite(heading)||heading<0||heading>=360))throw new IllegalArgumentException("Heading must be in [0,360)");
    }
}
