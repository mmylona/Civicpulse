package io.civicpulse.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthRequests {

    private AuthRequests() {
    }

    record Register(
            @NotBlank @Email @Size(max = 254) String email,
            @ValidPassword String password,
            @NotBlank @Size(max = 80) String displayName) {
    }

    record Login(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 256) String password) {
    }
}
