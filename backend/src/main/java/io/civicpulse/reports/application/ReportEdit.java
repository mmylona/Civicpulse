package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.Severity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH body, null means "leave as it is"
public record ReportEdit(
        @Size(max = 50) @Pattern(regexp = NOT_BLANK) String categoryCode,
        @Size(max = 120) @Pattern(regexp = NOT_BLANK) String title,
        @Size(max = 4000) @Pattern(regexp = NOT_BLANK) String description,
        Severity severity,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 200) String address) {

    // same rule as @NotBlank: something left after trim()
    static final String NOT_BLANK = "(?s).*[^\\x00-\\x20].*";
}
