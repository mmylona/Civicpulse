package io.civicpulse.reports.infrastructure;

import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.Severity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Map and nearby queries. Measured on 100k reports, see docs/adr/005-map-and-nearby.md.
@Repository
public class ReportGeoQueries {

    // has to be written exactly like the predicate of reports_open_location_idx
    static final String OPEN = "status NOT IN ('CLOSED', 'REJECTED', 'DUPLICATE')";
    static final String NOT_DISCARDED = "status NOT IN ('REJECTED', 'DUPLICATE')";

    // && only compares bounding boxes; for a map view that is enough and much cheaper than ST_Intersects
    static final String IN_BOX = "location && ST_MakeEnvelope(:west, :south, :east, :north, 4326)::geography";

    static final String POINT = "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography";

    private static final String SEVERITY_RANK =
            "array_position(ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'], coalesce(validated_severity, stated_severity))";

    private final JdbcClient jdbc;

    ReportGeoQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Marker> markers(double west, double south, double east, double north, String categoryCode,
            boolean includeClosed, int limit) {
        return jdbc.sql(markersSql(categoryCode != null, includeClosed))
                .param("west", west)
                .param("south", south)
                .param("east", east)
                .param("north", north)
                .param("categoryCode", categoryCode)
                .param("limit", limit)
                .query(ReportGeoQueries::marker)
                .list();
    }

    public List<Cluster> clusters(double west, double south, double east, double north, double cellDegrees,
            String categoryCode, boolean includeClosed, int limit) {
        return jdbc.sql(clustersSql(categoryCode != null, includeClosed))
                .param("west", west)
                .param("south", south)
                .param("east", east)
                .param("north", north)
                .param("cell", cellDegrees)
                .param("categoryCode", categoryCode)
                .param("limit", limit)
                .query(ReportGeoQueries::cluster)
                .list();
    }

    public List<Nearby> nearby(double latitude, double longitude, double radiusMeters, String categoryCode,
            int limit) {
        return jdbc.sql(nearbySql(categoryCode != null))
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("radius", radiusMeters)
                .param("categoryCode", categoryCode)
                .param("limit", limit)
                .query(ReportGeoQueries::nearby)
                .list();
    }

    static String markersSql(boolean byCategory, boolean includeClosed) {
        return """
                SELECT id, reference, title, category_code, status, %s AS severity, latitude, longitude
                FROM reports
                WHERE %s AND %s%s
                ORDER BY severity DESC, created_at DESC
                LIMIT :limit
                """.formatted(SEVERITY_RANK, IN_BOX, includeClosed ? NOT_DISCARDED : OPEN, category(byCategory));
    }

    // cells are counted in grid squares of :cell degrees, after the index has narrowed the rows down
    static String clustersSql(boolean byCategory, boolean includeClosed) {
        return """
                SELECT count(*) AS count,
                       avg(latitude) AS latitude,
                       avg(longitude) AS longitude,
                       mode() WITHIN GROUP (ORDER BY category_code) AS category_code,
                       max(%s) AS severity,
                       CASE WHEN count(*) = 1 THEN (array_agg(id))[1] END AS report_id
                FROM reports
                WHERE %s AND %s%s
                GROUP BY floor(longitude / :cell), floor(latitude / :cell)
                ORDER BY count DESC
                LIMIT :limit
                """.formatted(SEVERITY_RANK, IN_BOX, includeClosed ? NOT_DISCARDED : OPEN, category(byCategory));
    }

    static String nearbySql(boolean byCategory) {
        return """
                SELECT id, reference, title, category_code, status, %s AS severity, latitude, longitude,
                       still_present_count, ST_Distance(location, %s) AS distance
                FROM reports
                WHERE ST_DWithin(location, %s, :radius) AND %s%s
                ORDER BY distance, id
                LIMIT :limit
                """.formatted(SEVERITY_RANK, POINT, POINT, OPEN, category(byCategory));
    }

    private static String category(boolean byCategory) {
        return byCategory ? " AND category_code = :categoryCode" : "";
    }

    private static Severity severity(ResultSet rs) throws SQLException {
        return Severity.values()[rs.getInt("severity") - 1];
    }

    private static Marker marker(ResultSet rs, int rowNum) throws SQLException {
        return new Marker(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("title"),
                rs.getString("category_code"),
                ReportStatus.valueOf(rs.getString("status")),
                severity(rs),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"));
    }

    private static Cluster cluster(ResultSet rs, int rowNum) throws SQLException {
        return new Cluster(
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getInt("count"),
                rs.getString("category_code"),
                severity(rs),
                rs.getObject("report_id", UUID.class));
    }

    private static Nearby nearby(ResultSet rs, int rowNum) throws SQLException {
        return new Nearby(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("title"),
                rs.getString("category_code"),
                ReportStatus.valueOf(rs.getString("status")),
                severity(rs),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getInt("still_present_count"),
                (int) Math.round(rs.getDouble("distance")));
    }

    public record Marker(
            UUID id,
            String reference,
            String title,
            String categoryCode,
            ReportStatus status,
            Severity severity,
            double latitude,
            double longitude) {
    }

    // reportId is set when the cluster is a single report
    public record Cluster(
            double latitude,
            double longitude,
            int count,
            String categoryCode,
            Severity severity,
            UUID reportId) {
    }

    public record Nearby(
            UUID id,
            String reference,
            String title,
            String categoryCode,
            ReportStatus status,
            Severity severity,
            double latitude,
            double longitude,
            int stillPresentCount,
            int distanceMeters) {
    }
}
