package io.civicpulse.audit;

import io.civicpulse.reports.CommentModerated;
import io.civicpulse.reports.ReportRestored;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// normal status changes are in the report timeline; only reversals and moderation go here
@Component
class ReportAuditListener {

    private final AuditLog auditLog;

    ReportAuditListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    void on(ReportRestored event) {
        Map<String, Object> details = new HashMap<>();
        details.put("reference", event.reference());
        details.put("from", event.from());
        details.put("reason", event.reason());
        auditLog.record(event.occurredAt(), event.moderatorId(), "REPORT_RESTORED", "REPORT",
                event.reportId().toString(), details);
    }

    @EventListener
    void on(CommentModerated event) {
        Map<String, Object> details = new HashMap<>();
        details.put("reportId", event.reportId().toString());
        details.put("reason", event.reason());
        auditLog.record(event.occurredAt(), event.moderatorId(), event.hidden() ? "COMMENT_HIDDEN" : "COMMENT_SHOWN",
                "COMMENT", event.commentId().toString(), details);
    }
}
