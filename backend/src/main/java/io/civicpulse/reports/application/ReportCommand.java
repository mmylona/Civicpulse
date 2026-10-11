package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.RejectionReason;
import io.civicpulse.reports.domain.ReportAction;
import io.civicpulse.reports.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// request bodies of POST /api/v1/reports/{id}/{action}
public sealed interface ReportCommand {

    ReportAction action();

    record Triage(@NotBlank @Size(max = 50) String categoryCode, @NotNull Severity severity) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.TRIAGE;
        }
    }

    record Reject(@NotNull RejectionReason reason, @Size(max = 2000) String note) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.REJECT;
        }
    }

    record MarkDuplicate(@NotNull UUID duplicateOf) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.MARK_DUPLICATE;
        }
    }

    record RequestInformation(@NotBlank @Size(max = 2000) String question) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.REQUEST_INFORMATION;
        }
    }

    record ProvideInformation(@NotBlank @Size(max = 2000) String answer) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.PROVIDE_INFORMATION;
        }
    }

    record Resume(@Size(max = 2000) String note) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.RESUME;
        }
    }

    record Assign(@NotNull UUID teamId, UUID assigneeId) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.ASSIGN;
        }
    }

    record Unassign(@NotBlank @Size(max = 2000) String reason) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.UNASSIGN;
        }
    }

    record Start() implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.START;
        }
    }

    record HandBack(@NotBlank @Size(max = 2000) String note) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.HAND_BACK;
        }
    }

    record Resolve(@NotBlank @Size(max = 2000) String resolution) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.RESOLVE;
        }
    }

    record Reopen(@NotBlank @Size(max = 2000) String comment) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.REOPEN;
        }
    }

    record Close() implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.CLOSE;
        }
    }

    record Restore(@NotBlank @Size(max = 2000) String reason, Severity severity) implements ReportCommand {
        @Override
        public ReportAction action() {
            return ReportAction.RESTORE;
        }
    }
}
