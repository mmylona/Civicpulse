package io.civicpulse.identity.infrastructure;

import io.civicpulse.identity.application.RefreshTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshTokenProperties.class)
class IdentityConfiguration {
}
