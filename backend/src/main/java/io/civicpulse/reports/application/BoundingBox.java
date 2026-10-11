package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.shared.error.ApiException;

// bbox=west,south,east,north in degrees, like Leaflet's toBBoxString()
public record BoundingBox(double west, double south, double east, double north) {

    public static BoundingBox parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw invalid();
        }
        double[] numbers = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                numbers[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw invalid();
            }
        }
        BoundingBox box = new BoundingBox(numbers[0], numbers[1], numbers[2], numbers[3]);
        if (!(box.west >= -180 && box.east <= 180 && box.south >= -90 && box.north <= 90
                && box.west < box.east && box.south < box.north)) {
            throw invalid();
        }
        return box;
    }

    // a screen shows about half a degree at zoom 12; this allows a lot more but stops world-sized queries
    static double maxSpan(int zoom) {
        return Math.min(10, 9000.0 / (1L << zoom));
    }

    void checkSize(int zoom) {
        if (east - west > maxSpan(zoom) || north - south > maxSpan(zoom)) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ReportErrorCode.INVALID_BOUNDING_BOX);
    }
}
