package io.civicpulse.reports.domain;

import static io.civicpulse.reports.domain.Performer.MODERATOR;
import static io.civicpulse.reports.domain.Performer.OPERATOR;
import static io.civicpulse.reports.domain.Performer.REPORTER;
import static io.civicpulse.reports.domain.Performer.SYSTEM;
import static io.civicpulse.reports.domain.Performer.TEAM_OPERATOR;
import static io.civicpulse.reports.domain.ReportStatus.ASSIGNED;
import static io.civicpulse.reports.domain.ReportStatus.CLOSED;
import static io.civicpulse.reports.domain.ReportStatus.DUPLICATE;
import static io.civicpulse.reports.domain.ReportStatus.IN_PROGRESS;
import static io.civicpulse.reports.domain.ReportStatus.REJECTED;
import static io.civicpulse.reports.domain.ReportStatus.REPORTED;
import static io.civicpulse.reports.domain.ReportStatus.RESOLVED;
import static io.civicpulse.reports.domain.ReportStatus.TRIAGED;
import static io.civicpulse.reports.domain.ReportStatus.WAITING_FOR_INFORMATION;

import java.util.EnumSet;
import java.util.Set;

// The lifecycle table. Keep in sync with docs/adr/004-report-lifecycle.md
// and src/test/resources/report-lifecycle.csv.
public enum ReportAction {

    TRIAGE(EnumSet.of(REPORTED), TRIAGED, EnumSet.of(MODERATOR, OPERATOR)),
    REJECT(EnumSet.of(REPORTED, TRIAGED), REJECTED, EnumSet.of(MODERATOR)),
    MARK_DUPLICATE(EnumSet.of(REPORTED, TRIAGED), DUPLICATE, EnumSet.of(MODERATOR, OPERATOR)),
    REQUEST_INFORMATION(EnumSet.of(REPORTED, TRIAGED, ASSIGNED, IN_PROGRESS), WAITING_FOR_INFORMATION,
            EnumSet.of(MODERATOR, OPERATOR)),
    // these two go back to the status the report had before
    PROVIDE_INFORMATION(EnumSet.of(WAITING_FOR_INFORMATION), null, EnumSet.of(REPORTER)),
    RESUME(EnumSet.of(WAITING_FOR_INFORMATION), null, EnumSet.of(MODERATOR, OPERATOR)),
    ASSIGN(EnumSet.of(TRIAGED), ASSIGNED, EnumSet.of(OPERATOR)),
    UNASSIGN(EnumSet.of(ASSIGNED), TRIAGED, EnumSet.of(OPERATOR)),
    START(EnumSet.of(ASSIGNED), IN_PROGRESS, EnumSet.of(TEAM_OPERATOR)),
    HAND_BACK(EnumSet.of(IN_PROGRESS), ASSIGNED, EnumSet.of(TEAM_OPERATOR)),
    RESOLVE(EnumSet.of(IN_PROGRESS), RESOLVED, EnumSet.of(TEAM_OPERATOR)),
    REOPEN(EnumSet.of(RESOLVED), ASSIGNED, EnumSet.of(REPORTER)),
    CLOSE(EnumSet.of(RESOLVED), CLOSED, EnumSet.of(OPERATOR, SYSTEM)),
    RESTORE(EnumSet.of(REJECTED, DUPLICATE), TRIAGED, EnumSet.of(MODERATOR));

    static final Set<ReportStatus> RESUMABLE = EnumSet.of(REPORTED, TRIAGED, ASSIGNED, IN_PROGRESS);

    private final Set<ReportStatus> from;
    private final ReportStatus target;
    private final Set<Performer> performers;

    ReportAction(Set<ReportStatus> from, ReportStatus target, Set<Performer> performers) {
        this.from = from;
        this.target = target;
        this.performers = performers;
    }

    public boolean isAvailableFrom(ReportStatus status) {
        return from.contains(status);
    }

    public Set<ReportStatus> from() {
        return EnumSet.copyOf(from);
    }

    public Set<ReportStatus> targets() {
        return target == null ? EnumSet.copyOf(RESUMABLE) : EnumSet.of(target);
    }

    public boolean returnsToPreviousStatus() {
        return target == null;
    }

    public Set<Performer> performers() {
        return EnumSet.copyOf(performers);
    }

    // URL segment, e.g. REQUEST_INFORMATION -> request-information
    public String path() {
        return name().toLowerCase().replace('_', '-');
    }
}
