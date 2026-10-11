package io.civicpulse.geo;

import java.util.List;
import java.util.Optional;

// what other modules may ask about places in the city
public interface GeoDirectory {

    // empty when the point is outside the city
    Optional<District> districtAt(double latitude, double longitude);

    Optional<District> findDistrict(String code);

    List<District> districts();

    List<NearbySite> criticalSitesNear(double latitude, double longitude, double radiusMeters);
}
