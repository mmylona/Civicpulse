package io.civicpulse.identity.api;

import io.civicpulse.identity.application.AuthResult;
import io.civicpulse.identity.application.UserProfile;

record TokenResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {

    static TokenResponse of(AuthResult result) {
        return new TokenResponse(result.accessToken(), "Bearer", result.expiresInSeconds(), result.user());
    }
}
