package io.civicpulse.shared.security;

public enum Role {
    CITIZEN,
    MODERATOR,
    CITY_OPERATOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
