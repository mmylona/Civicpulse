package io.civicpulse.reports.domain;

import static io.civicpulse.reports.domain.ReportAction.ASSIGN;
import static io.civicpulse.reports.domain.ReportAction.CLOSE;
import static io.civicpulse.reports.domain.ReportAction.HAND_BACK;
import static io.civicpulse.reports.domain.ReportAction.MARK_DUPLICATE;
import static io.civicpulse.reports.domain.ReportAction.PROVIDE_INFORMATION;
import static io.civicpulse.reports.domain.ReportAction.REJECT;
import static io.civicpulse.reports.domain.ReportAction.REOPEN;
import static io.civicpulse.reports.domain.ReportAction.REQUEST_INFORMATION;
import static io.civicpulse.reports.domain.ReportAction.RESOLVE;
import static io.civicpulse.reports.domain.ReportAction.RESTORE;
import static io.civicpulse.reports.domain.ReportAction.RESUME;
import static io.civicpulse.reports.domain.ReportAction.START;
import static io.civicpulse.reports.domain.ReportAction.TRIAGE;
import static io.civicpulse.reports.domain.ReportAction.UNASSIGN;

import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.persistence.AssignedIdEntity;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.security.SecurityErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class Report extends AssignedIdEntity {

    // the reporter can reopen a resolved report for this long, then it is closed
    public static final Duration REOPEN_WINDOW = Duration.ofDays(14);

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "category_code", nullable = false)
    private String categoryCode;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "stated_severity", nullable = false)
    private Severity statedSeverity;

    @Enumerated(EnumType.STRING)
    @Column(name = "validated_severity")
    private Severity validatedSeverity;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private String address;

    @Column(name = "district_code")
    private String districtCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resume_status")
    private ReportStatus resumeStatus;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "submission_key", updatable = false)
    private String submissionKey;

    // updated with SQL when confirmations change, see Confirmations
    @Column(name = "still_present_count", insertable = false, updatable = false)
    private int stillPresentCount;

    @Column(name = "fixed_count", insertable = false, updatable = false)
    private int fixedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    protected Report() {
    }

    private Report(UUID id, String reference, UUID reporterId, ReportContent content, String submissionKey,
            Instant now) {
        super(id);
        this.reference = reference;
        this.reporterId = reporterId;
        this.submissionKey = submissionKey;
        this.status = ReportStatus.REPORTED;
        this.createdAt = now;
        apply(content, now);
    }

    public static Report submit(UUID id, String reference, UUID reporterId, ReportContent content,
            String submissionKey, Instant now) {
        return new Report(id, reference, reporterId, content, submissionKey, now);
    }

    // returns false if nothing changed
    public boolean edit(Actor actor, ReportContent content, Instant now) {
        if (!isReporter(actor)) {
            throw new ApiException(SecurityErrorCode.ACCESS_DENIED);
        }
        if (status != ReportStatus.REPORTED) {
            throw new ApiException(ReportErrorCode.REPORT_NOT_EDITABLE);
        }
        if (content.equals(content())) {
            return false;
        }
        apply(content, now);
        return true;
    }

    // set by the application from the location; null if unknown
    public void placeIn(String districtCode) {
        this.districtCode = districtCode;
    }

    public boolean canEdit(Actor actor) {
        return isReporter(actor) && status == ReportStatus.REPORTED;
    }

    public StatusChange triage(Actor actor, String categoryCode, Severity severity, Instant now) {
        check(TRIAGE, actor, now);
        this.categoryCode = categoryCode;
        this.validatedSeverity = severity;
        return move(TRIAGE, ReportStatus.TRIAGED, now);
    }

    public StatusChange reject(Actor actor, Instant now) {
        check(REJECT, actor, now);
        return move(REJECT, ReportStatus.REJECTED, now);
    }

    // original must already be the end of any duplicate chain
    public StatusChange markDuplicateOf(Report original, Actor actor, Instant now) {
        check(MARK_DUPLICATE, actor, now);
        if (original.getId().equals(getId()) || original.status.isClosed()) {
            throw new ApiException(ReportErrorCode.DUPLICATE_TARGET_INVALID);
        }
        duplicateOfId = original.getId();
        return move(MARK_DUPLICATE, ReportStatus.DUPLICATE, now);
    }

    public StatusChange requestInformation(Actor actor, Instant now) {
        check(REQUEST_INFORMATION, actor, now);
        resumeStatus = status;
        return move(REQUEST_INFORMATION, ReportStatus.WAITING_FOR_INFORMATION, now);
    }

    public StatusChange provideInformation(Actor actor, Instant now) {
        return returnToPreviousStatus(PROVIDE_INFORMATION, actor, now);
    }

    public StatusChange resume(Actor actor, Instant now) {
        return returnToPreviousStatus(RESUME, actor, now);
    }

    public StatusChange assign(Actor actor, UUID teamId, UUID assigneeId, Instant now) {
        check(ASSIGN, actor, now);
        this.teamId = teamId;
        this.assigneeId = assigneeId;
        return move(ASSIGN, ReportStatus.ASSIGNED, now);
    }

    public StatusChange unassign(Actor actor, Instant now) {
        check(UNASSIGN, actor, now);
        teamId = null;
        assigneeId = null;
        return move(UNASSIGN, ReportStatus.TRIAGED, now);
    }

    public StatusChange start(Actor actor, Instant now) {
        check(START, actor, now);
        if (assigneeId == null) {
            assigneeId = actor.id();
        }
        return move(START, ReportStatus.IN_PROGRESS, now);
    }

    public StatusChange handBack(Actor actor, Instant now) {
        check(HAND_BACK, actor, now);
        assigneeId = null;
        return move(HAND_BACK, ReportStatus.ASSIGNED, now);
    }

    public StatusChange resolve(Actor actor, Instant now) {
        check(RESOLVE, actor, now);
        resolvedAt = now;
        return move(RESOLVE, ReportStatus.RESOLVED, now);
    }

    public StatusChange reopen(Actor actor, Instant now) {
        check(REOPEN, actor, now);
        resolvedAt = null;
        assigneeId = null;
        return move(REOPEN, ReportStatus.ASSIGNED, now);
    }

    public StatusChange close(Actor actor, Instant now) {
        check(CLOSE, actor, now);
        closedAt = now;
        return move(CLOSE, ReportStatus.CLOSED, now);
    }

    // severity may be null; then the old validated or the stated severity is kept
    public StatusChange restore(Actor actor, Severity severity, Instant now) {
        check(RESTORE, actor, now);
        duplicateOfId = null;
        if (severity != null) {
            validatedSeverity = severity;
        } else if (validatedSeverity == null) {
            validatedSeverity = statedSeverity;
        }
        return move(RESTORE, ReportStatus.TRIAGED, now);
    }

    public boolean allows(ReportAction action, Actor actor, Instant now) {
        return action.isAvailableFrom(status) && isPerformer(action, actor) && guardHolds(action, now);
    }

    public ActorKind kindOf(Actor actor) {
        if (actor.isSystem()) {
            return ActorKind.SYSTEM;
        }
        if (actor.isStaff()) {
            return ActorKind.STAFF;
        }
        return isReporter(actor) ? ActorKind.REPORTER : ActorKind.CITIZEN;
    }

    public boolean isReporter(Actor actor) {
        return !actor.isSystem() && reporterId.equals(actor.id());
    }

    // 403 if the actor can never do this, 409 if the status is wrong, then 403 for the team rule
    public void ensureAllowed(ReportAction action, Actor actor, Instant now) {
        check(action, actor, now);
    }

    private void check(ReportAction action, Actor actor, Instant now) {
        if (!hasRoleFor(action, actor)) {
            throw new ApiException(SecurityErrorCode.ACCESS_DENIED);
        }
        if (!action.isAvailableFrom(status)) {
            throw new ApiException(ReportErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (!isPerformer(action, actor)) {
            throw new ApiException(SecurityErrorCode.ACCESS_DENIED);
        }
        if (!guardHolds(action, now)) {
            throw new ApiException(ReportErrorCode.REOPEN_WINDOW_CLOSED);
        }
    }

    private boolean hasRoleFor(ReportAction action, Actor actor) {
        for (Performer performer : action.performers()) {
            boolean matches = performer == Performer.TEAM_OPERATOR
                    ? actor.hasRole(Role.CITY_OPERATOR)
                    : matches(performer, actor);
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private boolean isPerformer(ReportAction action, Actor actor) {
        for (Performer performer : action.performers()) {
            if (matches(performer, actor)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Performer performer, Actor actor) {
        return switch (performer) {
            case MODERATOR -> actor.hasRole(Role.MODERATOR);
            case OPERATOR -> actor.hasRole(Role.CITY_OPERATOR);
            case TEAM_OPERATOR -> actor.hasRole(Role.CITY_OPERATOR) && teamId != null && teamId.equals(actor.teamId());
            case REPORTER -> isReporter(actor);
            case SYSTEM -> actor.isSystem();
        };
    }

    private boolean guardHolds(ReportAction action, Instant now) {
        if (action == REOPEN) {
            return resolvedAt != null && now.isBefore(resolvedAt.plus(REOPEN_WINDOW));
        }
        return true;
    }

    private StatusChange returnToPreviousStatus(ReportAction action, Actor actor, Instant now) {
        check(action, actor, now);
        ReportStatus previous = resumeStatus;
        resumeStatus = null;
        return move(action, previous, now);
    }

    private StatusChange move(ReportAction action, ReportStatus target, Instant now) {
        StatusChange change = new StatusChange(action, status, target);
        status = target;
        updatedAt = now;
        return change;
    }

    private void apply(ReportContent content, Instant now) {
        this.categoryCode = content.categoryCode();
        this.title = content.title();
        this.description = content.description();
        this.statedSeverity = content.severity();
        this.latitude = content.latitude();
        this.longitude = content.longitude();
        this.address = content.address();
        this.updatedAt = now;
    }

    public ReportContent content() {
        return new ReportContent(categoryCode, title, description, statedSeverity, latitude, longitude, address);
    }

    public Severity severity() {
        return validatedSeverity != null ? validatedSeverity : statedSeverity;
    }

    public UUID id() {
        return getId();
    }

    public String reference() {
        return reference;
    }

    public UUID reporterId() {
        return reporterId;
    }

    public String categoryCode() {
        return categoryCode;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Severity statedSeverity() {
        return statedSeverity;
    }

    public Severity validatedSeverity() {
        return validatedSeverity;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public String address() {
        return address;
    }

    public String districtCode() {
        return districtCode;
    }

    public ReportStatus status() {
        return status;
    }

    public ReportStatus resumeStatus() {
        return resumeStatus;
    }

    public UUID teamId() {
        return teamId;
    }

    public UUID assigneeId() {
        return assigneeId;
    }

    public UUID duplicateOfId() {
        return duplicateOfId;
    }

    public int stillPresentCount() {
        return stillPresentCount;
    }

    public int fixedCount() {
        return fixedCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public long version() {
        return version;
    }
}
