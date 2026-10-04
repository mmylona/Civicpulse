package io.civicpulse.identity.application;

import io.civicpulse.identity.UserRolesChanged;
import io.civicpulse.identity.UserStatusChanged;
import io.civicpulse.identity.UserTeamChanged;
import io.civicpulse.identity.domain.EmailAddresses;
import io.civicpulse.identity.domain.Team;
import io.civicpulse.identity.domain.User;
import io.civicpulse.identity.infrastructure.TeamRepository;
import io.civicpulse.identity.infrastructure.UserRepository;
import io.civicpulse.shared.error.ApiException;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserAdministrationService {

    private final UserRepository users;
    private final TeamRepository teams;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserAdministrationService(UserRepository users, TeamRepository teams, RefreshTokenService refreshTokens,
            ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.teams = teams;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> search(String query, Role role, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("email")));
        Page<User> result = users.findAll(matching(query, role), pageRequest);
        Instant now = clock.instant();
        return PageResponse.of(result, user -> AdminUserView.of(user, now));
    }

    @Transactional
    public AdminUserView changeRoles(UUID userId, Set<Role> roles, CurrentUser actor) {
        User user = find(userId);
        if (user.id().equals(actor.id()) && !roles.contains(Role.ADMIN)) {
            throw new ApiException(IdentityErrorCode.CANNOT_REMOVE_OWN_ADMIN_ROLE);
        }
        Set<Role> previous = user.roles();
        Instant now = clock.instant();
        if (user.replaceRoles(EnumSet.copyOf(roles))) {
            refreshTokens.revokeAllForUser(user.id(), now);
            events.publishEvent(new UserRolesChanged(user.id(), user.email(), previous, user.roles(), actor.id(), now));
        }
        return AdminUserView.of(user, now);
    }

    @Transactional
    public AdminUserView changeStatus(UUID userId, boolean enabled, CurrentUser actor) {
        User user = find(userId);
        if (user.id().equals(actor.id()) && !enabled) {
            throw new ApiException(IdentityErrorCode.CANNOT_DISABLE_OWN_ACCOUNT);
        }
        Instant now = clock.instant();
        if (user.changeEnabled(enabled)) {
            if (!enabled) {
                refreshTokens.revokeAllForUser(user.id(), now);
            }
            events.publishEvent(new UserStatusChanged(user.id(), user.email(), enabled, actor.id(), now));
        }
        return AdminUserView.of(user, now);
    }

    @Transactional
    public AdminUserView changeTeam(UUID userId, UUID teamId, CurrentUser actor) {
        User user = find(userId);
        Team team = teamId == null
                ? null
                : teams.findById(teamId).orElseThrow(() -> new ApiException(IdentityErrorCode.UNKNOWN_TEAM));
        Instant now = clock.instant();
        UUID currentTeamId = user.team() == null ? null : user.team().getId();
        if (!Objects.equals(currentTeamId, teamId)) {
            user.assignTeam(team);
            events.publishEvent(new UserTeamChanged(user.id(), user.email(), teamId, actor.id(), now));
        }
        return AdminUserView.of(user, now);
    }

    @Transactional(readOnly = true)
    public List<UserProfile.TeamView> teams() {
        return teams.findAllByOrderByNameAsc().stream().map(UserProfile.TeamView::of).toList();
    }

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(IdentityErrorCode.USER_NOT_FOUND));
    }

    private static Specification<User> matching(String query, Role role) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(EmailAddresses.normalize(query)) + "%";
                predicates.add(cb.or(
                        cb.like(root.<String>get("email"), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("displayName")), pattern, '\\')));
            }
            if (role != null) {
                predicates.add(cb.isMember(role, root.<Set<Role>>get("roles")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
