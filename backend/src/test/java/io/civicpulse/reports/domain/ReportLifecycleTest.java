package io.civicpulse.reports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReportLifecycleTest {

    private static final Set<String> ALLOWED = readAllowedMoves();

    static Stream<Arguments> allPairs() {
        return Arrays.stream(ReportStatus.values())
                .flatMap(from -> Arrays.stream(ReportStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void matchesTable(ReportStatus from, ReportStatus to) {
        assertThat(ReportLifecycle.allows(from, to))
                .as("%s -> %s", from, to)
                .isEqualTo(ALLOWED.contains(from + "," + to));
    }

    @Test
    void checksEveryPair() {
        assertThat(allPairs()).hasSize(81);
        assertThat(ALLOWED).hasSize(22);
    }

    @Test
    void closedIsFinal() {
        assertThat(ReportLifecycle.targetsFrom(ReportStatus.CLOSED)).isEmpty();
    }

    @Test
    void waitingGoesBackToAnyEarlierWorkingStatus() {
        assertThat(ReportLifecycle.targetsFrom(ReportStatus.WAITING_FOR_INFORMATION)).containsExactlyInAnyOrder(
                ReportStatus.REPORTED, ReportStatus.TRIAGED, ReportStatus.ASSIGNED, ReportStatus.IN_PROGRESS);
    }

    @Test
    void actionPaths() {
        assertThat(ReportAction.REQUEST_INFORMATION.path()).isEqualTo("request-information");
        assertThat(ReportAction.TRIAGE.path()).isEqualTo("triage");
    }

    private static Set<String> readAllowedMoves() {
        InputStream in = ReportLifecycleTest.class.getResourceAsStream("/report-lifecycle.csv");
        if (in == null) {
            throw new IllegalStateException("report-lifecycle.csv not found");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
