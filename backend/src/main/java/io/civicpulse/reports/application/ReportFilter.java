package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ReportStatus;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// teamId and assignedToMe are only applied for staff
public record ReportFilter(Set<ReportStatus> statuses, String categoryCode, String districtCode, String query,
        UUID teamId, boolean assignedToMe) {

    public ReportFilter {
        // "?status=A," gives a null element
        statuses = statuses == null
                ? Set.of()
                : statuses.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
