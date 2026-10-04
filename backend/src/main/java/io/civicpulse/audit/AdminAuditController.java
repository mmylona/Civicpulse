package io.civicpulse.audit;

import io.civicpulse.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AdminAuditController {

    private final AuditLog auditLog;

    AdminAuditController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping("/api/v1/admin/audit")
    PageResponse<AuditEntry> search(
            @RequestParam(required = false) @Size(max = 64) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @Size(max = 64) String targetType,
            @RequestParam(required = false) @Size(max = 64) String targetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return auditLog.search(new AuditLog.AuditQuery(action, actorId, targetType, targetId), page, size);
    }
}
