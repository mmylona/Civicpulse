package io.civicpulse.shared.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("civicpulse.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("civicpulse") String issuer) {
}
