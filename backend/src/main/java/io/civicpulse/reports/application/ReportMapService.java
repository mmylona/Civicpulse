package io.civicpulse.reports.application;

import io.civicpulse.reports.infrastructure.ReportGeoQueries;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportMapService {

    static final int MARKER_ZOOM = 15;
    static final int MAX_ITEMS = 500;

    private final ReportGeoQueries queries;

    ReportMapService(ReportGeoQueries queries) {
        this.queries = queries;
    }

    public MapView map(BoundingBox box, int zoom, String categoryCode, boolean includeClosed) {
        box.checkSize(zoom);
        if (zoom >= MARKER_ZOOM) {
            List<ReportGeoQueries.Marker> markers = queries.markers(box.west(), box.south(), box.east(),
                    box.north(), categoryCode, includeClosed, MAX_ITEMS + 1);
            return new MapView(MapView.Mode.MARKERS, List.of(), first(markers), markers.size() > MAX_ITEMS);
        }
        List<ReportGeoQueries.Cluster> clusters = queries.clusters(box.west(), box.south(), box.east(),
                box.north(), cellSize(zoom), categoryCode, includeClosed, MAX_ITEMS + 1);
        return new MapView(MapView.Mode.CLUSTERS, first(clusters), List.of(), clusters.size() > MAX_ITEMS);
    }

    public List<ReportGeoQueries.Nearby> nearby(double latitude, double longitude, int radiusMeters,
            String categoryCode, int limit) {
        return queries.nearby(latitude, longitude, radiusMeters, categoryCode, limit);
    }

    // a quarter of a 256 px map tile, so roughly one cluster per 64 px
    static double cellSize(int zoom) {
        return 360.0 / (1L << zoom) / 4;
    }

    private static <T> List<T> first(List<T> items) {
        return items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items;
    }
}
