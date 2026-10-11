package io.civicpulse.reports.application;

import io.civicpulse.identity.TeamSummary;
import io.civicpulse.identity.UserDirectory;
import io.civicpulse.reports.ReportRestored;
import io.civicpulse.reports.domain.Actor;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.StatusChange;
import io.civicpulse.reports.infrastructure.CategoryRepository;
import io.civicpulse.reports.infrastructure.ReportRepository;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportCommandService {

    private static final int MAX_DUPLICATE_HOPS = 20;

    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final UserDirectory directory;
    private final Actors actors;
    private final ReportViews views;
    private final ReportHistory history;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ReportCommandService(ReportRepository reports, CategoryRepository categories, UserDirectory directory,
            Actors actors, ReportViews views, ReportHistory history, ApplicationEventPublisher events, Clock clock) {
        this.reports = reports;
        this.categories = categories;
        this.directory = directory;
        this.actors = actors;
        this.views = views;
        this.history = history;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ReportDetails perform(UUID id, long expectedVersion, ReportCommand command, CurrentUser user) {
        Report report = reports.findById(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        ReportService.checkVersion(report, expectedVersion);
        Actor actor = actors.of(user);
        Instant now = clock.instant();
        // check permissions before looking up teams, categories or other reports
        report.ensureAllowed(command.action(), actor, now);

        Map<String, Object> details = new LinkedHashMap<>();
        Map<String, Object> internal = new LinkedHashMap<>();
        StatusChange change = switch (command) {
            case ReportCommand.Triage triage -> {
                requireCategory(triage.categoryCode());
                details.put("categoryCode", triage.categoryCode());
                details.put("severity", triage.severity().name());
                yield report.triage(actor, triage.categoryCode(), triage.severity(), now);
            }
            case ReportCommand.Reject reject -> {
                details.put("reason", reject.reason().name());
                putText(details, "message", reject.note());
                yield report.reject(actor, now);
            }
            case ReportCommand.MarkDuplicate duplicate -> {
                Report original = endOfChain(duplicate.duplicateOf());
                StatusChange marked = report.markDuplicateOf(original, actor, now);
                reports.moveDuplicates(report.id(), original.id());
                details.put("duplicateOf", original.reference());
                yield marked;
            }
            case ReportCommand.RequestInformation request -> {
                putText(details, "message", request.question());
                yield report.requestInformation(actor, now);
            }
            case ReportCommand.ProvideInformation answer -> {
                putText(details, "message", answer.answer());
                yield report.provideInformation(actor, now);
            }
            case ReportCommand.Resume resume -> {
                putText(internal, "note", resume.note());
                yield report.resume(actor, now);
            }
            case ReportCommand.Assign assign -> {
                TeamSummary team = directory.findTeam(assign.teamId())
                        .orElseThrow(() -> new ApiException(ReportErrorCode.UNKNOWN_TEAM));
                if (assign.assigneeId() != null) {
                    requireOperatorInTeam(assign.assigneeId(), team.id());
                }
                internal.put("team", team.name());
                if (assign.assigneeId() != null) {
                    internal.put("assigneeId", assign.assigneeId().toString());
                }
                yield report.assign(actor, team.id(), assign.assigneeId(), now);
            }
            case ReportCommand.Unassign unassign -> {
                putText(internal, "note", unassign.reason());
                yield report.unassign(actor, now);
            }
            case ReportCommand.Start start -> report.start(actor, now);
            case ReportCommand.HandBack handBack -> {
                putText(internal, "note", handBack.note());
                yield report.handBack(actor, now);
            }
            case ReportCommand.Resolve resolve -> {
                putText(details, "message", resolve.resolution());
                yield report.resolve(actor, now);
            }
            case ReportCommand.Reopen reopen -> {
                putText(details, "message", reopen.comment());
                yield report.reopen(actor, now);
            }
            case ReportCommand.Close close -> report.close(actor, now);
            case ReportCommand.Restore restore -> {
                putText(internal, "note", restore.reason());
                yield report.restore(actor, restore.severity(), now);
            }
        };

        reports.saveAndFlush(report);
        history.statusChanged(report, change, actor, details, internal, now);
        if (command instanceof ReportCommand.Restore restore) {
            events.publishEvent(new ReportRestored(report.id(), report.reference(), change.from().name(),
                    restore.reason().trim(), actor.id(), now));
        }
        return views.details(report, Viewer.of(actor));
    }

    // CP-1 duplicate of CP-2 duplicate of CP-3: marking against CP-1 points at CP-3
    private Report endOfChain(UUID id) {
        Report target = reports.findById(id)
                .orElseThrow(() -> new ApiException(ReportErrorCode.DUPLICATE_TARGET_INVALID));
        for (int hops = 0; target.status() == ReportStatus.DUPLICATE && hops < MAX_DUPLICATE_HOPS; hops++) {
            target = reports.findById(target.duplicateOfId())
                    .orElseThrow(() -> new ApiException(ReportErrorCode.DUPLICATE_TARGET_INVALID));
        }
        return target;
    }

    private void requireCategory(String code) {
        if (!categories.existsByCodeAndActiveTrue(code)) {
            throw new ApiException(ReportErrorCode.UNKNOWN_CATEGORY);
        }
    }

    private void requireOperatorInTeam(UUID userId, UUID teamId) {
        boolean valid = directory.findUser(userId)
                .filter(user -> user.enabled() && user.hasRole(Role.CITY_OPERATOR) && teamId.equals(user.teamId()))
                .isPresent();
        if (!valid) {
            throw new ApiException(ReportErrorCode.ASSIGNEE_NOT_IN_TEAM);
        }
    }

    private static void putText(Map<String, Object> details, String key, String text) {
        if (text != null && !text.isBlank()) {
            details.put(key, text.strip());
        }
    }
}
