package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ConfirmationKind;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.reports.infrastructure.Confirmations;
import io.civicpulse.reports.infrastructure.ReportRepository;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.security.CurrentUser;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('CITIZEN')")
public class ConfirmationService {

    private final ReportRepository reports;
    private final Confirmations confirmations;
    private final Actors actors;
    private final Clock clock;

    ConfirmationService(ReportRepository reports, Confirmations confirmations, Actors actors, Clock clock) {
        this.reports = reports;
        this.confirmations = confirmations;
        this.actors = actors;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationView confirm(UUID reportId, ConfirmationKind kind, CurrentUser user) {
        Report report = lock(reportId);
        if (report.isReporter(actors.of(user))) {
            throw new ApiException(ReportErrorCode.OWN_REPORT);
        }
        if (report.status().isClosed()) {
            throw new ApiException(ReportErrorCode.REPORT_CLOSED);
        }
        confirmations.save(reportId, user.id(), kind, clock.instant());
        Confirmations.Counts counts = confirmations.recount(reportId);
        return new ConfirmationView(kind, counts.stillPresent(), counts.fixed());
    }

    @Transactional
    public ConfirmationView withdraw(UUID reportId, CurrentUser user) {
        actors.of(user);
        lock(reportId);
        confirmations.delete(reportId, user.id());
        Confirmations.Counts counts = confirmations.recount(reportId);
        return new ConfirmationView(null, counts.stillPresent(), counts.fixed());
    }

    private Report lock(UUID id) {
        return reports.lockById(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    }
}
