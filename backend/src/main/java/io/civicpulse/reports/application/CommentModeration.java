package io.civicpulse.reports.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentModeration(@NotNull Boolean hidden, @Size(max = 500) String reason) {
}
