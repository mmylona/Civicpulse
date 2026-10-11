package io.civicpulse.reports.application;

import io.civicpulse.identity.UserDirectory;
import io.civicpulse.reports.domain.ConfirmationKind;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.infrastructure.Confirmations;
import io.civicpulse.reports.infrastructure.ReportRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ReportViews {

    // "still there" confirmations after a report was resolved before staff are told about it
    static final int REOPEN_REQUEST_THRESHOLD = 2;

    private final UserDirectory directory;
    private final ReportRepository reports;
    private final Confirmations confirmations;

    ReportViews(UserDirectory directory, ReportRepository reports, Confirmations confirmations) {
        this.directory = directory;
        this.reports = reports;
        this.confirmations = confirmations;
    }

    ReportDetails details(Report report, Viewer viewer) {
        ReportDetails.Link duplicateOf = report.duplicateOfId() == null
                ? null
                : reports.findById(report.duplicateOfId())
                        .map(original -> new ReportDetails.Link(original.id(), original.reference()))
                        .orElse(null);
        ConfirmationKind myConfirmation = viewer.isSignedIn()
                ? confirmations.find(report.id(), viewer.userId()).orElse(null)
                : null;
        return new ReportDetails(
                report.id(),
                report.reference(),
                report.title(),
                report.description(),
                report.categoryCode(),
                report.status(),
                report.severity(),
                report.latitude(),
                report.longitude(),
                report.address(),
                duplicateOf,
                report.stillPresentCount(),
                report.fixedCount(),
                report.createdAt(),
                report.updatedAt(),
                report.resolvedAt(),
                report.closedAt(),
                report.version(),
                viewer.is(report.reporterId()),
                myConfirmation,
                viewer.isStaff() ? staff(report) : null);
    }

    private ReportDetails.Staff staff(Report report) {
        Set<UUID> people = new HashSet<>();
        people.add(report.reporterId());
        if (report.assigneeId() != null) {
            people.add(report.assigneeId());
        }
        Map<UUID, String> names = directory.displayNames(people);
        ReportDetails.Team team = report.teamId() == null
                ? null
                : directory.findTeam(report.teamId())
                        .map(found -> new ReportDetails.Team(found.id(), found.name()))
                        .orElse(null);
        boolean reopenRequested = report.status() == ReportStatus.RESOLVED
                && report.resolvedAt() != null
                && confirmations.stillPresentSince(report.id(), report.resolvedAt()) >= REOPEN_REQUEST_THRESHOLD;
        return new ReportDetails.Staff(
                person(report.reporterId(), names),
                report.statedSeverity(),
                report.validatedSeverity(),
                team,
                person(report.assigneeId(), names),
                report.resumeStatus(),
                reopenRequested);
    }

    private static ReportDetails.Person person(UUID id, Map<UUID, String> names) {
        return id == null ? null : new ReportDetails.Person(id, names.get(id));
    }
}
