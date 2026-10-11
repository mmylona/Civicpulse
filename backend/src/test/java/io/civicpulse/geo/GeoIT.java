package io.civicpulse.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GeoIT {

    @Autowired
    private GeoDirectory geo;

    @Autowired
    private MockMvc mvc;

    @Test
    void findsTheDistrict() {
        assertThat(geo.districtAt(46.9479, 7.4478)).map(District::code).contains("innere-stadt");
        assertThat(geo.districtAt(46.9450, 7.3880)).map(District::code).contains("buempliz-oberbottigen");
        assertThat(geo.districtAt(46.9605, 7.4585)).map(District::code).contains("breitenrain-lorraine");
    }

    @Test
    void nothingOutsideTheCity() {
        // Zurich
        assertThat(geo.districtAt(47.3769, 8.5417)).isEmpty();
    }

    @Test
    void criticalSitesNearby() {
        List<NearbySite> sites = geo.criticalSitesNear(46.9492, 7.4393, 150);

        assertThat(sites).extracting(NearbySite::name).contains("Bern station");
        assertThat(sites).allSatisfy(site -> assertThat(site.distanceMeters()).isLessThanOrEqualTo(150));
        assertThat(geo.criticalSitesNear(46.9800, 7.3000, 150)).isEmpty();
    }

    @Test
    void districtsWithBoundariesArePublic() throws Exception {
        mvc.perform(get("/api/v1/geo/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].code", hasItem("innere-stadt")))
                .andExpect(jsonPath("$[0].boundary.type").value("MultiPolygon"));
    }

    @Test
    void criticalSitesArePublic() throws Exception {
        mvc.perform(get("/api/v1/geo/critical-sites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Inselspital")));
    }
}
