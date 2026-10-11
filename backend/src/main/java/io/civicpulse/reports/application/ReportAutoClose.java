package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.Actor;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.StatusChange;
import io.civicpulse.reports.infrastructure.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// closes resolved reports once the reporter can no longer reopen them
@Component
public class ReportAutoClose {

    private final ReportRepository reports;
    private final ReportHistory history;
    private final Clock clock;

    ReportAutoClose(ReportRepository reports, ReportHistory history, Clock clock) {
        this.reports = reports;
        this.history = history;
        this.clock = clock;
    }

    // at most 100 per run; with several instances one of them fails on the version check and rolls back
    @Scheduled(cron = "${civicpulse.reports.auto-close-cron:0 */15 * * * *}")
    @Transactional
    public int run() {
        Instant now = clock.instant();
        List<Report> due = reports.findTop100ByStatusAndResolvedAtBeforeOrderByResolvedAtAsc(
                ReportStatus.RESOLVED, now.minus(Report.REOPEN_WINDOW));
        for (Report report : due) {
            StatusChange change = report.close(Actor.system(), now);
            reports.saveAndFlush(report);
            history.statusChanged(report, change, Actor.system(), Map.of(), Map.of(), now);
        }
        return due.size();
    }
}
