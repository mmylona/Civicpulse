package io.civicpulse.reports.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ReportLifecycle {

    private static final Map<ReportStatus, Set<ReportStatus>> MOVES = new EnumMap<>(ReportStatus.class);

    static {
        for (ReportStatus status : ReportStatus.values()) {
            MOVES.put(status, EnumSet.noneOf(ReportStatus.class));
        }
        for (ReportAction action : ReportAction.values()) {
            for (ReportStatus from : action.from()) {
                MOVES.get(from).addAll(action.targets());
            }
        }
    }

    private ReportLifecycle() {
    }

    public static boolean allows(ReportStatus from, ReportStatus to) {
        return MOVES.get(from).contains(to);
    }

    public static Set<ReportStatus> targetsFrom(ReportStatus from) {
        return EnumSet.copyOf(MOVES.get(from));
    }
}
