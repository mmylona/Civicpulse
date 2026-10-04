package io.civicpulse.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("civicpulse.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
