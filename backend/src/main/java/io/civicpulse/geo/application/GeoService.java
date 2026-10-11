package io.civicpulse.geo.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.civicpulse.geo.District;
import io.civicpulse.geo.GeoDirectory;
import io.civicpulse.geo.NearbySite;
import io.civicpulse.geo.infrastructure.GeoQueries;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GeoService implements GeoDirectory {

    private final GeoQueries queries;
    private final ObjectMapper objectMapper;

    GeoService(GeoQueries queries, ObjectMapper objectMapper) {
        this.queries = queries;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<District> districtAt(double latitude, double longitude) {
        return queries.districtAt(latitude, longitude);
    }

    @Override
    public Optional<District> findDistrict(String code) {
        return queries.findDistrict(code);
    }

    @Override
    public List<District> districts() {
        return queries.districts();
    }

    @Override
    public List<NearbySite> criticalSitesNear(double latitude, double longitude, double radiusMeters) {
        return queries.criticalSitesNear(latitude, longitude, radiusMeters);
    }

    public List<DistrictView> districtsWithBoundaries() {
        return queries.boundaries().stream()
                .map(district -> new DistrictView(district.code(), district.name(), parse(district.boundary())))
                .toList();
    }

    public List<GeoQueries.Site> criticalSites() {
        return queries.criticalSites();
    }

    private JsonNode parse(String geoJson) {
        try {
            return objectMapper.readTree(geoJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PostGIS returned invalid GeoJSON", e);
        }
    }

    // boundary is a GeoJSON MultiPolygon
    public record DistrictView(String code, String name, JsonNode boundary) {
    }
}
