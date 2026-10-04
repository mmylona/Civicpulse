package io.civicpulse.identity.application;

public record AuthResult(String accessToken, long expiresInSeconds, String refreshToken, UserProfile user) {
}
