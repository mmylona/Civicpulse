package io.civicpulse.shared.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(UUID id, String email, Set<Role> roles) {

    public CurrentUser {
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public static CurrentUser from(Jwt jwt) {
        Collection<String> roleNames = jwt.getClaimAsStringList(JwtClaimNames.ROLES);
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (roleNames != null) {
            roleNames.forEach(name -> roles.add(Role.valueOf(name)));
        }
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(JwtClaimNames.EMAIL), roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
