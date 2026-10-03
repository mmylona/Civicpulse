package io.civicpulse.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call the API cross-origin.
 *
 * <p>In the compose setup the SPA and the API share one origin through nginx, so this only
 * matters when the frontend dev server runs on its own port.
 */
@ConfigurationProperties("civicpulse.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
