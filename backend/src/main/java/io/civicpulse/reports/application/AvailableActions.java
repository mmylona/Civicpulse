package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ReportAction;
import java.util.List;

// the UI shows buttons from this
public record AvailableActions(
        List<ReportAction> actions,
        boolean canEdit,
        boolean canComment,
        boolean canConfirm,
        boolean canModerateComments) {
}
