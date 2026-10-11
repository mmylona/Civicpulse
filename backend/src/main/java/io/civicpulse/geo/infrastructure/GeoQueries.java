package io.civicpulse.geo.infrastructure;

import io.civicpulse.geo.District;
import io.civicpulse.geo.NearbySite;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GeoQueries {

    private static final String POINT = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)";

    private final JdbcClient jdbc;

    GeoQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<District> districtAt(double latitude, double longitude) {
        // on a shared edge both districts match; take the first so the answer is stable
        return jdbc.sql("SELECT code, name FROM districts WHERE ST_Covers(boundary, %s) ORDER BY code LIMIT 1"
                        .formatted(POINT))
                .param("latitude", latitude)
                .param("longitude", longitude)
                .query((rs, rowNum) -> new District(rs.getString("code"), rs.getString("name")))
                .optional();
    }

    public Optional<District> findDistrict(String code) {
        return jdbc.sql("SELECT code, name FROM districts WHERE code = :code")
                .param("code", code)
                .query((rs, rowNum) -> new District(rs.getString("code"), rs.getString("name")))
                .optional();
    }

    public List<District> districts() {
        return jdbc.sql("SELECT code, name FROM districts ORDER BY name")
                .query((rs, rowNum) -> new District(rs.getString("code"), rs.getString("name")))
                .list();
    }

    public List<DistrictBoundary> boundaries() {
        return jdbc.sql("SELECT code, name, ST_AsGeoJSON(boundary, 6) AS boundary FROM districts ORDER BY name")
                .query((rs, rowNum) -> new DistrictBoundary(rs.getString("code"), rs.getString("name"),
                        rs.getString("boundary")))
                .list();
    }

    public List<NearbySite> criticalSitesNear(double latitude, double longitude, double radiusMeters) {
        return jdbc.sql("""
                        SELECT kind, name, weight, ST_Distance(location, %1$s::geography) AS distance
                        FROM critical_sites
                        WHERE ST_DWithin(location, %1$s::geography, :radius)
                        ORDER BY distance
                        """.formatted(POINT))
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("radius", radiusMeters)
                .query((rs, rowNum) -> new NearbySite(rs.getString("kind"), rs.getString("name"), rs.getInt("weight"),
                        (int) Math.round(rs.getDouble("distance"))))
                .list();
    }

    public List<Site> criticalSites() {
        return jdbc.sql("""
                        SELECT kind, name, weight,
                               ST_Y(location::geometry) AS latitude, ST_X(location::geometry) AS longitude
                        FROM critical_sites
                        ORDER BY kind, name
                        """)
                .query((rs, rowNum) -> new Site(rs.getString("kind"), rs.getString("name"), rs.getInt("weight"),
                        rs.getDouble("latitude"), rs.getDouble("longitude")))
                .list();
    }

    // boundary is GeoJSON
    public record DistrictBoundary(String code, String name, String boundary) {
    }

    public record Site(String kind, String name, int weight, double latitude, double longitude) {
    }
}
