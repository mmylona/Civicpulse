package io.civicpulse.reports.application;

import io.civicpulse.identity.UserDirectory;
import io.civicpulse.reports.domain.Actor;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportAction;
import io.civicpulse.reports.domain.ReportContent;
import io.civicpulse.reports.domain.ReportErrorCode;
import io.civicpulse.reports.infrastructure.CategoryRepository;
import io.civicpulse.reports.infrastructure.ReportRepository;
import io.civicpulse.reports.infrastructure.ReportTimeline;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.error.CommonErrorCode;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final ReportTimeline timeline;
    private final UserDirectory directory;
    private final Actors actors;
    private final ReportViews views;
    private final ReportHistory history;
    private final Clock clock;

    ReportService(ReportRepository reports, CategoryRepository categories, ReportTimeline timeline,
            UserDirectory directory, Actors actors, ReportViews views, ReportHistory history, Clock clock) {
        this.reports = reports;
        this.categories = categories;
        this.timeline = timeline;
        this.directory = directory;
        this.actors = actors;
        this.views = views;
        this.history = history;
        this.clock = clock;
    }

    // a repeated submissionKey returns the report created the first time
    @Transactional
    @PreAuthorize("hasRole('CITIZEN')")
    public ReportDetails create(NewReport request, String submissionKey, CurrentUser user) {
        Viewer viewer = Viewer.of(actors.of(user));
        if (submissionKey != null) {
            Optional<Report> existing = reports.findByReporterIdAndSubmissionKey(user.id(), submissionKey);
            if (existing.isPresent()) {
                return views.details(existing.get(), viewer);
            }
        }
        requireCategory(request.categoryCode());
        Instant now = clock.instant();
        ReportContent content = new ReportContent(
                request.categoryCode(),
                request.title().trim(),
                request.description().trim(),
                request.severity(),
                request.latitude(),
                request.longitude(),
                blankToNull(request.address()));
        Report report = Report.submit(Ids.newId(), "CP-" + reports.nextNumber(), user.id(), content, submissionKey, now);
        reports.saveAndFlush(report);
        history.submitted(report, now);
        return views.details(report, viewer);
    }

    @Transactional(readOnly = true)
    public Optional<ReportDetails> findSubmission(String submissionKey, CurrentUser user) {
        return reports.findByReporterIdAndSubmissionKey(user.id(), submissionKey)
                .map(report -> views.details(report, actors.viewer(user)));
    }

    @Transactional(readOnly = true)
    public ReportDetails get(UUID id, CurrentUser user) {
        return views.details(load(id), actors.viewer(user));
    }

    @Transactional
    public ReportDetails edit(UUID id, long expectedVersion, ReportEdit edit, CurrentUser user) {
        Report report = load(id);
        checkVersion(report, expectedVersion);
        Actor actor = actors.of(user);
        ReportContent current = report.content();
        ReportContent updated = new ReportContent(
                edit.categoryCode() != null ? edit.categoryCode().trim() : current.categoryCode(),
                edit.title() != null ? edit.title().trim() : current.title(),
                edit.description() != null ? edit.description().trim() : current.description(),
                edit.severity() != null ? edit.severity() : current.severity(),
                edit.latitude() != null ? edit.latitude() : current.latitude(),
                edit.longitude() != null ? edit.longitude() : current.longitude(),
                edit.address() != null ? blankToNull(edit.address()) : current.address());
        Instant now = clock.instant();
        if (report.edit(actor, updated, now)) {
            if (!updated.categoryCode().equals(current.categoryCode())) {
                requireCategory(updated.categoryCode());
            }
            reports.saveAndFlush(report);
            history.edited(report, actor, changedFields(current, updated), now);
        }
        return views.details(report, Viewer.of(actor));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> search(ReportFilter filter, CurrentUser user, int page, int size) {
        Viewer viewer = actors.viewer(user);
        return PageResponse.of(
                reports.findAll(matching(filter, viewer, null), PageRequest.of(page, size, NEWEST_FIRST)),
                ReportSummary::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> mine(CurrentUser user, int page, int size) {
        ReportFilter all = new ReportFilter(Set.of(), null, null, null, false);
        return PageResponse.of(
                reports.findAll(matching(all, Viewer.anonymous(), user.id()), PageRequest.of(page, size, NEWEST_FIRST)),
                ReportSummary::of);
    }

    @Transactional(readOnly = true)
    public AvailableActions actions(UUID id, CurrentUser user) {
        Report report = load(id);
        Actor actor = actors.of(user);
        Instant now = clock.instant();
        List<ReportAction> allowed = Arrays.stream(ReportAction.values())
                .filter(action -> report.allows(action, actor, now))
                .toList();
        boolean open = !report.status().isClosed();
        return new AvailableActions(
                allowed,
                report.canEdit(actor),
                open || actor.isStaff(),
                open && actor.hasRole(Role.CITIZEN) && !report.isReporter(actor),
                actor.hasRole(Role.MODERATOR));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID id, CurrentUser user) {
        load(id);
        Viewer viewer = actors.viewer(user);
        List<ReportTimeline.Entry> entries = timeline.list(id, viewer.isStaff());
        Map<UUID, String> names = viewer.isStaff()
                ? directory.displayNames(actorIds(entries))
                : Map.of();
        return entries.stream()
                .map(entry -> new TimelineEntry(
                        entry.id(),
                        entry.type(),
                        entry.visibility(),
                        entry.actorKind(),
                        entry.actorId() == null ? null : names.get(entry.actorId()),
                        entry.payload(),
                        entry.occurredAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(category -> new CategoryView(category.code()))
                .toList();
    }

    private Report load(UUID id) {
        return reports.findById(id).orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    }

    private void requireCategory(String code) {
        if (!categories.existsByCodeAndActiveTrue(code)) {
            throw new ApiException(ReportErrorCode.UNKNOWN_CATEGORY);
        }
    }

    static void checkVersion(Report report, long expectedVersion) {
        if (report.version() != expectedVersion) {
            throw new ApiException(CommonErrorCode.VERSION_CONFLICT);
        }
    }

    private static Set<UUID> actorIds(List<ReportTimeline.Entry> entries) {
        Set<UUID> ids = new HashSet<>();
        for (ReportTimeline.Entry entry : entries) {
            if (entry.actorId() != null) {
                ids.add(entry.actorId());
            }
        }
        return ids;
    }

    private static List<String> changedFields(ReportContent before, ReportContent after) {
        List<String> fields = new ArrayList<>();
        if (!before.categoryCode().equals(after.categoryCode())) {
            fields.add("categoryCode");
        }
        if (!before.title().equals(after.title())) {
            fields.add("title");
        }
        if (!before.description().equals(after.description())) {
            fields.add("description");
        }
        if (before.severity() != after.severity()) {
            fields.add("severity");
        }
        if (before.latitude() != after.latitude() || before.longitude() != after.longitude()) {
            fields.add("location");
        }
        if (!Objects.equals(before.address(), after.address())) {
            fields.add("address");
        }
        return fields;
    }

    private static Specification<Report> matching(ReportFilter filter, Viewer viewer, UUID reporterId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reporterId != null) {
                predicates.add(cb.equal(root.get("reporterId"), reporterId));
            }
            if (!filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.categoryCode() != null) {
                predicates.add(cb.equal(root.get("categoryCode"), filter.categoryCode()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String pattern = "%" + escapeLike(filter.query().strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("description")), pattern, '\\')));
            }
            if (viewer.isStaff()) {
                if (filter.teamId() != null) {
                    predicates.add(cb.equal(root.get("teamId"), filter.teamId()));
                }
                if (filter.assignedToMe()) {
                    predicates.add(cb.equal(root.get("assigneeId"), viewer.userId()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
