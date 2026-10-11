package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ConfirmationKind;

public record ConfirmationView(ConfirmationKind myConfirmation, int stillPresentCount, int fixedCount) {
}
