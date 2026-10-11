package io.civicpulse.reports.application;

import io.civicpulse.reports.infrastructure.ReportGeoQueries;
import java.util.List;

// below zoom 15 the map gets clusters, from 15 on single markers
public record MapView(
        Mode mode,
        List<ReportGeoQueries.Cluster> clusters,
        List<ReportGeoQueries.Marker> markers,
        boolean truncated) {

    public enum Mode {
        CLUSTERS,
        MARKERS
    }
}
