package io.civicpulse.audit;

import io.civicpulse.identity.RefreshTokenReuseDetected;
import io.civicpulse.identity.SignInFailed;
import io.civicpulse.identity.UserRegistered;
import io.civicpulse.identity.UserRolesChanged;
import io.civicpulse.identity.UserSignedIn;
import io.civicpulse.identity.UserStatusChanged;
import io.civicpulse.identity.UserTeamChanged;
import io.civicpulse.shared.security.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class IdentityAuditListener {

    static final String USER = "USER";

    private final AuditLog auditLog;

    IdentityAuditListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    void on(UserRegistered event) {
        auditLog.record(event.occurredAt(), event.userId(), "USER_REGISTERED", USER, event.userId().toString(),
                Map.of("email", event.email()));
    }

    @EventListener
    void on(UserSignedIn event) {
        auditLog.record(event.occurredAt(), event.userId(), "SIGN_IN_SUCCEEDED", USER, event.userId().toString(),
                Map.of());
    }

    @EventListener
    void on(SignInFailed event) {
        String action = event.accountLocked() ? "ACCOUNT_LOCKED" : "SIGN_IN_FAILED";
        auditLog.record(event.occurredAt(), null, action, USER, event.userId().toString(),
                Map.of("email", event.email()));
    }

    @EventListener
    void on(UserRolesChanged event) {
        Map<String, Object> details = new HashMap<>();
        details.put("email", event.email());
        details.put("previousRoles", names(event.previousRoles()));
        details.put("newRoles", names(event.newRoles()));
        auditLog.record(event.occurredAt(), event.changedBy(), "USER_ROLES_CHANGED", USER, event.userId().toString(),
                details);
    }

    @EventListener
    void on(UserStatusChanged event) {
        auditLog.record(event.occurredAt(), event.changedBy(), event.enabled() ? "USER_ENABLED" : "USER_DISABLED",
                USER, event.userId().toString(), Map.of("email", event.email()));
    }

    @EventListener
    void on(UserTeamChanged event) {
        Map<String, Object> details = new HashMap<>();
        details.put("email", event.email());
        details.put("teamId", event.teamId() == null ? null : event.teamId().toString());
        auditLog.record(event.occurredAt(), event.changedBy(), "USER_TEAM_CHANGED", USER, event.userId().toString(),
                details);
    }

    @EventListener
    void on(RefreshTokenReuseDetected event) {
        auditLog.record(event.occurredAt(), null, "REFRESH_TOKEN_REUSE_DETECTED", USER, event.userId().toString(),
                Map.of("tokenFamily", event.familyId().toString()));
    }

    private static List<String> names(Set<Role> roles) {
        return roles.stream().sorted().map(Enum::name).toList();
    }
}
