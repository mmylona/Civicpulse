package io.civicpulse.geo.api;

import io.civicpulse.geo.application.GeoService;
import io.civicpulse.geo.infrastructure.GeoQueries;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geo")
class GeoController {

    private final GeoService geo;

    GeoController(GeoService geo) {
        this.geo = geo;
    }

    @GetMapping("/districts")
    List<GeoService.DistrictView> districts() {
        return geo.districtsWithBoundaries();
    }

    @GetMapping("/critical-sites")
    List<GeoQueries.Site> criticalSites() {
        return geo.criticalSites();
    }
}
