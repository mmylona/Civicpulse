package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewComment(@NotBlank @Size(max = 2000) String body, Visibility visibility) {
}
