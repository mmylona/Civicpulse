package io.civicpulse.identity.domain;

import io.civicpulse.shared.persistence.AssignedIdEntity;
import io.civicpulse.shared.security.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "users")
public class User extends AssignedIdEntity {

    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    private User(UUID id, String email, String passwordHash, String displayName, Set<Role> roles, Instant createdAt) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roles.addAll(roles);
        this.enabled = true;
        this.createdAt = createdAt;
    }

    public static User register(UUID id, String email, String passwordHash, String displayName, Instant now) {
        return new User(id, email, passwordHash, displayName, Set.of(Role.CITIZEN), now);
    }

    public static User create(UUID id, String email, String passwordHash, String displayName, Set<Role> roles,
            Instant now) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
        return new User(id, email, passwordHash, displayName, roles, now);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean canSignInAt(Instant now) {
        return enabled && !isLockedAt(now);
    }

    public boolean recordFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGINS) {
            failedLoginCount = 0;
            lockedUntil = now.plus(LOCKOUT);
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public boolean replaceRoles(Set<Role> newRoles) {
        if (newRoles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
        if (roles.equals(newRoles)) {
            return false;
        }
        roles.clear();
        roles.addAll(newRoles);
        return true;
    }

    public boolean changeEnabled(boolean value) {
        if (enabled == value) {
            return false;
        }
        enabled = value;
        return true;
    }

    public void assignTeam(Team newTeam) {
        this.team = newTeam;
    }

    public UUID id() {
        return getId();
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Team team() {
        return team;
    }

    public Set<Role> roles() {
        return roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int failedLoginCount() {
        return failedLoginCount;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
