package io.civicpulse.reports.api;

import io.civicpulse.reports.application.AvailableActions;
import io.civicpulse.reports.application.NewReport;
import io.civicpulse.reports.application.ReportDetails;
import io.civicpulse.reports.application.ReportEdit;
import io.civicpulse.reports.application.ReportFilter;
import io.civicpulse.reports.application.ReportService;
import io.civicpulse.reports.application.ReportSummary;
import io.civicpulse.reports.application.TimelineEntry;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.web.PageResponse;
import io.civicpulse.shared.web.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
class ReportController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final int REPORTS_PER_HOUR = 20;

    private final ReportService reports;
    private final RateLimiter rateLimiter;

    ReportController(ReportService reports, RateLimiter rateLimiter) {
        this.reports = reports;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    ResponseEntity<ReportDetails> create(
            @Valid @RequestBody NewReport body,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = 100) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        rateLimiter.check("report", user.id().toString(), REPORTS_PER_HOUR, Duration.ofHours(1));
        ReportDetails report;
        try {
            report = reports.create(body, idempotencyKey, user);
        } catch (DataIntegrityViolationException e) {
            // two requests with the same key at the same time; the other one won
            if (idempotencyKey == null) {
                throw e;
            }
            report = reports.findSubmission(idempotencyKey, user).orElseThrow(() -> e);
        }
        return ResponseEntity.created(URI.create("/api/v1/reports/" + report.id()))
                .eTag(Etags.of(report.version()))
                .body(report);
    }

    @GetMapping
    PageResponse<ReportSummary> search(
            @RequestParam(required = false) Set<ReportStatus> status,
            @RequestParam(required = false) @Size(max = 50) String category,
            @RequestParam(required = false) @Size(max = 50) String district,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) @Pattern(regexp = "me") String assignee,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        ReportFilter filter = new ReportFilter(status, category, district, q, teamId, assignee != null);
        return reports.search(filter, userOrNull(jwt), page, size);
    }

    @GetMapping("/mine")
    PageResponse<ReportSummary> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return reports.mine(CurrentUser.from(jwt), page, size);
    }

    @GetMapping("/{id}")
    ResponseEntity<ReportDetails> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        ReportDetails report = reports.get(id, userOrNull(jwt));
        return ResponseEntity.ok().eTag(Etags.of(report.version())).body(report);
    }

    @PatchMapping("/{id}")
    ResponseEntity<ReportDetails> edit(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReportEdit body,
            @AuthenticationPrincipal Jwt jwt) {
        ReportDetails report = reports.edit(id, Etags.expectedVersion(ifMatch), body, CurrentUser.from(jwt));
        return ResponseEntity.ok().eTag(Etags.of(report.version())).body(report);
    }

    @GetMapping("/{id}/actions")
    AvailableActions actions(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return reports.actions(id, CurrentUser.from(jwt));
    }

    @GetMapping("/{id}/timeline")
    List<TimelineEntry> timeline(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return reports.timeline(id, userOrNull(jwt));
    }

    // public endpoints: no token means anonymous
    static CurrentUser userOrNull(Jwt jwt) {
        return jwt == null ? null : CurrentUser.from(jwt);
    }
}
