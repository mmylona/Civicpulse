package io.civicpulse.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("civicpulse.security.refresh-token")
public record RefreshTokenProperties(
        @DefaultValue("14d") Duration ttl,
        @DefaultValue("10s") Duration reuseGrace,
        @DefaultValue("true") boolean cookieSecure) {
}
