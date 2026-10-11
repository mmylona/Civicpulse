package io.civicpulse.reports.api;

import io.civicpulse.reports.application.BoundingBox;
import io.civicpulse.reports.application.MapView;
import io.civicpulse.reports.application.ReportMapService;
import io.civicpulse.reports.infrastructure.ReportGeoQueries;
import io.civicpulse.shared.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
class ReportMapController {

    // the map asks again on every pan and zoom
    private static final int REQUESTS_PER_MINUTE = 120;

    private final ReportMapService map;
    private final RateLimiter rateLimiter;

    ReportMapController(ReportMapService map, RateLimiter rateLimiter) {
        this.map = map;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/map")
    MapView map(
            @RequestParam @Size(max = 100) String bbox,
            @RequestParam @Min(0) @Max(22) int zoom,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(defaultValue = "false") boolean includeClosed,
            HttpServletRequest request) {
        rateLimiter.check("map", request.getRemoteAddr(), REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
        return map.map(BoundingBox.parse(bbox), zoom, category, includeClosed);
    }

    @GetMapping("/nearby")
    List<ReportGeoQueries.Nearby> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @RequestParam(defaultValue = "100") @Min(1) @Max(2000) int radius,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        rateLimiter.check("map", request.getRemoteAddr(), REQUESTS_PER_MINUTE, Duration.ofMinutes(1));
        return map.nearby(lat, lng, radius, category, limit);
    }
}
