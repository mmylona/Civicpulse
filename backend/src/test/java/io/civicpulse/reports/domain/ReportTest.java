package io.civicpulse.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.civicpulse.shared.Ids;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.error.ErrorCode;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.security.SecurityErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private final UUID roads = UUID.randomUUID();
    private final UUID lighting = UUID.randomUUID();

    private final Actor reporter = new Actor(UUID.randomUUID(), Set.of(Role.CITIZEN), null);
    private final Actor otherCitizen = new Actor(UUID.randomUUID(), Set.of(Role.CITIZEN), null);
    private final Actor moderator = new Actor(UUID.randomUUID(), Set.of(Role.MODERATOR), null);
    private final Actor roadsOperator = new Actor(UUID.randomUUID(), Set.of(Role.CITY_OPERATOR), roads);
    private final Actor lightingOperator = new Actor(UUID.randomUUID(), Set.of(Role.CITY_OPERATOR), lighting);
    private final Actor admin = new Actor(UUID.randomUUID(), Set.of(Role.ADMIN), null);

    @Test
    void newReportIsReported() {
        Report report = newReport();

        assertThat(report.status()).isEqualTo(ReportStatus.REPORTED);
        assertThat(report.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void triageSetsCategoryAndSeverity() {
        Report report = newReport();

        StatusChange change = report.triage(moderator, "STREET_LIGHT", Severity.HIGH, NOW);

        assertThat(change).isEqualTo(new StatusChange(ReportAction.TRIAGE, ReportStatus.REPORTED, ReportStatus.TRIAGED));
        assertThat(report.categoryCode()).isEqualTo("STREET_LIGHT");
        assertThat(report.severity()).isEqualTo(Severity.HIGH);
        assertThat(report.statedSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void citizenCannotTriage() {
        Report report = newReport();

        assertFails(() -> report.triage(reporter, "POTHOLE", Severity.LOW, NOW), SecurityErrorCode.ACCESS_DENIED);
    }

    @Test
    void adminHasNoReportActions() {
        Report report = newReport();

        for (ReportAction action : ReportAction.values()) {
            assertThat(report.allows(action, admin, NOW)).as(action.name()).isFalse();
        }
        assertFails(() -> report.triage(admin, "POTHOLE", Severity.LOW, NOW), SecurityErrorCode.ACCESS_DENIED);
    }

    @Test
    void wrongStatusIsConflict() {
        Report report = newReport();

        assertFails(() -> report.resolve(roadsOperator, NOW), ReportErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void onlyModeratorsReject() {
        Report report = newReport();

        assertFails(() -> report.reject(roadsOperator, NOW), SecurityErrorCode.ACCESS_DENIED);
        report.reject(moderator, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.REJECTED);
    }

    @Test
    void onlyTheAssignedTeamWorksOnIt() {
        Report report = assignedToRoads();

        assertFails(() -> report.start(lightingOperator, NOW), SecurityErrorCode.ACCESS_DENIED);
        assertThat(report.allows(ReportAction.START, lightingOperator, NOW)).isFalse();
        assertThat(report.allows(ReportAction.START, roadsOperator, NOW)).isTrue();

        report.start(roadsOperator, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(report.assigneeId()).isEqualTo(roadsOperator.id());
    }

    @Test
    void startKeepsExistingAssignee() {
        Report report = newReport();
        report.triage(moderator, "POTHOLE", Severity.LOW, NOW);
        UUID colleague = UUID.randomUUID();
        report.assign(lightingOperator, roads, colleague, NOW);

        report.start(roadsOperator, NOW);

        assertThat(report.assigneeId()).isEqualTo(colleague);
    }

    @Test
    void unassignClearsTeam() {
        Report report = assignedToRoads();

        report.unassign(lightingOperator, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.TRIAGED);
        assertThat(report.teamId()).isNull();
    }

    @Test
    void handBackKeepsTeamButNotAssignee() {
        Report report = inProgress();

        report.handBack(roadsOperator, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.ASSIGNED);
        assertThat(report.teamId()).isEqualTo(roads);
        assertThat(report.assigneeId()).isNull();
    }

    @Test
    void informationRequestReturnsToPreviousStatus() {
        Report report = inProgress();

        report.requestInformation(moderator, NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.WAITING_FOR_INFORMATION);
        assertThat(report.resumeStatus()).isEqualTo(ReportStatus.IN_PROGRESS);

        StatusChange change = report.provideInformation(reporter, NOW);

        assertThat(change.to()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(report.resumeStatus()).isNull();
    }

    @Test
    void onlyReporterAnswers() {
        Report report = newReport();
        report.requestInformation(moderator, NOW);

        assertFails(() -> report.provideInformation(otherCitizen, NOW), SecurityErrorCode.ACCESS_DENIED);
        assertFails(() -> report.provideInformation(moderator, NOW), SecurityErrorCode.ACCESS_DENIED);

        report.resume(roadsOperator, NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.REPORTED);
    }

    @Test
    void reporterCanReopenWithinWindow() {
        Report report = resolved();

        assertThat(report.allows(ReportAction.REOPEN, reporter, NOW.plus(Duration.ofDays(13)))).isTrue();
        assertThat(report.allows(ReportAction.REOPEN, otherCitizen, NOW)).isFalse();
        assertFails(() -> report.reopen(reporter, NOW.plus(Report.REOPEN_WINDOW)), ReportErrorCode.REOPEN_WINDOW_CLOSED);

        report.reopen(reporter, NOW.plus(Duration.ofDays(1)));

        assertThat(report.status()).isEqualTo(ReportStatus.ASSIGNED);
        assertThat(report.resolvedAt()).isNull();
    }

    @Test
    void systemOrOperatorCloses() {
        Report report = resolved();

        assertFails(() -> report.close(reporter, NOW), SecurityErrorCode.ACCESS_DENIED);
        report.close(Actor.system(), NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.CLOSED);
        assertThat(report.closedAt()).isEqualTo(NOW);
        assertThat(report.kindOf(Actor.system())).isEqualTo(ActorKind.SYSTEM);
    }

    @Test
    void duplicateNeedsAnotherOpenReport() {
        Report report = newReport();
        Report original = newReport();
        Report rejected = newReport();
        rejected.reject(moderator, NOW);

        assertFails(() -> report.markDuplicateOf(report, moderator, NOW), ReportErrorCode.DUPLICATE_TARGET_INVALID);
        assertFails(() -> report.markDuplicateOf(rejected, moderator, NOW), ReportErrorCode.DUPLICATE_TARGET_INVALID);

        report.markDuplicateOf(original, roadsOperator, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.DUPLICATE);
        assertThat(report.duplicateOfId()).isEqualTo(original.id());
    }

    @Test
    void restoreClearsDuplicateAndKeepsSeverity() {
        Report report = newReport();
        report.markDuplicateOf(newReport(), moderator, NOW);

        report.restore(moderator, null, NOW);

        assertThat(report.status()).isEqualTo(ReportStatus.TRIAGED);
        assertThat(report.duplicateOfId()).isNull();
        assertThat(report.validatedSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void reporterEditsOnlyBeforeTriage() {
        Report report = newReport();
        ReportContent changed = new ReportContent("POTHOLE", "Bigger hole", "Now much deeper", Severity.HIGH,
                46.95, 7.45, null);

        assertFails(() -> report.edit(otherCitizen, changed, NOW), SecurityErrorCode.ACCESS_DENIED);
        assertThat(report.edit(reporter, report.content(), NOW)).isFalse();
        assertThat(report.edit(reporter, changed, NOW)).isTrue();
        assertThat(report.title()).isEqualTo("Bigger hole");

        report.triage(moderator, "POTHOLE", Severity.HIGH, NOW);
        assertThat(report.canEdit(reporter)).isFalse();
        assertFails(() -> report.edit(reporter, changed, NOW), ReportErrorCode.REPORT_NOT_EDITABLE);
    }

    @Test
    void actorKinds() {
        Report report = newReport();

        assertThat(report.kindOf(reporter)).isEqualTo(ActorKind.REPORTER);
        assertThat(report.kindOf(otherCitizen)).isEqualTo(ActorKind.CITIZEN);
        assertThat(report.kindOf(moderator)).isEqualTo(ActorKind.STAFF);
    }

    private Report newReport() {
        ReportContent content = new ReportContent("POTHOLE", "Pothole", "Deep pothole near the tram stop",
                Severity.MEDIUM, 46.948, 7.4474, "Bubenbergplatz");
        return Report.submit(Ids.newId(), "CP-10000", reporter.id(), content, null, NOW);
    }

    private Report assignedToRoads() {
        Report report = newReport();
        report.triage(moderator, "POTHOLE", Severity.HIGH, NOW);
        report.assign(lightingOperator, roads, null, NOW);
        return report;
    }

    private Report inProgress() {
        Report report = assignedToRoads();
        report.start(roadsOperator, NOW);
        return report;
    }

    private Report resolved() {
        Report report = inProgress();
        report.resolve(roadsOperator, NOW);
        return report;
    }

    private static void assertFails(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }
}
