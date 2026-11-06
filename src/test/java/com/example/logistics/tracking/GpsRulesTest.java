package com.example.logistics.tracking;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class GpsRulesTest {
    private final Instant now=Instant.parse("2026-11-06T10:00:00Z");
    @Test void acceptsValidPositionAtCoordinateBoundaries(){assertDoesNotThrow(()->GpsRules.validate(now,90,-180,220.0,359.9,now));}
    @Test void rejectsCoordinatesOutsideWorldBounds(){assertThrows(IllegalArgumentException.class,()->GpsRules.validate(now,90.01,0,null,null,now));}
    @Test void rejectsTimestampSkewAndImpossibleSpeed(){assertThrows(IllegalArgumentException.class,()->GpsRules.validate(now.minusSeconds(601),0,0,20.0,null,now));assertThrows(IllegalArgumentException.class,()->GpsRules.validate(now,0,0,221.0,null,now));}
    @Test void rejectsNonFiniteCoordinates(){assertThrows(IllegalArgumentException.class,()->GpsRules.validate(now,Double.NaN,0,null,null,now));}
}
